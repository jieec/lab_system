package com.lab.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.system.entity.Lab;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 实验室 Mapper
 */
public interface LabMapper extends BaseMapper<Lab> {

    /**
     * 以当前读锁住实验室行。必须在事务中调用。
     * 把同一间实验室的预约写操作串行化，避免「先查冲突再插入」在并发下双通过。
     * 放在事务第一条语句，可保证等待锁的事务在拿到锁后才建立 read view，
     * 从而能看到前一个事务已提交的预约记录。
     */
    @Select("SELECT id FROM lab WHERE id = #{labId} FOR UPDATE")
    Long selectIdForUpdate(@Param("labId") Long labId);
}
