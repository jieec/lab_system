package com.lab.system.controller;

import com.lab.system.common.R;
import com.lab.system.entity.LabClosure;
import com.lab.system.security.OperationLog;
import com.lab.system.security.RequireRole;
import com.lab.system.service.LabClosureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/closures")
@RequiredArgsConstructor
@Tag(name = "闭馆安排", description = "节假日或临时闭馆，闭馆日期不可预约")
public class LabClosureController {

    private final LabClosureService closureService;

    @GetMapping
    @Operation(summary = "查询闭馆日期")
    public R<List<LabClosure>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(closureService.list(from, to));
    }

    @PostMapping
    @RequireRole("ADMIN")
    @Operation(summary = "新增闭馆日期")
    @OperationLog(module = "闭馆管理", value = "新增闭馆安排")
    public R<Void> add(@RequestBody LabClosure closure) {
        closureService.add(closure);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole("ADMIN")
    @Operation(summary = "删除闭馆日期")
    @OperationLog(module = "闭馆管理", value = "删除闭馆安排")
    public R<Void> remove(@PathVariable Long id) {
        closureService.remove(id);
        return R.ok();
    }
}
