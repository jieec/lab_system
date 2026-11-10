package com.lab.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.lab.system.security.OperationLog;
import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.entity.Equipment;
import com.lab.system.entity.Lab;
import com.lab.system.security.RequireRole;
import com.lab.system.service.LabService;
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
@RequestMapping("/api/labs")
@RequiredArgsConstructor
@Tag(name = "实验室管理", description = "实验室台账、开放时间与设备清单")
public class LabController {

    private final LabService labService;

    @GetMapping
    public R<PageResult<Lab>> page(@RequestParam(defaultValue = "1") int pageNum,
                                   @RequestParam(defaultValue = "9") int pageSize,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) String building,
                                   @RequestParam(required = false) Integer status) {
        return R.ok(labService.page(pageNum, pageSize, keyword, building, status));
    }

    @GetMapping("/all")
    public R<List<Lab>> all() {
        return R.ok(labService.listAll());
    }

    @GetMapping("/buildings")
    public R<List<String>> buildings() {
        return R.ok(labService.buildings());
    }

    @GetMapping("/{id}")
    public R<Lab> detail(@PathVariable Long id) {
        return R.ok(labService.detail(id));
    }

    @GetMapping("/{id}/equipments")
    public R<List<Equipment>> equipments(@PathVariable Long id) {
        return R.ok(labService.equipmentsOf(id));
    }

    @PostMapping
    @OperationLog(module = "实验室管理", value = "新增记录")
    @RequireRole("ADMIN")
    public R<Void> save(@RequestBody Lab lab) {
        labService.save(lab);
        return R.ok();
    }

    @PutMapping
    @OperationLog(module = "实验室管理", value = "修改记录")
    @RequireRole("ADMIN")
    public R<Void> update(@RequestBody Lab lab) {
        labService.update(lab);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "实验室管理", value = "删除记录")
    @RequireRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        labService.delete(id);
        return R.ok();
    }
}
