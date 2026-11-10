package com.lab.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.lab.system.security.OperationLog;
import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.entity.Equipment;
import com.lab.system.security.RequireRole;
import com.lab.system.service.EquipmentService;
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
@RequestMapping("/api/equipments")
@RequiredArgsConstructor
@Tag(name = "设备台账", description = "设备档案、状态变更与按实验室查询")
public class EquipmentController {

    private final EquipmentService equipmentService;

    @GetMapping
    public R<PageResult<Equipment>> page(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long labId,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String status) {
        return R.ok(equipmentService.page(pageNum, pageSize, keyword, labId, category, status));
    }

    @GetMapping("/categories")
    public R<List<String>> categories() {
        return R.ok(equipmentService.categories());
    }

    @GetMapping("/by-lab/{labId}")
    public R<List<Equipment>> byLab(@PathVariable Long labId) {
        return R.ok(equipmentService.listByLab(labId));
    }

    @GetMapping("/{id}")
    public R<Equipment> detail(@PathVariable Long id) {
        return R.ok(equipmentService.detail(id));
    }

    @PostMapping
    @OperationLog(module = "设备管理", value = "新增记录")
    @RequireRole("ADMIN")
    public R<Void> save(@RequestBody Equipment equipment) {
        equipmentService.save(equipment);
        return R.ok();
    }

    @PutMapping
    @OperationLog(module = "设备管理", value = "修改记录")
    @RequireRole("ADMIN")
    public R<Void> update(@RequestBody Equipment equipment) {
        equipmentService.update(equipment);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "设备管理", value = "删除记录")
    @RequireRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @OperationLog(module = "设备管理", value = "变更设备状态")
    @RequireRole("ADMIN")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam String status) {
        equipmentService.changeStatus(id, status);
        return R.ok();
    }
}
