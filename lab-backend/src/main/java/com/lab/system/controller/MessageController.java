package com.lab.system.controller;

import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.entity.Message;
import com.lab.system.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "站内信", description = "消息列表、未读数与已读标记")
public class MessageController {

    private final MessageService messageService;

    @GetMapping("/unread-count")
    @Operation(summary = "查询未读消息数")
    public R<Long> unreadCount() {
        return R.ok(messageService.unreadCount());
    }

    @GetMapping
    @Operation(summary = "我的消息列表")
    public R<PageResult<Message>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String isRead) {
        return R.ok(messageService.page(pageNum, pageSize, isRead));
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条已读")
    public R<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return R.ok();
    }

    @PutMapping("/read-all")
    @Operation(summary = "全部标记已读")
    public R<Void> markAllRead() {
        messageService.markAllRead();
        return R.ok();
    }
}
