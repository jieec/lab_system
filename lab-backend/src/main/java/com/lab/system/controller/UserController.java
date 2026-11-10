package com.lab.system.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.lab.system.security.OperationLog;
import com.lab.system.common.PageResult;
import com.lab.system.common.R;
import com.lab.system.entity.SysUser;
import com.lab.system.security.RequireRole;
import com.lab.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查、重置密码与启停")
public class UserController {

    private final UserService userService;

    @GetMapping
    @RequireRole("ADMIN")
    public R<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String role,
                                       @RequestParam(required = false) Integer status) {
        return R.ok(userService.page(pageNum, pageSize, keyword, role, status));
    }

    /** 下拉选项，管理员和教师可用 */
    @GetMapping("/options")
    @RequireRole({"ADMIN", "TEACHER"})
    public R<List<SysUser>> options(@RequestParam(required = false) String role) {
        return R.ok(userService.options(role));
    }

    @PostMapping
    @OperationLog(module = "用户管理", value = "新增记录")
    @RequireRole("ADMIN")
    public R<Void> save(@RequestBody SysUser user) {
        userService.save(user);
        return R.ok("新增成功，初始密码 123456", null);
    }

    @PutMapping
    @OperationLog(module = "用户管理", value = "修改记录")
    @RequireRole("ADMIN")
    public R<Void> update(@RequestBody SysUser user) {
        userService.update(user);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @OperationLog(module = "用户管理", value = "删除记录")
    @RequireRole("ADMIN")
    public R<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/reset-password")
    @OperationLog(module = "用户管理", value = "重置用户密码")
    @RequireRole("ADMIN")
    public R<String> resetPassword(@PathVariable Long id) {
        String pwd = userService.resetPassword(id);
        return R.ok("密码已重置为 " + pwd, pwd);
    }

    @PutMapping("/{id}/status")
    @OperationLog(module = "用户管理", value = "启用或停用账号")
    @RequireRole("ADMIN")
    public R<Void> toggleStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.toggleStatus(id, status);
        return R.ok();
    }
}
