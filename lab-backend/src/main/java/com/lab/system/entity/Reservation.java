package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 实验室预约单
 */
@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 预约单号 */
    private String orderNo;

    private Long userId;

    private Long labId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 使用人数 */
    private Integer headcount;

    /** 使用用途 */
    private String purpose;

    private String remark;

    /** PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回 / CANCELLED 已取消 / FINISHED 已完成 */
    private String status;

    private Long approverId;

    private LocalDateTime approveTime;

    private String approveRemark;

    /** 签到时间 */
    private LocalDateTime checkinTime;

    /** NONE 未签到 / CHECKED_IN 已签到 / NO_SHOW 爽约 */
    private String checkinStatus;

    /** 信用分（冗余展示，来自预约人） */
    @TableField(exist = false)
    private Integer creditScore;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String userName;

    @TableField(exist = false)
    private String userNo;

    @TableField(exist = false)
    private String dept;

    @TableField(exist = false)
    private String labName;

    @TableField(exist = false)
    private String labCode;

    @TableField(exist = false)
    private String building;

    @TableField(exist = false)
    private String room;

    @TableField(exist = false)
    private String approverName;
}
