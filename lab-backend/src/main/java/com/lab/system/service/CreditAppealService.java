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
import java.time.LocalDateTime;
import java.util.List;
@Service @RequiredArgsConstructor
public class CreditAppealService {
    private final CreditAppealMapper appeals;
    private final SysUserMapper users;
    private final CreditService credit;
    private final MessageService messages;
    public PageResult<CreditAppeal> page(int pageNum,boolean pending){
        var q=Wrappers.<CreditAppeal>lambdaQuery().orderByDesc(CreditAppeal::getId);
        if(pending){if(!UserContext.isAdmin())throw new BizException(403,"仅管理员可查看审核列表");q.eq(CreditAppeal::getStatus,"PENDING");}
        else q.eq(CreditAppeal::getUserId,UserContext.userId());
        Page<CreditAppeal> page=appeals.selectPage(new Page<>(Math.max(1,pageNum),10),q);
        return PageResult.of(page.getTotal(),page.getRecords());
    }
    @Transactional public CreditAppeal apply(String reason){
        if(reason==null||reason.trim().length()<10||reason.trim().length()>500)throw new BizException("请填写10–500字的恢复原因与改进措施");
        SysUser user=users.selectByIdForUpdate(UserContext.userId());
        if(user==null)throw new BizException("用户不存在");
        if(user.getCreditScore()==null||user.getCreditScore()>=60)throw new BizException("当前分数不低于60分，无需信用恢复；近期爽约限制按日期解除");
        if(appeals.selectCount(Wrappers.<CreditAppeal>lambdaQuery().eq(CreditAppeal::getUserId,user.getId()).eq(CreditAppeal::getStatus,"PENDING"))>0)throw new BizException("已有待审核申请，请勿重复提交");
        if(appeals.selectCount(Wrappers.<CreditAppeal>lambdaQuery().eq(CreditAppeal::getUserId,user.getId()).eq(CreditAppeal::getStatus,"APPROVED").ge(CreditAppeal::getReviewTime,LocalDateTime.now().minusDays(7)))>0)throw new BizException("信用恢复通过后7天内不能再次申请");
        CreditAppeal row=new CreditAppeal();row.setUserId(user.getId());row.setReason(reason.trim());row.setStatus("PENDING");row.setCreateTime(LocalDateTime.now());appeals.insert(row);
        messages.pushToRoles(List.of("ADMIN"),"信用恢复申请待审核","用户ID "+user.getId()+" 提交信用恢复申请，请在用户管理中审核","CREDIT",row.getId());
        return row;
    }
    @Transactional public CreditAppeal review(Long id,boolean approve,String reason){
        if(!UserContext.isAdmin())throw new BizException(403,"仅管理员可审核");
        if(reason==null||reason.trim().length()<5||reason.trim().length()>200)throw new BizException("审核原因须为5–200字");
        CreditAppeal row=appeals.lock(id);
        if(row==null||!"PENDING".equals(row.getStatus()))throw new BizException("申请不存在或已经审核");
        if(approve){
            SysUser user=users.selectByIdForUpdate(row.getUserId());
            if(user==null)throw new BizException("用户不存在");
            credit.adjust(row.getUserId(),Math.max(user.getCreditScore()==null?100:user.getCreditScore(),70),"恢复申请 #"+id+" 审核通过："+reason.trim());
        }
        row.setStatus(approve?"APPROVED":"REJECTED");row.setReviewReason(reason.trim());row.setReviewerId(UserContext.userId());row.setReviewTime(LocalDateTime.now());appeals.updateById(row);
        messages.push(row.getUserId(),"信用恢复申请已审核",(approve?"审核通过，信用分至少恢复至70分。近期爽约限制仍按记录生效。":"申请未通过。")+" 审核说明："+reason.trim(),"CREDIT",row.getId());
        return row;
    }
}
