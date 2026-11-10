package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long receiverId;

    private String title;

    private String content;

    /** RESERVATION / REPAIR / CREDIT / SYSTEM */
    private String type;

    private Long bizId;

    /** 1 已读 0 未读 */
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
