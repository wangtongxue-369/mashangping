package com.mashangping.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.common.LikeUtils;
import com.mashangping.course.dto.CourseUpsertRequest;
import com.mashangping.problem.CourseProblem;
import com.mashangping.problem.CourseProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final CourseProblemMapper courseProblemMapper;

    public Course create(long teacherUid, CourseUpsertRequest request) {
        Course c = new Course();
        c.setName(request.name());
        c.setTerm(request.term());
        c.setDescription(request.description());
        c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        return c;
    }

    public Page<CourseView> listMine(long teacherUid, int page, int size, String keyword) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getTeacherId, teacherUid)
                .like(keyword != null && !keyword.isBlank(), Course::getName,
                        LikeUtils.escapeForLike(keyword.trim()))
                .orderByDesc(Course::getId);
        Page<Course> result = courseMapper.selectPage(new Page<>(page, size), wrapper);
        return (Page<CourseView>) result.convert(CourseView::from);
    }

    /** 归属链校验的唯一入口：不存在或非属主一律 40400「课程不存在」 */
    public Course getOwned(long teacherUid, long courseId) {
        Course c = courseMapper.selectById(courseId);
        if (c == null || c.getTeacherId() == null || c.getTeacherId() != teacherUid) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return c;
    }

    public void update(long teacherUid, long courseId, CourseUpsertRequest request) {
        Course c = getOwned(teacherUid, courseId);
        c.setName(request.name());
        c.setTerm(request.term());
        c.setDescription(request.description());
        courseMapper.updateById(c);
    }

    @Transactional
    public void delete(long teacherUid, long courseId) {
        getOwned(teacherUid, courseId);
        // 按外键依赖序物理删除：先子表后父表（fk_cp_course/fk_ap_* 均为 RESTRICT 兜底）
        enrollmentMapper.delete(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId));
        List<Long> assignmentIds = assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                        .eq(Assignment::getCourseId, courseId))
                .stream().map(Assignment::getId).toList();
        if (!assignmentIds.isEmpty()) {
            assignmentProblemMapper.delete(new LambdaQueryWrapper<AssignmentProblem>()
                    .in(AssignmentProblem::getAssignmentId, assignmentIds));
        }
        assignmentMapper.delete(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getCourseId, courseId));
        courseProblemMapper.delete(new LambdaQueryWrapper<CourseProblem>()
                .eq(CourseProblem::getCourseId, courseId));
        courseMapper.deleteById(courseId);
    }
}
