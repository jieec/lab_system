package com.lab.system.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.system.entity.CreditAppeal;
import org.apache.ibatis.annotations.Select;
public interface CreditAppealMapper extends BaseMapper<CreditAppeal> {
    @Select("SELECT * FROM credit_appeal WHERE id=#{id} FOR UPDATE") CreditAppeal lock(Long id);
}
