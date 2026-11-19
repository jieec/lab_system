package com.lab.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.*;
import com.lab.system.entity.*;
import com.lab.system.mapper.*;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CreditService {
    private final CreditRecordMapper records;
    private final SysUserMapper users;
    private final ReservationMapper reservations;
    private final MessageService messages;

    public Map<String,Object> summary() {
        SysUser user=users.selectById(UserContext.userId());
        int score=user.getCreditScore()==null?100:user.getCreditScore();
        long recent=reservations.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId,user.getId()).eq(Reservation::getCheckinStatus,"NO_SHOW")
                .ge(Reservation::getEndTime,LocalDateTime.now().minusDays(7)));
        String restrictedUntil="";
        if(recent>=3){var latest=reservations.selectList(Wrappers.<Reservation>lambdaQuery().eq(Reservation::getUserId,user.getId()).eq(Reservation::getCheckinStatus,"NO_SHOW").ge(Reservation::getEndTime,LocalDateTime.now().minusDays(7)).orderByDesc(Reservation::getEndTime).last("LIMIT 3"));if(latest.size()==3)restrictedUntil=latest.get(2).getEndTime().plusDays(7).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
        return Map.of("score",score,"violationCount",Objects.requireNonNullElse(user.getViolationCount(),0),"restrictedUntil",restrictedUntil,
                "recentNoShows",recent,"canReserve",score>=60&&recent<3,
                "rules",List.of("初始100分，分数范围0–100", "每次有效签到+2分，满分时不额外增加", "预约结束15分钟后仍未签到记爽约，扣10分", "低于60分，或近7天爽约3次及以上，暂停预约", "开始前取消不扣分；签到后不可取消或改期", "低于60分可申请恢复，管理员通过后至少恢复至70分，7天内最多通过一次", "分数恢复不会清除近7天爽约记录；近期限制按所示时间解除"));
    }
    public PageResult<CreditRecord> myRecords(int pageNum,int pageSize) {
        Page<CreditRecord> page=records.selectPage(new Page<>(Math.max(1,pageNum),Math.clamp(pageSize,1,50)),
                Wrappers.<CreditRecord>lambdaQuery().eq(CreditRecord::getUserId,UserContext.userId()).orderByDesc(CreditRecord::getId));
        return PageResult.of(page.getTotal(),page.getRecords());
    }

    /** Caller holds the reservation row lock. Lock user to serialize changes from different reservations. */
    @Transactional
    public CreditRecord change(Long userId,String eventKey,String eventType,int delta,String reason) {
        SysUser user=users.selectByIdForUpdate(userId);
        if(user==null) throw new BizException("用户不存在");
        CreditRecord existing=records.selectOne(Wrappers.<CreditRecord>lambdaQuery().eq(CreditRecord::getEventKey,eventKey));
        if(existing!=null) return existing;
        return write(user,eventKey,eventType,Math.clamp((user.getCreditScore()==null?100:user.getCreditScore())+delta,0,100),reason);
    }
    @Transactional
    public CreditRecord adjust(Long userId,int target,String reason) {
        if(!UserContext.isAdmin()) throw new BizException(403,"仅管理员可调整信用分");
        if(target<0||target>100||reason==null||reason.trim().length()<5||reason.trim().length()>200)
            throw new BizException("目标分数须为0–100，调整原因须为5–200字");
        SysUser user=users.selectByIdForUpdate(userId);
        if(user==null) throw new BizException("用户不存在");
        return write(user,"ADJUST:"+UUID.randomUUID(),"ADJUST",target,reason.trim());
    }
    private CreditRecord write(SysUser user,String key,String type,int after,String reason) {
        int before=user.getCreditScore()==null?100:user.getCreditScore();
        SysUser update=new SysUser();update.setId(user.getId());update.setCreditScore(after);
        if("NO_SHOW".equals(type)) update.setViolationCount(Objects.requireNonNullElse(user.getViolationCount(),0)+1);
        users.updateById(update);
        CreditRecord record=new CreditRecord();record.setUserId(user.getId());record.setEventKey(key);record.setEventType(type);
        record.setScoreBefore(before);record.setScoreAfter(after);record.setChangeAmount(after-before);record.setReason(reason);
        record.setOperatorId(UserContext.peek()==null?null:UserContext.userId());record.setCreateTime(LocalDateTime.now());records.insert(record);
        messages.push(user.getId(),"信用分变动",reason+"；实际变动 "+(after-before>=0?"+":"")+(after-before)+" 分，当前 "+after+" 分","CREDIT",record.getId());
        return record;
    }
}
