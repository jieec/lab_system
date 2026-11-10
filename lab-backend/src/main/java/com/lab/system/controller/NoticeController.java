package com.lab.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.lab.system.security.OperationLog;
import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.entity.Notice;
import com.lab.system.security.RequireRole;
import com.lab.system.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
@Tag(name = "通知公告", description = "公告发布与查询")
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public R<PageResult<Notice>> page(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(noticeService.page(pageNum, pageSize, keyword));
    }

    @GetMapping("/latest")
    public R<List<Notice>> latest(@RequestParam(defaultValue = "5") int limit) {
        return R.ok(noticeService.latest(limit));
    }

    @PostMapping
    @OperationLog(module = "通知公告", value = "新增记录")
    @RequireRole("ADMIN")
    public R<Void> save(@RequestBody Notice notice) {
        noticeService.save(notice);
        return R.ok();
    }

    @PutMapping
    @OperationLog(module = "通知公告", value = "修改记录")
    @RequireRole("ADMIN")
    public R<Void> update(@RequestBody Notice notice) {
        noticeService.update(notice);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "通知公告", value = "删除记录")
    @RequireRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        noticeService.delete(id);
        return R.ok();
    }
}
