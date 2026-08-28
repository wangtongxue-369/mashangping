package com.mashangping.gradebook;

import com.mashangping.judging.Submission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface GradebookMapper extends BaseMapper<Submission> {

    /** (studentId, assignmentProblemId) 的最高有效分；无提交的 (user, ap) 组合不出现 */
    @Select("""
            SELECT s.user_id AS studentId, s.assignment_problem_id AS assignmentProblemId,
                   MAX(CASE WHEN s.status = 'AC' THEN s.score ELSE 0 END) AS bestScore
            FROM submission s
            WHERE s.assignment_id = #{assignmentId}
              AND s.assignment_problem_id IS NOT NULL
            GROUP BY s.user_id, s.assignment_problem_id
            """)
    List<Map<String, Object>> selectBestScores(@Param("assignmentId") long assignmentId);

    /** 每个学生总分 = 各题最高有效分之和（先按 (user, ap) 取最高再跨题累加） */
    @Select("""
            SELECT per.studentId, SUM(per.best) AS total
            FROM (
                SELECT s.user_id AS studentId, s.assignment_problem_id,
                       MAX(CASE WHEN s.status = 'AC' THEN s.score ELSE 0 END) AS best
                FROM submission s
                WHERE s.assignment_id = #{assignmentId}
                  AND s.assignment_problem_id IS NOT NULL
                GROUP BY s.user_id, s.assignment_problem_id
            ) per
            GROUP BY per.studentId
            """)
    List<Map<String, Object>> selectTotals(@Param("assignmentId") long assignmentId);
}