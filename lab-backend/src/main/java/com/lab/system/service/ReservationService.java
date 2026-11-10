package com.lab.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.BizException;
import com.lab.system.common.CreditPolicy;
import com.lab.system.common.PageResult;
import com.lab.system.common.ReservationState;
import com.lab.system.common.TimeWindow;
import com.lab.system.dto.ApproveDTO;
import com.lab.system.dto.RescheduleDTO;
import com.lab.system.dto.ReservationDTO;
import com.lab.system.entity.Lab;
import com.lab.system.entity.Reservation;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.LabMapper;
import com.lab.system.mapper.ReservationMapper;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 实验室预约
 *
 * 并发安全设计：所有会写入预约记录的事务，第一条语句都是当前读（FOR UPDATE），
 * 再对实验室行加锁把同一实验室的写操作串行化。这样等待锁的事务在拿到锁之后
 * 才建立一致性读视图，能够看到前一个事务已提交的预约，从根本上消除
 * 「先查冲突再插入」的 check-then-act 竞态。
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 签到窗口：开始前 15 分钟开放 */
    private static final int CHECKIN_AHEAD_MINUTES = 15;
    /** 爽约判定：结束后 15 分钟仍未签到 */
    private static final int NO_SHOW_GRACE_MINUTES = 15;

    private final ReservationMapper reservationMapper;
    private final LabMapper labMapper;
    private final SysUserMapper userMapper;
    private final MessageService messageService;
    private final LabClosureService closureService;

    @Transactional
    public Reservation create(ReservationDTO dto) {
        // 事务第一条语句：锁实验室行（当前读，不建立 read view）
        labMapper.selectIdForUpdate(dto.getLabId());

        Lab lab = labMapper.selectById(dto.getLabId());
        if (lab == null || lab.getStatus() == null || lab.getStatus() != 1) {
            throw new BizException("实验室不存在或已停用");
        }
        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = dto.getEndTime();
        validateTimeWindow(lab, start, end);

        closureService.ensureOpen(dto.getLabId(), start.toLocalDate());
        checkCredit();
        checkConflict(dto.getLabId(), start, end, null);

        Long selfConflict = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, UserContext.userId())
                .in(Reservation::getStatus, "PENDING", "APPROVED")
                .lt(Reservation::getStartTime, end)
                .gt(Reservation::getEndTime, start));
        if (selfConflict > 0) {
            throw new BizException("你在该时段已有其他预约，请先取消或调整时间");
        }

        Reservation r = new Reservation();
        // 6 位随机后缀，避免高并发下单号撞唯一索引
        r.setOrderNo("RS" + LocalDateTime.now().format(NO_FMT)
                + ThreadLocalRandom.current().nextInt(100000, 1000000));
        r.setUserId(UserContext.userId());
        r.setLabId(dto.getLabId());
        r.setStartTime(start);
        r.setEndTime(end);
        r.setHeadcount(dto.getHeadcount());
        r.setPurpose(dto.getPurpose());
        r.setRemark(dto.getRemark());
        r.setCheckinStatus("NONE");
        boolean autoPass = lab.getNeedApproval() != null && lab.getNeedApproval() == 0;
        r.setStatus(autoPass ? "APPROVED" : "PENDING");
        if (autoPass) {
            r.setApproveTime(LocalDateTime.now());
            r.setApproveRemark("该实验室免审批，系统自动通过");
        }
        reservationMapper.insert(r);

        if (autoPass) {
            messageService.push(r.getUserId(), "预约已通过",
                    "你在 " + lab.getName() + " 的预约已自动通过，单号 " + r.getOrderNo(),
                    "RESERVATION", r.getId());
        } else {
            messageService.pushToRoles(List.of("ADMIN", "TEACHER"), "新的预约待审批",
                    UserContext.get().getRealName() + " 提交了 " + lab.getName() + " 的预约申请，单号 " + r.getOrderNo(),
                    "RESERVATION", r.getId());
        }
        return r;
    }

    /** 提取公共的时段校验，供新建与改期复用 */
    private void validateTimeWindow(Lab lab, LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        if (start.isBefore(LocalDateTime.now())) {
            throw new BizException("不能预约过去的时间段");
        }
        if (start.toLocalDate().isAfter(LocalDate.now().plusDays(30))) {
            throw new BizException("最多只能提前 30 天预约");
        }
        if (Duration.between(start, end).toMinutes() < 30) {
            throw new BizException("单次预约时长不能少于 30 分钟");
        }
        if (Duration.between(start, end).toHours() > 8) {
            throw new BizException("单次预约时长不能超过 8 小时");
        }
        if (lab.getOpenTime() != null && start.toLocalTime().isBefore(lab.getOpenTime())) {
            throw new BizException("预约时间早于实验室开放时间 " + lab.getOpenTime());
        }
        if (lab.getCloseTime() != null && end.toLocalTime().isAfter(lab.getCloseTime())
                && !end.toLocalDate().isAfter(start.toLocalDate())) {
            throw new BizException("预约时间晚于实验室关闭时间 " + lab.getCloseTime());
        }
    }

    private void checkCredit() {
        SysUser u = userMapper.selectById(UserContext.userId());
        if (u == null) {
            throw new BizException(401, "用户不存在");
        }
        int score = u.getCreditScore() == null ? CreditPolicy.MAX : u.getCreditScore();
        if (!CreditPolicy.canReserve(score)) {
            throw new BizException("信用分不足 " + CreditPolicy.FLOOR
                    + "（当前 " + score + " 分），暂不可预约");
        }
        int violations = u.getViolationCount() == null ? 0 : u.getViolationCount();
        if (violations >= CreditPolicy.RECENT_VIOLATION_LIMIT) {
            long recent = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                    .eq(Reservation::getUserId, u.getId())
                    .eq(Reservation::getCheckinStatus, "NO_SHOW")
                    .ge(Reservation::getEndTime, LocalDateTime.now().minusDays(7)));
            if (recent >= CreditPolicy.RECENT_VIOLATION_LIMIT) {
                throw new BizException("近 7 天内爽约 " + CreditPolicy.RECENT_VIOLATION_LIMIT
                        + " 次，预约权限已暂停，请联系管理员");
            }
        }
    }

    public PageResult<Reservation> myPage(int pageNum, int pageSize, String status) {
        Page<Reservation> page = reservationMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getUserId, UserContext.userId())
                        .eq(StringUtils.hasText(status), Reservation::getStatus, status)
                        .orderByDesc(Reservation::getStartTime));
        fillExtra(page.getRecords());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public PageResult<Reservation> page(int pageNum, int pageSize, String status, Long labId,
                                        String keyword, String date) {
        LambdaQueryWrapper<Reservation> wrapper = Wrappers.<Reservation>lambdaQuery()
                .eq(StringUtils.hasText(status), Reservation::getStatus, status)
                .eq(labId != null, Reservation::getLabId, labId)
                .orderByDesc(Reservation::getCreateTime);

        if (StringUtils.hasText(date)) {
            LocalDate d = LocalDate.parse(date);
            wrapper.lt(Reservation::getStartTime, d.plusDays(1).atStartOfDay())
                    .gt(Reservation::getEndTime, d.atStartOfDay());
        }
        if (StringUtils.hasText(keyword)) {
            List<Long> userIds = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                            .like(SysUser::getRealName, keyword).or()
                            .like(SysUser::getUserNo, keyword))
                    .stream().map(SysUser::getId).toList();
            wrapper.and(w -> {
                w.like(Reservation::getOrderNo, keyword);
                if (!userIds.isEmpty()) {
                    w.or().in(Reservation::getUserId, userIds);
                }
            });
        }

        Page<Reservation> page = reservationMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillExtra(page.getRecords());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Transactional
    public void approve(Long id, ApproveDTO dto) {
        // 第一条语句：当前读锁住预约行；随后锁实验室行串行化同实验室的写操作
        Reservation r = reservationMapper.selectByIdForUpdate(id);
        if (r == null) {
            throw new BizException("预约不存在");
        }
        labMapper.selectIdForUpdate(r.getLabId());
        boolean pass = Boolean.TRUE.equals(dto.getPass());
        if (!ReservationState.can(r.getStatus(), pass ? "APPROVED" : "REJECTED")) {
            throw new BizException("该预约已处理，请刷新后重试");
        }
        if (pass) {
            // 加锁后二次校验，避免并发下重复占用
            checkConflict(r.getLabId(), r.getStartTime(), r.getEndTime(), r.getId());
        }
        r.setStatus(pass ? "APPROVED" : "REJECTED");
        r.setApproverId(UserContext.userId());
        r.setApproveTime(LocalDateTime.now());
        r.setApproveRemark(dto.getRemark());
        reservationMapper.updateById(r);

        messageService.push(r.getUserId(), pass ? "预约审批通过" : "预约审批未通过",
                "单号 " + r.getOrderNo() + "：" + (pass ? "已通过，请按时签到"
                        : "未通过" + (StringUtils.hasText(dto.getRemark()) ? "，原因：" + dto.getRemark() : "")),
                "RESERVATION", r.getId());
    }

    @Transactional
    public void cancel(Long id) {
        Reservation r = reservationMapper.selectByIdForUpdate(id);
        if (r == null) {
            throw new BizException("预约不存在");
        }
        boolean self = r.getUserId().equals(UserContext.userId());
        if (!self && !UserContext.isAdmin() && !"TEACHER".equals(UserContext.role())) {
            throw new BizException(403, "没有操作权限");
        }
        if (!ReservationState.can(r.getStatus(), "CANCELLED")) {
            throw new BizException("当前状态不可取消");
        }
        if (r.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BizException("预约已开始，无法取消");
        }
        Reservation update = new Reservation();
        update.setId(id);
        update.setStatus("CANCELLED");
        reservationMapper.updateById(update);
    }

    /** 签到：仅本人、已通过、且处于「开始前 15 分钟 ~ 结束」窗口内 */
    @Transactional
    public void checkin(Long id) {
        Reservation r = reservationMapper.selectByIdForUpdate(id);
        if (r == null || !r.getUserId().equals(UserContext.userId())) {
            throw new BizException(403, "只能为本人预约签到");
        }
        if (!"APPROVED".equals(r.getStatus())) {
            throw new BizException("仅已通过的预约可以签到");
        }
        if (!"NONE".equals(r.getCheckinStatus())) {
            throw new BizException("请勿重复签到");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(r.getStartTime().minusMinutes(CHECKIN_AHEAD_MINUTES))) {
            throw new BizException("签到窗口尚未开放，开始前 " + CHECKIN_AHEAD_MINUTES + " 分钟可签到");
        }
        if (now.isAfter(r.getEndTime())) {
            throw new BizException("预约已结束，无法签到");
        }
        Reservation update = new Reservation();
        update.setId(id);
        update.setCheckinStatus("CHECKED_IN");
        update.setCheckinTime(now);
        reservationMapper.updateById(update);

        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .setSql("credit_score = LEAST(100, IFNULL(credit_score,100) + " + CreditPolicy.CHECKIN_REWARD + ")")
                .eq(SysUser::getId, r.getUserId()));
        messageService.push(r.getUserId(), "签到成功",
                "单号 " + r.getOrderNo() + " 已签到，信用分 +" + CreditPolicy.CHECKIN_REWARD,
                "CREDIT", r.getId());
    }

    /** 改期：重新走一遍完整校验；需审批的实验室改期后回到待审批 */
    @Transactional
    public void reschedule(Long id, RescheduleDTO dto) {
        Reservation r = reservationMapper.selectByIdForUpdate(id);
        if (r == null || !r.getUserId().equals(UserContext.userId())) {
            throw new BizException(403, "只能改期本人预约");
        }
        if (!"APPROVED".equals(r.getStatus())) {
            throw new BizException("仅已通过的预约可以申请改期");
        }
        if (r.getStartTime().isBefore(LocalDateTime.now())) {
            throw new BizException("预约已开始，无法改期");
        }
        labMapper.selectIdForUpdate(r.getLabId());
        Lab lab = labMapper.selectById(r.getLabId());
        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = dto.getEndTime();
        validateTimeWindow(lab, start, end);
        closureService.ensureOpen(r.getLabId(), start.toLocalDate());
        if (lab.getCapacity() != null && r.getHeadcount() != null && r.getHeadcount() > lab.getCapacity()) {
            throw new BizException("使用人数超过实验室容量（" + lab.getCapacity() + " 人）");
        }
        checkConflict(r.getLabId(), start, end, r.getId());

        boolean needApproval = lab.getNeedApproval() == null || lab.getNeedApproval() == 1;
        // 用 wrapper 一次性写入，null 值也能被显式清空（updateById 会忽略 null）
        reservationMapper.update(null, Wrappers.<Reservation>lambdaUpdate()
                .set(Reservation::getStartTime, start)
                .set(Reservation::getEndTime, end)
                .set(Reservation::getStatus, needApproval ? "PENDING" : "APPROVED")
                .set(Reservation::getApproverId, null)
                .set(Reservation::getApproveTime, needApproval ? null : LocalDateTime.now())
                .set(Reservation::getApproveRemark, needApproval ? null : "改期后免审批自动通过")
                .set(Reservation::getCheckinStatus, "NONE")
                .set(StringUtils.hasText(dto.getReason()), Reservation::getRemark,
                        StringUtils.hasText(dto.getReason()) ? trimTo("改期：" + dto.getReason(), 500) : null)
                .eq(Reservation::getId, id));

        if (needApproval) {
            messageService.pushToRoles(List.of("ADMIN", "TEACHER"), "预约改期待审批",
                    UserContext.get().getRealName() + " 申请将单号 " + r.getOrderNo() + " 改期，请重新审批",
                    "RESERVATION", r.getId());
            messageService.push(r.getUserId(), "改期申请已提交",
                    "单号 " + r.getOrderNo() + " 的改期申请已提交，等待审批", "RESERVATION", r.getId());
        } else {
            messageService.push(r.getUserId(), "改期成功",
                    "单号 " + r.getOrderNo() + " 已改期并自动通过", "RESERVATION", r.getId());
        }
    }

    /** 某实验室某天已被占用的时段（含已完成的，便于日历回看） */
    public List<Reservation> timeline(Long labId, String date) {
        LocalDate d = StringUtils.hasText(date) ? LocalDate.parse(date) : LocalDate.now();
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getLabId, labId)
                .in(Reservation::getStatus, "PENDING", "APPROVED", "FINISHED")
                .lt(Reservation::getStartTime, d.plusDays(1).atStartOfDay())
                .gt(Reservation::getEndTime, d.atStartOfDay())
                .orderByAsc(Reservation::getStartTime));
        fillExtra(list);
        return list;
    }

    public Reservation detail(Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null) {
            throw new BizException("预约不存在");
        }
        fillExtra(List.of(r));
        return r;
    }

    /** 定时任务：把已结束的预约标记为已完成 */
    public int finishExpired() {
        Reservation update = new Reservation();
        update.setStatus("FINISHED");
        return reservationMapper.update(update, Wrappers.<Reservation>lambdaUpdate()
                .eq(Reservation::getStatus, "APPROVED")
                .lt(Reservation::getEndTime, LocalDateTime.now()));
    }

    /** 定时任务：判定爽约、扣分、推送提醒（幂等，只处理 NONE 状态） */
    @Transactional
    public int markNoShow() {
        // 同时覆盖 APPROVED 与 FINISHED，避免与 finishExpired 的执行顺序互相影响
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getStatus, "APPROVED", "FINISHED")
                .eq(Reservation::getCheckinStatus, "NONE")
                .lt(Reservation::getEndTime,
                        LocalDateTime.now().minusMinutes(NO_SHOW_GRACE_MINUTES)));
        for (Reservation r : list) {
            Reservation update = new Reservation();
            update.setId(r.getId());
            update.setCheckinStatus("NO_SHOW");
            reservationMapper.updateById(update);

            userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                    .setSql("credit_score = GREATEST(0, IFNULL(credit_score,100) - " + CreditPolicy.NO_SHOW_PENALTY
                            + "), violation_count = IFNULL(violation_count,0) + 1")
                    .eq(SysUser::getId, r.getUserId()));
            messageService.push(r.getUserId(), "预约爽约提醒",
                    "单号 " + r.getOrderNo() + " 未按时签到，记爽约一次，信用分 -" + CreditPolicy.NO_SHOW_PENALTY,
                    "CREDIT", r.getId());
        }
        return list.size();
    }

    private void checkConflict(Long labId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        // SQL 用 idx_lab_time 做索引预筛，应用层再用半开区间精确判定，避免边界歧义
        List<Reservation> candidates = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getLabId, labId)
                .in(Reservation::getStatus, "PENDING", "APPROVED")
                .lt(Reservation::getStartTime, end)
                .gt(Reservation::getEndTime, start)
                .ne(excludeId != null, Reservation::getId, excludeId));
        for (Reservation c : candidates) {
            if (TimeWindow.overlaps(start, end, c.getStartTime(), c.getEndTime())) {
                throw new BizException("该时段已被预约，请选择其他时间");
            }
        }
    }

    private String trimTo(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private void fillExtra(List<Reservation> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> userIds = list.stream().map(Reservation::getUserId).filter(Objects::nonNull).distinct().toList();
        List<Long> labIds = list.stream().map(Reservation::getLabId).filter(Objects::nonNull).distinct().toList();
        List<Long> approverIds = list.stream().map(Reservation::getApproverId).filter(Objects::nonNull).distinct().toList();

        Map<Long, SysUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }
        Map<Long, Lab> labMap = new HashMap<>();
        if (!labIds.isEmpty()) {
            labMapper.selectBatchIds(labIds).forEach(l -> labMap.put(l.getId(), l));
        }
        Map<Long, String> approverMap = new HashMap<>();
        if (!approverIds.isEmpty()) {
            userMapper.selectBatchIds(approverIds).forEach(u -> approverMap.put(u.getId(), u.getRealName()));
        }

        for (Reservation r : list) {
            SysUser user = userMap.get(r.getUserId());
            if (user != null) {
                r.setUserName(user.getRealName());
                r.setUserNo(user.getUserNo());
                r.setDept(user.getDept());
                r.setCreditScore(user.getCreditScore());
            }
            Lab lab = labMap.get(r.getLabId());
            if (lab != null) {
                r.setLabName(lab.getName());
                r.setLabCode(lab.getCode());
                r.setBuilding(lab.getBuilding());
                r.setRoom(lab.getRoom());
            }
            r.setApproverName(approverMap.get(r.getApproverId()));
        }
    }
}
