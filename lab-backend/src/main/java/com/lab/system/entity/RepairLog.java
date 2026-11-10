package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报修工单流转记录
 */
@Data
@TableName("repair_log")
public class RepairLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long operatorId;

    private String operatorName;

    /** CREATE / ASSIGN / START / FINISH / VERIFY */
    private String action;

    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
