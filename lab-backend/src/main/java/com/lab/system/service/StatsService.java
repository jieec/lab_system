package com.lab.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lab.system.entity.Equipment;
import com.lab.system.entity.Lab;
import com.lab.system.entity.RepairOrder;
import com.lab.system.entity.Reservation;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.EquipmentMapper;
import com.lab.system.mapper.LabMapper;
import com.lab.system.mapper.RepairOrderMapper;
import com.lab.system.mapper.ReservationMapper;
import com.lab.system.mapper.StatsMapper;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据看板统计
 */
@Service
@RequiredArgsConstructor
public class StatsService {

    private final StatsMapper statsMapper;
    private final LabMapper labMapper;
    private final EquipmentMapper equipmentMapper;
    private final ReservationMapper reservationMapper;
    private final RepairOrderMapper repairOrderMapper;
    private final SysUserMapper userMapper;

    public Map<String, Object> dashboard() {
        Map<String, Object> data = new LinkedHashMap<>();

        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        data.put("labTotal", labMapper.selectCount(Wrappers.<Lab>lambdaQuery().eq(Lab::getStatus, 1)));
        data.put("equipmentTotal", equipmentMapper.selectCount(null));
        data.put("equipmentIdle", equipmentMapper.selectCount(
                Wrappers.<Equipment>lambdaQuery().eq(Equipment::getStatus, "IDLE")));
        data.put("equipmentRepair", equipmentMapper.selectCount(
                Wrappers.<Equipment>lambdaQuery().eq(Equipment::getStatus, "REPAIR")));
        data.put("userTotal", userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().ne(SysUser::getRole, "ADMIN")));

        data.put("todayReservation", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getStatus, "APPROVED", "FINISHED", "PENDING")
                .lt(Reservation::getStartTime, dayEnd)
                .gt(Reservation::getEndTime, dayStart)));
        data.put("monthReservation", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .ge(Reservation::getCreateTime, monthStart)));
        data.put("pendingApproval", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, "PENDING")));
        data.put("pendingRepair", repairOrderMapper.selectCount(Wrappers.<RepairOrder>lambdaQuery()
                .ne(RepairOrder::getStatus, "VERIFIED")));

        data.put("myReservation", reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, UserContext.userId())
                .in(Reservation::getStatus, "PENDING", "APPROVED")));
        data.put("myRepair", repairOrderMapper.selectCount(Wrappers.<RepairOrder>lambdaQuery()
                .eq(RepairOrder::getReporterId, UserContext.userId())
                .ne(RepairOrder::getStatus, "VERIFIED")));

        data.put("trend", statsMapper.reservationTrend(14));
        data.put("labUsage", statsMapper.labUsage(8));
        data.put("equipmentStatus", statsMapper.equipmentStatus());
        data.put("faultType", statsMapper.faultType());
        data.put("repairRank", statsMapper.repairRank(5));
        data.put("reservationStatus", statsMapper.reservationStatus());
        return data;
    }
}
