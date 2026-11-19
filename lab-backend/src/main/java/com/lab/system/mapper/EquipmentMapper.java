package com.lab.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.system.entity.Equipment;

public interface EquipmentMapper extends BaseMapper<Equipment> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM equipment WHERE id = #{id} FOR UPDATE")
    Equipment selectByIdForUpdate(Long id);
}
