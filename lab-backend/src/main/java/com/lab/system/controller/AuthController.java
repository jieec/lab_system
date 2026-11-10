package com.lab.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.lab.system.security.OperationLog;
import com.lab.system.common.R;
import com.lab.system.dto.LoginDTO;
import com.lab.system.dto.PasswordDTO;
import com.lab.system.entity.SysUser;
import com.lab.system.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "登录认证", description = "登录、个人中心与密码管理")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @OperationLog(module = "认证管理", value = "用户登录")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok("登录成功", authService.login(dto));
    }

    @GetMapping("/profile")
    public R<SysUser> profile() {
        return R.ok(authService.profile());
    }

    @PutMapping("/profile")
    @OperationLog(module = "认证管理", value = "修改个人资料")
    public R<Void> updateProfile(@RequestBody SysUser user) {
        authService.updateProfile(user);
        return R.ok();
    }

    @PutMapping("/password")
    @OperationLog(module = "认证管理", value = "修改密码")
    public R<Void> changePassword(@Valid @RequestBody PasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok();
    }
}
