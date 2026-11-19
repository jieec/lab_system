package com.lab.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.system.entity.SysUser;

public interface SysUserMapper extends BaseMapper<SysUser> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM sys_user WHERE id = #{id} FOR UPDATE")
    SysUser selectByIdForUpdate(Long id);
}
