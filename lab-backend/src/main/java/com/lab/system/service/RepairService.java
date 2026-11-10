package com.lab.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.BizException;
import com.lab.system.common.PageResult;
import com.lab.system.dto.RepairCreateDTO;
import com.lab.system.dto.RepairFinishDTO;
import com.lab.system.dto.RepairVerifyDTO;
import com.lab.system.entity.Equipment;
import com.lab.system.entity.Lab;
import com.lab.system.entity.RepairLog;
import com.lab.system.entity.RepairOrder;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.EquipmentMapper;
import com.lab.system.mapper.LabMapper;
import com.lab.system.mapper.RepairLogMapper;
import com.lab.system.mapper.RepairOrderMapper;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 设备报修与运维（报修 → 派单 → 维修 → 验收 闭环）
 */
@Service
@RequiredArgsConstructor
public class RepairService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final RepairOrderMapper orderMapper;
    private final RepairLogMapper logMapper;
    private final EquipmentMapper equipmentMapper;
    private final LabMapper labMapper;
    private final SysUserMapper userMapper;
    private final MessageService messageService;

    @Transactional
    public RepairOrder create(RepairCreateDTO dto) {
        Equipment equipment = equipmentMapper.selectById(dto.getEquipmentId());
        if (equipment == null) {
            throw new BizException("设备不存在");
        }
        if ("SCRAPPED".equals(equipment.getStatus())) {
            throw new BizException("该设备已报废，无法报修");
        }
        Long running = orderMapper.selectCount(Wrappers.<RepairOrder>lambdaQuery()
                .eq(RepairOrder::getEquipmentId, dto.getEquipmentId())
                .ne(RepairOrder::getStatus, "VERIFIED"));
        if (running > 0) {
            throw new BizException("该设备已有未完成的报修工单，请勿重复提交");
        }

        RepairOrder order = new RepairOrder();
        order.setOrderNo("RP" + LocalDateTime.now().format(NO_FMT)
                + ThreadLocalRandom.current().nextInt(100, 999));
        order.setEquipmentId(equipment.getId());
        order.setLabId(equipment.getLabId());
        order.setReporterId(UserContext.userId());
        order.setFaultType(StringUtils.hasText(dto.getFaultType()) ? dto.getFaultType() : "其他");
        order.setUrgency(StringUtils.hasText(dto.getUrgency()) ? dto.getUrgency() : "NORMAL");
        order.setFaultDesc(dto.getFaultDesc());
        order.setStatus("PENDING");
        orderMapper.insert(order);

        Equipment update = new Equipment();
        update.setId(equipment.getId());
        update.setStatus("REPAIR");
        equipmentMapper.updateById(update);

        addLog(order.getId(), "CREATE", "提交报修：" + dto.getFaultDesc());
        messageService.push(order.getReporterId(), "报修已提交",
                "工单 " + order.getOrderNo() + " 已受理，等待派单", "REPAIR", order.getId());
        messageService.pushToRoles(List.of("ADMIN", "TEACHER"), "新报修工单待派单",
                "设备 " + equipment.getName() + " 有新工单 " + order.getOrderNo(), "REPAIR", order.getId());
        return order;
    }

    public PageResult<RepairOrder> page(int pageNum, int pageSize, String status, Long labId,
                                        String keyword, String scope) {
        LambdaQueryWrapper<RepairOrder> wrapper = Wrappers.<RepairOrder>lambdaQuery()
                .eq(StringUtils.hasText(status), RepairOrder::getStatus, status)
                .eq(labId != null, RepairOrder::getLabId, labId)
                .orderByDesc(RepairOrder::getCreateTime);

        if ("mine".equals(scope)) {
            wrapper.eq(RepairOrder::getReporterId, UserContext.userId());
        } else if ("assigned".equals(scope)) {
            wrapper.eq(RepairOrder::getRepairerId, UserContext.userId());
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(RepairOrder::getOrderNo, keyword)
                    .or().like(RepairOrder::getFaultDesc, keyword));
        }

        Page<RepairOrder> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillExtra(page.getRecords());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public RepairOrder detail(Long id) {
        RepairOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在");
        }
        fillExtra(List.of(order));
        return order;
    }

    public List<RepairLog> logs(Long orderId) {
        return logMapper.selectList(Wrappers.<RepairLog>lambdaQuery()
                .eq(RepairLog::getOrderId, orderId)
                .orderByAsc(RepairLog::getId));
    }

    @Transactional
    public void assign(Long id, Long repairerId) {
        RepairOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在");
        }
        if (!"PENDING".equals(order.getStatus())) {
            throw new BizException("只有待派单的工单可以派单");
        }
        SysUser repairer = userMapper.selectById(repairerId);
        if (repairer == null || !"REPAIRER".equals(repairer.getRole())) {
            throw new BizException("请选择有效的维修人员");
        }
        order.setRepairerId(repairerId);
        order.setStatus("ASSIGNED");
        order.setAssignTime(LocalDateTime.now());
        orderMapper.updateById(order);
        addLog(id, "ASSIGN", "派单给维修员：" + repairer.getRealName());
        messageService.push(repairerId, "新工单派单",
                "工单 " + order.getOrderNo() + " 已指派给你，请及时处理", "REPAIR", id);
        messageService.push(order.getReporterId(), "工单已派单",
                "工单 " + order.getOrderNo() + " 已指派给 " + repairer.getRealName(), "REPAIR", id);
    }

    @Transactional
    public void start(Long id) {
        RepairOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在");
        }
        if (!"ASSIGNED".equals(order.getStatus())) {
            throw new BizException("只有已派单的工单可以开始维修");
        }
        checkRepairer(order);
        RepairOrder update = new RepairOrder();
        update.setId(id);
        update.setStatus("REPAIRING");
        orderMapper.updateById(update);
        addLog(id, "START", "维修员开始处理工单");
    }

    @Transactional
    public void finish(Long id, RepairFinishDTO dto) {
        RepairOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在");
        }
        if (!"REPAIRING".equals(order.getStatus())) {
            throw new BizException("只有维修中的工单可以提交维修结果");
        }
        checkRepairer(order);
        if (!StringUtils.hasText(dto.getRepairDesc())) {
            throw new BizException("请填写维修处理说明");
        }
        RepairOrder update = new RepairOrder();
        update.setId(id);
        update.setStatus("DONE");
        update.setRepairDesc(dto.getRepairDesc());
        update.setCost(dto.getCost());
        update.setFinishTime(LocalDateTime.now());
        orderMapper.updateById(update);
        addLog(id, "FINISH", "维修完成：" + dto.getRepairDesc());
        messageService.push(order.getReporterId(), "维修完成，待验收",
                "工单 " + order.getOrderNo() + " 已维修完成，请验收", "REPAIR", id);
    }

    @Transactional
    public void verify(Long id, RepairVerifyDTO dto) {
        RepairOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException("工单不存在");
        }
        if (!"DONE".equals(order.getStatus())) {
            throw new BizException("只有待验收的工单可以验收");
        }
        boolean self = order.getReporterId().equals(UserContext.userId());
        if (!self && !UserContext.isAdmin() && !"TEACHER".equals(UserContext.role())) {
            throw new BizException(403, "只有报修人或管理员可以验收");
        }

        RepairOrder update = new RepairOrder();
        update.setId(id);
        update.setStatus("VERIFIED");
        update.setScore(dto.getScore());
        update.setVerifyRemark(dto.getVerifyRemark());
        update.setVerifyTime(LocalDateTime.now());
        orderMapper.updateById(update);

        Equipment equipment = new Equipment();
        equipment.setId(order.getEquipmentId());
        equipment.setStatus("IDLE");
        equipmentMapper.updateById(equipment);

        addLog(id, "VERIFY", "报修人验收通过" + (dto.getScore() != null ? "，评分 " + dto.getScore() + " 分" : ""));
        if (order.getRepairerId() != null) {
            messageService.push(order.getRepairerId(), "工单已验收",
                    "工单 " + order.getOrderNo() + " 已验收通过"
                            + (dto.getScore() != null ? "，评分 " + dto.getScore() + " 分" : ""),
                    "REPAIR", id);
        }
    }

    private void checkRepairer(RepairOrder order) {
        boolean isRepairer = UserContext.userId().equals(order.getRepairerId());
        if (!isRepairer && !UserContext.isAdmin()) {
            throw new BizException(403, "只有被指派的维修员可以操作该工单");
        }
    }

    private void addLog(Long orderId, String action, String content) {
        RepairLog log = new RepairLog();
        log.setOrderId(orderId);
        log.setOperatorId(UserContext.userId());
        log.setOperatorName(UserContext.get().getRealName());
        log.setAction(action);
        log.setContent(content);
        logMapper.insert(log);
    }

    private void fillExtra(List<RepairOrder> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> eqIds = list.stream().map(RepairOrder::getEquipmentId).filter(Objects::nonNull).distinct().toList();
        List<Long> labIds = list.stream().map(RepairOrder::getLabId).filter(Objects::nonNull).distinct().toList();
        List<Long> userIds = list.stream()
                .flatMap(o -> java.util.stream.Stream.of(o.getReporterId(), o.getRepairerId()))
                .filter(Objects::nonNull).distinct().toList();

        Map<Long, Equipment> eqMap = new HashMap<>();
        if (!eqIds.isEmpty()) {
            equipmentMapper.selectBatchIds(eqIds).forEach(e -> eqMap.put(e.getId(), e));
        }
        Map<Long, Lab> labMap = new HashMap<>();
        if (!labIds.isEmpty()) {
            labMapper.selectBatchIds(labIds).forEach(l -> labMap.put(l.getId(), l));
        }
        Map<Long, String> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u.getRealName()));
        }

        for (RepairOrder o : list) {
            Equipment e = eqMap.get(o.getEquipmentId());
            if (e != null) {
                o.setEquipmentName(e.getName());
                o.setEquipmentCode(e.getCode());
            }
            Lab lab = labMap.get(o.getLabId());
            if (lab != null) {
                o.setLabName(lab.getName());
            }
            o.setReporterName(userMap.get(o.getReporterId()));
            o.setRepairerName(userMap.get(o.getRepairerId()));
        }
    }
}
