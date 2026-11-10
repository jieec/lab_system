package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备报修工单
 */
@Data
@TableName("repair_order")
public class RepairOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单号 */
    private String orderNo;

    private Long equipmentId;

    private Long labId;

    private Long reporterId;

    /** 故障类型：硬件故障 / 软件故障 / 耗材缺失 / 其他 */
    private String faultType;

    /** 紧急程度：LOW / NORMAL / HIGH */
    private String urgency;

    private String faultDesc;

    /** PENDING 待派单 / ASSIGNED 已派单 / REPAIRING 维修中 / DONE 待验收 / VERIFIED 已完成 */
    private String status;

    private Long repairerId;

    private LocalDateTime assignTime;

    private LocalDateTime finishTime;

    private String repairDesc;

    /** 维修费用 */
    private BigDecimal cost;

    /** 验收评分 1-5 */
    private Integer score;

    private String verifyRemark;

    private LocalDateTime verifyTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String equipmentName;

    @TableField(exist = false)
    private String equipmentCode;

    @TableField(exist = false)
    private String labName;

    @TableField(exist = false)
    private String reporterName;

    @TableField(exist = false)
    private String repairerName;
}
