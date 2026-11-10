package com.lab.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.PageResult;
import com.lab.system.entity.Message;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.MessageMapper;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内信。推送失败绝不影响主业务，因此全部做异常隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final SysUserMapper userMapper;

    /** 给单个用户推送 */
    public void push(Long receiverId, String title, String content, String type, Long bizId) {
        if (receiverId == null) {
            return;
        }
        try {
            Message m = new Message();
            m.setReceiverId(receiverId);
            m.setTitle(truncate(title, 150));
            m.setContent(truncate(content, 500));
            m.setType(type);
            m.setBizId(bizId);
            m.setIsRead(0);
            messageMapper.insert(m);
        } catch (Exception e) {
            log.warn("站内信推送失败 receiver={} title={}", receiverId, title, e);
        }
    }

    /** 给某类角色全员推送（如待审批提醒发给管理员与教师） */
    public void pushToRoles(List<String> roles, String title, String content, String type, Long bizId) {
        try {
            List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .in(SysUser::getRole, roles).eq(SysUser::getStatus, 1));
            for (SysUser u : users) {
                push(u.getId(), title, content, type, bizId);
            }
        } catch (Exception e) {
            log.warn("角色站内信推送失败 roles={}", roles, e);
        }
    }

    public long unreadCount() {
        return messageMapper.selectCount(Wrappers.<Message>lambdaQuery()
                .eq(Message::getReceiverId, UserContext.userId())
                .eq(Message::getIsRead, 0));
    }

    public PageResult<Message> page(int pageNum, int pageSize, String isRead) {
        // 注意：Java 会先求值所有实参，再调用 eq(condition, column, value)。
        // 若把 Integer.parseInt(isRead) 直接写在 eq 里，未传 isRead 时即使
        // condition 为 false 也会抛 NumberFormatException("Cannot parse null string")。
        Integer readFlag = null;
        if (org.springframework.util.StringUtils.hasText(isRead)) {
            try {
                readFlag = Integer.valueOf(isRead);
            } catch (NumberFormatException ignored) {
                // 非法值按「不过滤」处理，避免前端传空串导致 400
            }
        }
        Page<Message> page = messageMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getReceiverId, UserContext.userId())
                        .eq(readFlag != null, Message::getIsRead, readFlag)
                        .orderByDesc(Message::getCreateTime));
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public void markRead(Long id) {
        Message m = messageMapper.selectById(id);
        if (m == null || !m.getReceiverId().equals(UserContext.userId())) {
            return;
        }
        Message update = new Message();
        update.setId(id);
        update.setIsRead(1);
        messageMapper.updateById(update);
    }

    public int markAllRead() {
        Message update = new Message();
        update.setIsRead(1);
        return messageMapper.update(update, Wrappers.<Message>lambdaUpdate()
                .eq(Message::getReceiverId, UserContext.userId())
                .eq(Message::getIsRead, 0));
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
