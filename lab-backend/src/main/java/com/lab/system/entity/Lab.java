package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 实验室
 */
@Data
@TableName("lab")
public class Lab {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 实验室编号，如 LAB-A301 */
    private String code;

    private String name;

    private String building;

    private String room;

    /** 可容纳人数 */
    private Integer capacity;

    /** 开放开始时间 */
    private LocalTime openTime;

    /** 开放结束时间 */
    private LocalTime closeTime;

    /** 是否需要审批 1 是 0 否 */
    private Integer needApproval;

    /** 负责人 */
    private Long managerId;

    /** 1 开放 0 停用 */
    private Integer status;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String managerName;

    @TableField(exist = false)
    private Integer equipmentCount;

    /** 今日已预约时段数 */
    @TableField(exist = false)
    private Integer todayCount;
}
