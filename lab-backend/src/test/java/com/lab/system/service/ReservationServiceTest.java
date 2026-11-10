package com.lab.system.service;

import com.lab.system.common.BizException;
import com.lab.system.dto.ApproveDTO;
import com.lab.system.dto.RescheduleDTO;
import com.lab.system.dto.ReservationDTO;
import com.lab.system.entity.Lab;
import com.lab.system.entity.Reservation;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.LabMapper;
import com.lab.system.mapper.ReservationMapper;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.LoginUser;
import com.lab.system.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预约服务核心路径。使用真实 Service + Mock Mapper，
 * 覆盖的是生产代码真正调用的行锁、冲突、信用分与状态机逻辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {

    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private LabMapper labMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private MessageService messageService;
    @Mock
    private LabClosureService closureService;
    @InjectMocks
    private ReservationService reservationService;

    private static final long LAB_ID = 1L;
    private static final long USER_ID = 7L;

    private LocalDateTime start;
    private Lab lab;
    private SysUser user;

    @BeforeEach
    void setUp() {
        LoginUser ctx = new LoginUser();
        ctx.setId(USER_ID);
        ctx.setUsername("student01");
        ctx.setRealName("张三");
        ctx.setRole("STUDENT");
        UserContext.set(ctx);

        start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);

        lab = new Lab();
        lab.setId(LAB_ID);
        lab.setName("A301 综合实验室");
        lab.setStatus(1);
        lab.setNeedApproval(0);
        lab.setCapacity(30);

        user = new SysUser();
        user.setId(USER_ID);
        user.setCreditScore(100);
        user.setViolationCount(0);

        when(labMapper.selectIdForUpdate(LAB_ID)).thenReturn(LAB_ID);
        when(labMapper.selectById(LAB_ID)).thenReturn(lab);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
        when(reservationMapper.selectList(any())).thenReturn(List.of());
        when(reservationMapper.selectCount(any())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private ReservationDTO dto(LocalDateTime s, LocalDateTime e) {
        ReservationDTO d = new ReservationDTO();
        d.setLabId(LAB_ID);
        d.setStartTime(s);
        d.setEndTime(e);
        d.setHeadcount(4);
        return d;
    }

    private Reservation existing(String status, LocalDateTime s, LocalDateTime e) {
        Reservation r = new Reservation();
        r.setId(99L);
        r.setLabId(LAB_ID);
        r.setUserId(999L);
        r.setOrderNo("RS-EXIST");
        r.setStatus(status);
        r.setCheckinStatus("NONE");
        r.setStartTime(s);
        r.setEndTime(e);
        return r;
    }

    @Test
    @DisplayName("免审批实验室：新建即 APPROVED 并推送站内信")
    void createAutoApproved() {
        Reservation r = reservationService.create(dto(start, start.plusHours(2)));
        assertEquals("APPROVED", r.getStatus());
        assertEquals("NONE", r.getCheckinStatus());
        assertTrue(r.getOrderNo().startsWith("RS"));
        verify(messageService).push(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("需审批实验室：新建为 PENDING 并通知管理员/教师")
    void createPending() {
        lab.setNeedApproval(1);
        Reservation r = reservationService.create(dto(start, start.plusHours(2)));
        assertEquals("PENDING", r.getStatus());
        verify(messageService).pushToRoles(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("已有预约与新时段重叠时拒绝创建")
    void createConflict() {
        when(reservationMapper.selectList(any()))
                .thenReturn(List.of(existing("APPROVED", start.plusMinutes(30), start.plusHours(3))));
        BizException ex = assertThrows(BizException.class,
                () -> reservationService.create(dto(start, start.plusHours(2))));
        assertTrue(ex.getMessage().contains("已被预约"));
    }

    @Test
    @DisplayName("首尾相接不算冲突，可连续预约")
    void createBackToBackAllowed() {
        when(reservationMapper.selectList(any()))
                .thenReturn(List.of(existing("APPROVED", start.minusHours(2), start)));
        Reservation r = reservationService.create(dto(start, start.plusHours(2)));
        assertEquals("APPROVED", r.getStatus());
    }

    @Test
    @DisplayName("信用分低于 60 拒绝预约")
    void createBlockedByCredit() {
        user.setCreditScore(59);
        BizException ex = assertThrows(BizException.class,
                () -> reservationService.create(dto(start, start.plusHours(2))));
        assertTrue(ex.getMessage().contains("信用分不足"));
    }

    @Test
    @DisplayName("单次时长不足 30 分钟拒绝预约")
    void createTooShort() {
        BizException ex = assertThrows(BizException.class,
                () -> reservationService.create(dto(start, start.plusMinutes(20))));
        assertTrue(ex.getMessage().contains("30 分钟"));
    }

    @Test
    @DisplayName("不能预约过去的时间段")
    void createPastTime() {
        LocalDateTime past = LocalDateTime.now().minusDays(1).withHour(9).withMinute(0).withNano(0);
        BizException ex = assertThrows(BizException.class,
                () -> reservationService.create(dto(past, past.plusHours(2))));
        assertTrue(ex.getMessage().contains("过去"));
    }

    @Test
    @DisplayName("审批通过时二次校验冲突，防止并发下重复占用")
    void approveRechecksConflict() {
        Reservation pending = existing("PENDING", start, start.plusHours(2));
        pending.setUserId(USER_ID);
        when(reservationMapper.selectByIdForUpdate(99L)).thenReturn(pending);
        when(reservationMapper.selectList(any()))
                .thenReturn(List.of(existing("APPROVED", start.minusHours(1), start.plusHours(1))));

        ApproveDTO approve = new ApproveDTO();
        approve.setPass(true);
        BizException ex = assertThrows(BizException.class, () -> reservationService.approve(99L, approve));
        assertTrue(ex.getMessage().contains("已被预约"));
        verify(labMapper).selectIdForUpdate(LAB_ID);
    }

    @Test
    @DisplayName("已处理的预约不可重复审批")
    void approveTwiceRejected() {
        Reservation done = existing("APPROVED", start, start.plusHours(2));
        when(reservationMapper.selectByIdForUpdate(99L)).thenReturn(done);
        ApproveDTO approve = new ApproveDTO();
        approve.setPass(true);
        BizException ex = assertThrows(BizException.class, () -> reservationService.approve(99L, approve));
        assertTrue(ex.getMessage().contains("已处理"));
    }

    @Test
    @DisplayName("终态预约不可取消")
    void cancelTerminalRejected() {
        Reservation rejected = existing("REJECTED", start, start.plusHours(2));
        rejected.setUserId(USER_ID);
        when(reservationMapper.selectByIdForUpdate(99L)).thenReturn(rejected);
        BizException ex = assertThrows(BizException.class, () -> reservationService.cancel(99L));
        assertTrue(ex.getMessage().contains("不可取消"));
    }

    @Test
    @DisplayName("重复签到被拒绝")
    void checkinTwiceRejected() {
        Reservation r = existing("APPROVED", start, start.plusHours(2));
        r.setUserId(USER_ID);
        r.setCheckinStatus("CHECKED_IN");
        when(reservationMapper.selectByIdForUpdate(99L)).thenReturn(r);
        BizException ex = assertThrows(BizException.class, () -> reservationService.checkin(99L));
        assertTrue(ex.getMessage().contains("重复签到"));
    }

    @Test
    @DisplayName("爽约扫描：标记 NO_SHOW、扣分、推送提醒")
    void markNoShow() {
        Reservation overdue = existing("APPROVED", start.minusDays(2), start.minusDays(2).plusHours(2));
        when(reservationMapper.selectList(any())).thenReturn(List.of(overdue));

        assertEquals(1, reservationService.markNoShow());
        verify(reservationMapper).updateById(any(Reservation.class));
        verify(userMapper).update(any(), any());
        verify(messageService).push(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("改期时重新做冲突校验")
    void rescheduleRechecksConflict() {
        Reservation r = existing("APPROVED", start, start.plusHours(2));
        r.setUserId(USER_ID);
        r.setHeadcount(4);
        r.setOrderNo("RS-MINE");
        when(reservationMapper.selectByIdForUpdate(99L)).thenReturn(r);
        when(reservationMapper.selectList(any()))
                .thenReturn(List.of(existing("APPROVED", start.plusHours(1), start.plusHours(3))));

        RescheduleDTO d = new RescheduleDTO();
        d.setStartTime(start.plusHours(1));
        d.setEndTime(start.plusHours(2));
        BizException ex = assertThrows(BizException.class, () -> reservationService.reschedule(99L, d));
        assertTrue(ex.getMessage().contains("已被预约"));
    }
}
