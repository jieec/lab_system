package com.lab.system.service;

import com.lab.system.common.BizException;
import com.lab.system.dto.LoginDTO;
import com.lab.system.dto.PasswordDTO;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.JwtUtil;
import com.lab.system.security.LoginUser;
import com.lab.system.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 登录认证、密码校验与改密码后的令牌版本自增。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private JwtUtil jwtUtil;
    @InjectMocks
    private AuthService authService;

    private SysUser user;

    @BeforeEach
    void setUp() {
        user = new SysUser();
        user.setId(1L);
        user.setUsername("student01");
        user.setRealName("张三");
        user.setRole("STUDENT");
        user.setStatus(1);
        user.setTokenVersion(2);
        user.setPassword(authService.encode("123456"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private LoginDTO loginDTO(String username, String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        return dto;
    }

    @Test
    @DisplayName("账号密码正确时签发 token，并把 token_version 写进登录态")
    void loginSuccess() {
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtil.generate(any())).thenReturn("jwt-token");

        Map<String, Object> result = authService.login(loginDTO("student01", "123456"));

        assertEquals("jwt-token", result.get("token"));
        ArgumentCaptor<LoginUser> captor = ArgumentCaptor.forClass(LoginUser.class);
        verify(jwtUtil).generate(captor.capture());
        assertEquals(2, captor.getValue().getTokenVersion());
        assertEquals("STUDENT", captor.getValue().getRole());
    }

    @Test
    @DisplayName("密码错误拒绝登录")
    void loginWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(user);
        assertThrows(BizException.class, () -> authService.login(loginDTO("student01", "wrong")));
    }

    @Test
    @DisplayName("停用账号拒绝登录")
    void loginDisabledUser() {
        user.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        assertThrows(BizException.class, () -> authService.login(loginDTO("student01", "123456")));
    }

    @Test
    @DisplayName("兼容历史明文密码（演示数据）")
    void loginLegacyPlaintext() {
        user.setPassword("123456");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(jwtUtil.generate(any())).thenReturn("jwt-token");
        assertEquals("jwt-token", authService.login(loginDTO("student01", "123456")).get("token"));
    }

    @Test
    @DisplayName("修改密码后 token_version 自增且新密码为 BCrypt")
    void changePasswordBumpsVersion() {
        LoginUser ctx = new LoginUser();
        ctx.setId(1L);
        UserContext.set(ctx);
        when(userMapper.selectById(1L)).thenReturn(user);

        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("123456");
        dto.setNewPassword("newpass666");
        authService.changePassword(dto);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        SysUser saved = captor.getValue();
        assertEquals(3, saved.getTokenVersion());
        assertNotEquals("123456", saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"));
        assertTrue(authService.encode("newpass666").matches("^\\$2.*$"));
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
                .matches("newpass666", saved.getPassword()));
    }

    @Test
    @DisplayName("原密码不正确时拒绝修改")
    void changePasswordWrongOld() {
        LoginUser ctx = new LoginUser();
        ctx.setId(1L);
        UserContext.set(ctx);
        when(userMapper.selectById(1L)).thenReturn(user);

        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("bad-old");
        dto.setNewPassword("newpass666");
        assertThrows(BizException.class, () -> authService.changePassword(dto));
    }
}
