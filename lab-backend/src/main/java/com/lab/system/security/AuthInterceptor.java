package com.lab.system.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.system.common.R;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 登录与角色校验拦截器
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final SysUserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            return reject(response, 401, "请先登录");
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        LoginUser user;
        try {
            user = jwtUtil.parse(token);
        } catch (Exception e) {
            return reject(response, 401, "登录已过期，请重新登录");
        }
        // 令牌版本与账号状态实时校验：改密码 / 停用后旧 token 立即失效
        SysUser dbUser = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getId, user.getId())
                .select(SysUser::getId, SysUser::getStatus, SysUser::getTokenVersion));
        if (dbUser == null || dbUser.getStatus() == null || dbUser.getStatus() != 1) {
            return reject(response, 401, "账号已停用，请重新登录");
        }
        int currentVer = dbUser.getTokenVersion() == null ? 0 : dbUser.getTokenVersion();
        int tokenVer = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (tokenVer != currentVer) {
            return reject(response, 401, "登录状态已变更，请重新登录");
        }

        UserContext.set(user);

        RequireRole requireRole = method.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = method.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null && Arrays.stream(requireRole.value()).noneMatch(r -> r.equals(user.getRole()))) {
            return reject(response, 403, "没有操作权限");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean reject(HttpServletResponse response, int code, String msg) throws Exception {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(R.fail(code, msg)));
        return false;
    }
}
