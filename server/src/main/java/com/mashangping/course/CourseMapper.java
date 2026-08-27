package com.mashangping.course;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /** 每课互斥锁：FOR UPDATE 行锁在调用方事务内持有至事务结束（锁序恒 course 先） */
    @Select("SELECT id FROM course WHERE id = #{id} FOR UPDATE")
    Long selectByIdForUpdate(@Param("id") Long id);
}
