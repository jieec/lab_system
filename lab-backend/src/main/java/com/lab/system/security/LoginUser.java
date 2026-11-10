package com.lab.system.security;

import lombok.Data;

/**
 * 登录态用户（从 token 解析，仅含轻量字段）
 */
@Data
public class LoginUser {

    private Long id;
    private String username;
    private String realName;
    private String role;

    /** 令牌版本，与 sys_user.token_version 比对，不一致即视为已失效 */
    private Integer tokenVersion;
}
