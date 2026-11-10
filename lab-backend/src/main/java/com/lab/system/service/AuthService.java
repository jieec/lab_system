package com.lab.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lab.system.common.BizException;
import com.lab.system.dto.LoginDTO;
import com.lab.system.dto.PasswordDTO;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.JwtUtil;
import com.lab.system.security.LoginUser;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录认证与个人中心
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public Map<String, Object> login(LoginDTO dto) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !match(dto.getPassword(), user.getPassword())) {
            throw new BizException("账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已被停用，请联系管理员");
        }

        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRealName(user.getRealName());
        loginUser.setRole(user.getRole());
        loginUser.setTokenVersion(user.getTokenVersion() == null ? 0 : user.getTokenVersion());

        Map<String, Object> result = new HashMap<>();
        result.put("token", jwtUtil.generate(loginUser));
        result.put("user", user);
        return result;
    }

    public SysUser profile() {
        SysUser user = userMapper.selectById(UserContext.userId());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return user;
    }

    public void updateProfile(SysUser form) {
        SysUser user = new SysUser();
        user.setId(UserContext.userId());
        user.setRealName(form.getRealName());
        user.setPhone(form.getPhone());
        user.setEmail(form.getEmail());
        user.setAvatar(form.getAvatar());
        userMapper.updateById(user);
    }

    public void changePassword(PasswordDTO dto) {
        SysUser user = userMapper.selectById(UserContext.userId());
        if (user == null || !match(dto.getOldPassword(), user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(encoder.encode(dto.getNewPassword()));
        // 令牌版本自增：此前签发的所有 token 立即失效，需重新登录
        update.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
        userMapper.updateById(update);
    }

    public String encode(String raw) {
        return encoder.encode(raw);
    }

    /**
     * 兼容历史明文密码，便于演示数据初始化
     */
    private boolean match(String raw, String stored) {
        if (stored == null) {
            return false;
        }
        if (stored.startsWith("$2")) {
            return encoder.matches(raw, stored);
        }
        return stored.equals(raw);
    }
}
