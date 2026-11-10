package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备台账
 */
@Data
@TableName("equipment")
public class Equipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 资产编号 */
    private String code;

    private String name;

    /** 设备类别：仪器仪表 / 计算设备 / 实验装置 ... */
    private String category;

    private Long labId;

    private String model;

    private String brand;

    private LocalDate purchaseDate;

    private BigDecimal price;

    /** IDLE 空闲 / IN_USE 使用中 / REPAIR 维修中 / SCRAPPED 已报废 */
    private String status;

    private String keeper;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String labName;

    @TableField(exist = false)
    private String labRoom;
}
