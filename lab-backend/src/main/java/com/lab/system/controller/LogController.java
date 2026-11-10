package com.lab.system.controller;

import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.entity.SysLog;
import com.lab.system.security.RequireRole;
import com.lab.system.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@RequireRole("ADMIN")
@Tag(name = "审计日志", description = "操作留痕查询，仅管理员可见")
public class LogController {

    private final LogService logService;

    @GetMapping
    @Operation(summary = "分页查询操作日志")
    public R<PageResult<SysLog>> page(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize,
                                      @RequestParam(required = false) String module,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(logService.page(pageNum, pageSize, module, keyword));
    }
}
