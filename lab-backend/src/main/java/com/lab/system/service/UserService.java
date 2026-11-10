package com.lab.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.system.common.BizException;
import com.lab.system.common.PageResult;
import com.lab.system.entity.SysUser;
import com.lab.system.mapper.SysUserMapper;
import com.lab.system.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户管理
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final SysUserMapper userMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public PageResult<SysUser> page(int pageNum, int pageSize, String keyword, String role, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword).or()
                        .like(SysUser::getRealName, keyword).or()
                        .like(SysUser::getUserNo, keyword))
                .eq(StringUtils.hasText(role), SysUser::getRole, role)
                .eq(status != null, SysUser::getStatus, status)
                .orderByAsc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    public List<SysUser> options(String role) {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(StringUtils.hasText(role), SysUser::getRole, role)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getId));
    }

    public void save(SysUser user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BizException("账号不能为空");
        }
        Long exists = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, user.getUsername()));
        if (exists > 0) {
            throw new BizException("账号已存在");
        }
        user.setId(null);
        user.setPassword(encoder.encode(DEFAULT_PASSWORD));
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        userMapper.insert(user);
    }

    public void update(SysUser user) {
        SysUser db = userMapper.selectById(user.getId());
        if (db == null) {
            throw new BizException("用户不存在");
        }
        user.setPassword(null);
        user.setUsername(null);
        userMapper.updateById(user);
    }

    public void delete(Long id) {
        if (id.equals(UserContext.userId())) {
            throw new BizException("不能删除当前登录账号");
        }
        SysUser db = userMapper.selectById(id);
        if (db != null && "ADMIN".equals(db.getRole())) {
            throw new BizException("管理员账号不允许删除");
        }
        userMapper.deleteById(id);
    }

    public String resetPassword(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(encoder.encode(DEFAULT_PASSWORD));
        userMapper.updateById(user);
        bumpTokenVersion(id);
        return DEFAULT_PASSWORD;
    }

    public void toggleStatus(Long id, Integer status) {
        if (id.equals(UserContext.userId())) {
            throw new BizException("不能停用当前登录账号");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);
        bumpTokenVersion(id);
    }

    /** 令牌版本自增，使该用户此前签发的所有 token 立即失效 */
    private void bumpTokenVersion(Long id) {
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .setSql("token_version = IFNULL(token_version, 0) + 1")
                .eq(SysUser::getId, id));
    }
}
