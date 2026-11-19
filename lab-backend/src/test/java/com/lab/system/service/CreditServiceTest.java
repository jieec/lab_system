package com.lab.system.service;
import com.lab.system.common.BizException;
import com.lab.system.entity.*;
import com.lab.system.mapper.*;
import com.lab.system.security.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CreditServiceTest {
    CreditRecordMapper records=mock(CreditRecordMapper.class);
    SysUserMapper users=mock(SysUserMapper.class);
    ReservationMapper reservations=mock(ReservationMapper.class);
    MessageService messages=mock(MessageService.class);
    CreditService service=new CreditService(records,users,reservations,messages);
    SysUser user;
    @BeforeEach void setup(){
        user=new SysUser();user.setId(7L);user.setCreditScore(99);user.setViolationCount(0);
        when(users.selectByIdForUpdate(7L)).thenReturn(user);
        LoginUser ctx=new LoginUser();ctx.setId(1L);ctx.setRole("ADMIN");UserContext.set(ctx);
    }
    @AfterEach void clean(){UserContext.clear();}
    @Test void cappedRewardRecordsActualChange(){
        CreditRecord result=service.change(7L,"CHECKIN:1","CHECKIN",2,"有效签到");
        assertEquals(1,result.getChangeAmount());assertEquals(100,result.getScoreAfter());
        verify(records).insert(result);
    }
    @Test void penaltyNeverGoesBelowZeroAndStillCountsViolation(){
        user.setCreditScore(5);
        CreditRecord result=service.change(7L,"NO_SHOW:1","NO_SHOW",-10,"爽约");
        assertEquals(-5,result.getChangeAmount());assertEquals(0,result.getScoreAfter());
        var captured=org.mockito.ArgumentCaptor.forClass(SysUser.class);verify(users).updateById(captured.capture());
        assertEquals(1,captured.getValue().getViolationCount());
    }
    @Test void repeatedEventDoesNotUpdateUserOrInsertAgain(){
        CreditRecord existing=new CreditRecord();existing.setEventKey("CHECKIN:1");
        when(records.selectOne(any())).thenReturn(existing);
        assertSame(existing,service.change(7L,"CHECKIN:1","CHECKIN",2,"重复签到"));
        verify(users,never()).updateById(any(SysUser.class));verify(records,never()).insert(any(CreditRecord.class));
    }
    @Test void studentsCannotAdjustTheirOwnScore(){
        UserContext.get().setRole("STUDENT");
        assertThrows(BizException.class,()->service.adjust(7L,100,"不允许自行恢复"));verifyNoInteractions(records);
    }
    @Test void adjustmentNeedsReasonAndValidTarget(){
        assertThrows(BizException.class,()->service.adjust(7L,101,"已核实误判签到"));
        assertThrows(BizException.class,()->service.adjust(7L,100,"无"));
    }
    @Test void validAdjustmentRecordsReasonAndOperator(){
        user.setCreditScore(50);
        CreditRecord result=service.adjust(7L,70,"已核查签到证明，恢复20分");
        assertEquals(20,result.getChangeAmount());assertEquals(1L,result.getOperatorId());assertEquals("ADJUST",result.getEventType());
    }
}
