package com.lab.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.system.entity.Reservation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ReservationMapper extends BaseMapper<Reservation> {

    /**
     * 当前读 + 行锁。作为审批/改期/签到等写事务的第一条语句，
     * 保证等待锁的事务在拿到锁之后才建立 read view，能看到前一个事务已提交的数据。
     */
    @Select("SELECT * FROM reservation WHERE id = #{id} FOR UPDATE")
    Reservation selectByIdForUpdate(@Param("id") Long id);
}
