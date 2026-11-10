package com.lab.system.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.lab.system.common.R;
import com.lab.system.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "统计看板", description = "首页汇总数据")
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "看板汇总数据")
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(statsService.dashboard());
    }
}
