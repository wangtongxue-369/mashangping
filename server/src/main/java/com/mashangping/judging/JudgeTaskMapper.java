package com.mashangping.judging;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface JudgeTaskMapper extends BaseMapper<JudgeTask> {

    /** 原子抢占：仅当仍为 PENDING 时标 RUNNING，影响行数=领到与否 */
    @Update("UPDATE judge_task SET status = 'RUNNING' WHERE id = #{id} AND status = 'PENDING'")
    int claimById(@Param("id") Long id);

    /** 重试回队：PENDING 化且重试计数+1（仅 RUNNING 态允许，防双重回队） */
    @Update("UPDATE judge_task SET status = 'PENDING', retry_count = retry_count + 1 "
            + "WHERE id = #{id} AND status = 'RUNNING'")
    int requeueForRetry(@Param("id") Long id);
}
