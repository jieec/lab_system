package com.lab.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.BizException;
import com.lab.system.common.PageResult;
import com.lab.system.entity.Equipment;
import com.lab.system.entity.Lab;
import com.lab.system.entity.RepairOrder;
import com.lab.system.mapper.EquipmentMapper;
import com.lab.system.mapper.LabMapper;
import com.lab.system.mapper.RepairOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备台账管理
 */
@Service
@RequiredArgsConstructor
public class EquipmentService {

    private final EquipmentMapper equipmentMapper;
    private final LabMapper labMapper;
    private final RepairOrderMapper repairOrderMapper;

    public PageResult<Equipment> page(int pageNum, int pageSize, String keyword, Long labId,
                                      String category, String status) {
        LambdaQueryWrapper<Equipment> wrapper = Wrappers.<Equipment>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Equipment::getName, keyword).or()
                        .like(Equipment::getCode, keyword).or()
                        .like(Equipment::getModel, keyword))
                .eq(labId != null, Equipment::getLabId, labId)
                .eq(StringUtils.hasText(category), Equipment::getCategory, category)
                .eq(StringUtils.hasText(status), Equipment::getStatus, status)
                .orderByAsc(Equipment::getId);

        Page<Equipment> page = equipmentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillLabName(page.getRecords());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public List<Equipment> listByLab(Long labId) {
        List<Equipment> list = equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getLabId, labId)
                .orderByAsc(Equipment::getId));
        fillLabName(list);
        return list;
    }

    public List<String> categories() {
        return equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery().select(Equipment::getCategory))
                .stream()
                .map(Equipment::getCategory)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    public Equipment detail(Long id) {
        Equipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BizException("设备不存在");
        }
        fillLabName(List.of(equipment));
        return equipment;
    }

    public void save(Equipment equipment) {
        checkCode(equipment);
        equipment.setId(null);
        if (!StringUtils.hasText(equipment.getStatus())) {
            equipment.setStatus("IDLE");
        }
        equipmentMapper.insert(equipment);
    }

    public void update(Equipment equipment) {
        checkCode(equipment);
        equipmentMapper.updateById(equipment);
    }

    public void delete(Long id) {
        Long count = repairOrderMapper.selectCount(Wrappers.<RepairOrder>lambdaQuery()
                .eq(RepairOrder::getEquipmentId, id)
                .notIn(RepairOrder::getStatus, "VERIFIED"));
        if (count > 0) {
            throw new BizException("该设备存在未完成的报修工单，无法删除");
        }
        equipmentMapper.deleteById(id);
    }

    public void changeStatus(Long id, String status) {
        Equipment equipment = new Equipment();
        equipment.setId(id);
        equipment.setStatus(status);
        equipmentMapper.updateById(equipment);
    }

    private void checkCode(Equipment equipment) {
        if (!StringUtils.hasText(equipment.getCode())) {
            throw new BizException("设备编号不能为空");
        }
        Long count = equipmentMapper.selectCount(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getCode, equipment.getCode())
                .ne(equipment.getId() != null, Equipment::getId, equipment.getId()));
        if (count > 0) {
            throw new BizException("设备编号已存在");
        }
    }

    private void fillLabName(List<Equipment> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> labIds = list.stream().map(Equipment::getLabId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Lab> labMap = new HashMap<>();
        if (!labIds.isEmpty()) {
            labMapper.selectBatchIds(labIds).forEach(l -> labMap.put(l.getId(), l));
        }
        for (Equipment e : list) {
            Lab lab = labMap.get(e.getLabId());
            if (lab != null) {
                e.setLabName(lab.getName());
                e.setLabRoom(lab.getBuilding() + " " + lab.getRoom());
            }
        }
    }
}
