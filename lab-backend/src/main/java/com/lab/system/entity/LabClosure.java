package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 实验室闭馆安排（labId 为空表示全校闭馆）
 */
@Data
@TableName("lab_closure")
public class LabClosure {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long labId;

    private LocalDate closureDate;

    private String reason;

    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private String labName;
}
