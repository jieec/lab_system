package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志
 */
@Data
@TableName("sys_log")
public class SysLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    private String operatorName;

    private String module;

    private String action;

    private String method;

    private String uri;

    private String ip;

    private String params;

    /** 1 成功 0 失败 */
    private Integer success;

    private String errorMsg;

    private Long costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
