package com.lab.system.security;

import com.lab.system.common.BizException;

/**
 * 当前请求的登录用户上下文
 */
public class UserContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        return user;
    }

    /** 不抛异常的读取，供日志切面等非业务场景使用 */
    public static LoginUser peek() {
        return HOLDER.get();
    }

    public static Long userId() {
        return get().getId();
    }

    public static String role() {
        return get().getRole();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(role());
    }

    public static void clear() {
        HOLDER.remove();
    }
}
