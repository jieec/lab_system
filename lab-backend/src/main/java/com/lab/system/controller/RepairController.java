package com.lab.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.lab.system.security.OperationLog;
import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.dto.RepairCreateDTO;
import com.lab.system.dto.RepairFinishDTO;
import com.lab.system.dto.RepairVerifyDTO;
import com.lab.system.entity.RepairLog;
import com.lab.system.entity.RepairOrder;
import com.lab.system.security.RequireRole;
import com.lab.system.service.RepairService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/repairs")
@RequiredArgsConstructor
@Tag(name = "报修运维", description = "报修提交、派单、维修、验收完整闭环")
public class RepairController {

    private final RepairService repairService;

    @PostMapping
    @OperationLog(module = "报修运维", value = "提交报修")
    public R<RepairOrder> create(@Valid @RequestBody RepairCreateDTO dto) {
        return R.ok("报修提交成功", repairService.create(dto));
    }

    @GetMapping
    public R<PageResult<RepairOrder>> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) Long labId,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) String scope) {
        return R.ok(repairService.page(pageNum, pageSize, status, labId, keyword, scope));
    }

    @GetMapping("/{id}")
    public R<RepairOrder> detail(@PathVariable Long id) {
        return R.ok(repairService.detail(id));
    }

    @GetMapping("/{id}/logs")
    public R<List<RepairLog>> logs(@PathVariable Long id) {
        return R.ok(repairService.logs(id));
    }

    @PutMapping("/{id}/assign")
    @OperationLog(module = "报修运维", value = "工单派单")
    @RequireRole("ADMIN")
    public R<Void> assign(@PathVariable Long id, @RequestParam Long repairerId) {
        repairService.assign(id, repairerId);
        return R.ok();
    }

    @PutMapping("/{id}/start")
    @OperationLog(module = "报修运维", value = "开始维修")
    @RequireRole({"REPAIRER", "ADMIN"})
    public R<Void> start(@PathVariable Long id) {
        repairService.start(id);
        return R.ok();
    }

    @PutMapping("/{id}/finish")
    @OperationLog(module = "报修运维", value = "提交维修结果")
    @RequireRole({"REPAIRER", "ADMIN"})
    public R<Void> finish(@PathVariable Long id, @RequestBody RepairFinishDTO dto) {
        repairService.finish(id, dto);
        return R.ok();
    }

    @PutMapping("/{id}/verify")
    @OperationLog(module = "报修运维", value = "验收工单")
    public R<Void> verify(@PathVariable Long id, @RequestBody RepairVerifyDTO dto) {
        repairService.verify(id, dto);
        return R.ok();
    }
}
