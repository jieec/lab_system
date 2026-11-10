package com.lab.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.BizException;
import com.lab.system.common.PageResult;
import com.lab.system.entity.Equipment;
import com.lab.system.entity.Lab;
import com.lab.system.entity.Reservation;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.EquipmentMapper;
import com.lab.system.mapper.LabMapper;
import com.lab.system.mapper.ReservationMapper;
import com.lab.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实验室管理
 */
@Service
@RequiredArgsConstructor
public class LabService {

    private final LabMapper labMapper;
    private final SysUserMapper userMapper;
    private final EquipmentMapper equipmentMapper;
    private final ReservationMapper reservationMapper;

    public PageResult<Lab> page(int pageNum, int pageSize, String keyword, String building, Integer status) {
        LambdaQueryWrapper<Lab> wrapper = Wrappers.<Lab>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Lab::getName, keyword).or()
                        .like(Lab::getCode, keyword).or()
                        .like(Lab::getRoom, keyword))
                .eq(StringUtils.hasText(building), Lab::getBuilding, building)
                .eq(status != null, Lab::getStatus, status)
                .orderByAsc(Lab::getId);

        Page<Lab> page = labMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        fillExtra(page.getRecords());
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public List<Lab> listAll() {
        List<Lab> labs = labMapper.selectList(Wrappers.<Lab>lambdaQuery()
                .eq(Lab::getStatus, 1)
                .orderByAsc(Lab::getId));
        fillExtra(labs);
        return labs;
    }

    public List<String> buildings() {
        return labMapper.selectList(Wrappers.<Lab>lambdaQuery().select(Lab::getBuilding))
                .stream()
                .map(Lab::getBuilding)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public Lab detail(Long id) {
        Lab lab = labMapper.selectById(id);
        if (lab == null) {
            throw new BizException("实验室不存在");
        }
        fillExtra(List.of(lab));
        return lab;
    }

    public List<Equipment> equipmentsOf(Long labId) {
        return equipmentMapper.selectList(Wrappers.<Equipment>lambdaQuery()
                .eq(Equipment::getLabId, labId)
                .orderByAsc(Equipment::getId));
    }

    public void save(Lab lab) {
        checkCode(lab);
        lab.setId(null);
        if (lab.getStatus() == null) {
            lab.setStatus(1);
        }
        if (lab.getNeedApproval() == null) {
            lab.setNeedApproval(1);
        }
        labMapper.insert(lab);
    }

    public void update(Lab lab) {
        checkCode(lab);
        labMapper.updateById(lab);
    }

    public void delete(Long id) {
        Long count = reservationMapper.selectCount(
                Wrappers.<Reservation>lambdaQuery().eq(Reservation::getLabId, id)
                        .in(Reservation::getStatus, "PENDING", "APPROVED"));
        if (count > 0) {
            throw new BizException("该实验室存在未完成的预约，无法删除");
        }
        Long eqCount = equipmentMapper.selectCount(
                Wrappers.<Equipment>lambdaQuery().eq(Equipment::getLabId, id));
        if (eqCount > 0) {
            throw new BizException("该实验室下仍有设备，请先转移或删除设备");
        }
        labMapper.deleteById(id);
    }

    private void checkCode(Lab lab) {
        if (!StringUtils.hasText(lab.getCode())) {
            throw new BizException("实验室编号不能为空");
        }
        Long count = labMapper.selectCount(Wrappers.<Lab>lambdaQuery()
                .eq(Lab::getCode, lab.getCode())
                .ne(lab.getId() != null, Lab::getId, lab.getId()));
        if (count > 0) {
            throw new BizException("实验室编号已存在");
        }
    }

    private void fillExtra(List<Lab> labs) {
        if (labs == null || labs.isEmpty()) {
            return;
        }
        List<Long> managerIds = labs.stream().map(Lab::getManagerId).filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, String> nameMap = new HashMap<>();
        if (!managerIds.isEmpty()) {
            userMapper.selectBatchIds(managerIds).forEach(u -> nameMap.put(u.getId(), u.getRealName()));
        }

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        for (Lab lab : labs) {
            lab.setManagerName(nameMap.get(lab.getManagerId()));
            lab.setEquipmentCount(Math.toIntExact(equipmentMapper.selectCount(
                    Wrappers.<Equipment>lambdaQuery().eq(Equipment::getLabId, lab.getId()))));
            lab.setTodayCount(Math.toIntExact(reservationMapper.selectCount(
                    Wrappers.<Reservation>lambdaQuery()
                            .eq(Reservation::getLabId, lab.getId())
                            .in(Reservation::getStatus, "APPROVED", "FINISHED")
                            .lt(Reservation::getStartTime, dayEnd)
                            .gt(Reservation::getEndTime, dayStart))));
        }
    }
}
