package com.lab.system.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 统计报表查询
 */
public interface StatsMapper {

    /** 预约状态分布 */
    @Select("SELECT status AS label, COUNT(*) AS value FROM reservation GROUP BY status")
    List<Map<String, Object>> reservationStatus();

    /** 近 N 天预约申请趋势 */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%m-%d') AS label, COUNT(*) AS value
            FROM reservation
            WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE_FORMAT(create_time, '%m-%d')
            ORDER BY label
            """)
    List<Map<String, Object>> reservationTrend(@Param("days") int days);

    /** 实验室预约热度 Top N */
    @Select("""
            SELECT l.name AS label, COUNT(r.id) AS value
            FROM lab l
            LEFT JOIN reservation r ON r.lab_id = l.id AND r.status = 'APPROVED'
            GROUP BY l.id, l.name
            ORDER BY value DESC, l.id
            LIMIT #{limit}
            """)
    List<Map<String, Object>> labUsage(@Param("limit") int limit);

    /** 设备状态分布 */
    @Select("SELECT status AS label, COUNT(*) AS value FROM equipment GROUP BY status")
    List<Map<String, Object>> equipmentStatus();

    /** 故障类型分布 */
    @Select("SELECT fault_type AS label, COUNT(*) AS value FROM repair_order GROUP BY fault_type")
    List<Map<String, Object>> faultType();

    /** 报修次数最多的设备 Top N */
    @Select("""
            SELECT e.name AS label, COUNT(*) AS value
            FROM repair_order r
            JOIN equipment e ON e.id = r.equipment_id
            GROUP BY e.id, e.name
            ORDER BY value DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> repairRank(@Param("limit") int limit);
}
