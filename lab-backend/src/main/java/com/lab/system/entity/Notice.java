package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知公告
 */
@Data
@TableName("notice")
public class Notice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** NOTICE 通知 / MAINTENANCE 维护 / SYSTEM 系统 */
    private String type;

    private Long publisherId;

    private String publisherName;

    /** 1 发布 0 下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
