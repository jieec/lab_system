package com.lab.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户：学生 / 教师 / 管理员 / 维修员
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 密码（BCrypt），不返回给前端 */
    @JsonIgnore
    private String password;

    private String realName;

    /** ADMIN / TEACHER / STUDENT / REPAIRER */
    private String role;

    /** 学号 / 工号 */
    private String userNo;

    /** 学院 / 部门 */
    private String dept;

    private String phone;

    private String email;

    private String avatar;

    /** 1 正常 0 停用 */
    private Integer status;

    /** 令牌版本：改密码、重置密码、停用用户时自增，使已签发的旧 token 立即失效 */
    private Integer tokenVersion;

    /** 信用分 0–100，低于 60 禁止预约 */
    private Integer creditScore;

    /** 累计爽约次数 */
    private Integer violationCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
