package com.mashangping.assignment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
import com.mashangping.problem.Languages;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCase;
import com.mashangping.problem.TestCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentAssignmentService {

    private final EnrollmentMapper enrollmentMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final CourseMapper courseMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;

    /** 学生门：有效选课不存在一律 40400 隐藏课程存在性 */
    public void assertEnrolled(long studentUid, long courseId) {
        Long n = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId)
                .eq(Enrollment::getStudentId, studentUid)
                .eq(Enrollment::getStatus, Enrollment.STATUS_ACTIVE));
        if (n == null || n == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
    }

    /** 学生作业列表：仅已发布，due_at 升序，附题目计数与实时状态 */
    public Page<AssignmentViews.StudentListItem> list(long studentUid, long courseId, int page, int size) {
        assertEnrolled(studentUid, courseId);
        Page<Assignment> result = assignmentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Assignment>()
                        .eq(Assignment::getCourseId, courseId)
                        .eq(Assignment::getIsPublished, true)
                        .orderByAsc(Assignment::getDueAt));
        List<Assignment> rows = result.getRecords();
        List<Long> ids = rows.stream().map(Assignment::getId).toList();
        Map<Long, Long> counts = ids.isEmpty() ? Map.of()
                : assignmentProblemMapper.selectList(new LambdaQueryWrapper<AssignmentProblem>()
                        .in(AssignmentProblem::getAssignmentId, ids))
                        .stream().collect(Collectors.groupingBy(AssignmentProblem::getAssignmentId, Collectors.counting()));
        LocalDateTime now = LocalDateTime.now();
        Page<AssignmentViews.StudentListItem> views =
                new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        views.setRecords(rows.stream().map(a -> new AssignmentViews.StudentListItem(
                        a.getId(), a.getTitle(), a.getStartAt(), a.getDueAt(), a.getLateDays(),
                        counts.getOrDefault(a.getId(), 0L),
                        AssignmentStatus.of(now, a.getStartAt(), a.getDueAt(), a.getLateDays()).name()))
                .collect(Collectors.toList()));
        return views;
    }

    /** 学生作业上下文：深链页头（课程名/作业名/状态/时间/题数）。已发布 + 已选课即可读，不要求已开始。 */
    public AssignmentViews.StudentAssignmentHeader header(long studentUid, long assignmentId) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || !Boolean.TRUE.equals(a.getIsPublished())) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        assertEnrolled(studentUid, a.getCourseId());
        Course course = courseMapper.selectById(a.getCourseId());
        Long count = assignmentProblemMapper.selectCount(new LambdaQueryWrapper<AssignmentProblem>()
                .eq(AssignmentProblem::getAssignmentId, assignmentId));
        LocalDateTime now = LocalDateTime.now();
        return new AssignmentViews.StudentAssignmentHeader(
                a.getCourseId(), course != null ? course.getName() : "", a.getId(), a.getTitle(),
                AssignmentStatus.of(now, a.getStartAt(), a.getDueAt(), a.getLateDays()).name(),
                a.getStartAt(), a.getDueAt(), a.getLateDays(),
                count == null ? 0L : count);
    }

    /** 作业题目列表：三级门 已选课→已发布→已过 start_at；隐藏点零进列表 */
    public List<AssignmentViews.StudentProblemItem> problems(long studentUid, long courseId, long assignmentId) {
        Assignment a = visibleAssignment(studentUid, courseId, assignmentId);
        requireStarted(a);
        List<AssignmentProblem> aps = assignmentProblemMapper.selectList(
                new LambdaQueryWrapper<AssignmentProblem>()
                        .eq(AssignmentProblem::getAssignmentId, assignmentId)
                        .orderByAsc(AssignmentProblem::getSortOrder));
        Map<Long, Problem> problems = aps.isEmpty() ? Map.of()
                : problemMapper.selectBatchIds(aps.stream().map(AssignmentProblem::getProblemId).toList())
                        .stream().collect(Collectors.toMap(Problem::getId, Function.identity()));
        return aps.stream()
                .map(ap -> {
                    Problem p = problems.get(ap.getProblemId());
                    return new AssignmentViews.StudentProblemItem(
                            ap.getId(), ap.getProblemId(), p != null ? p.getTitle() : "",
                            ap.getScore(), ap.getSortOrder(),
                            p != null ? Languages.parse(p.getAllowedLanguages()) : List.of(),
                            p != null ? p.getTimeLimitMs() : 0,
                            p != null ? p.getMemoryLimitMb() : 0);
                })
                .toList();
    }

    /** 题目阅读视图：MD 原文 + 样例句完整输入输出；隐藏点结构性零泄漏（只查 is_sample=true） */
    public AssignmentViews.StudentProblemDetail problemDetail(long studentUid, long courseId,
                                                              long assignmentId, long problemId) {
        Assignment a = visibleAssignment(studentUid, courseId, assignmentId);
        requireStarted(a);
        AssignmentProblem ap = assignmentProblemMapper.selectOne(new LambdaQueryWrapper<AssignmentProblem>()
                .eq(AssignmentProblem::getAssignmentId, assignmentId)
                .eq(AssignmentProblem::getProblemId, problemId));
        if (ap == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不在本作业中");
        }
        Problem p = problemMapper.selectById(problemId);
        if (p == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        List<AssignmentViews.StudentSample> samples = testCaseMapper
                .selectList(new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, problemId)
                        .eq(TestCase::getIsSample, true)
                        .orderByAsc(TestCase::getId))
                .stream()
                .map(tc -> new AssignmentViews.StudentSample(tc.getInput(), tc.getExpectedOutput()))
                .toList();
        return new AssignmentViews.StudentProblemDetail(ap.getId(), p.getId(), p.getTitle(), p.getDescription(),
                Languages.parse(p.getAllowedLanguages()), p.getTimeLimitMs(), p.getMemoryLimitMb(), samples);
    }

    private Assignment visibleAssignment(long studentUid, long courseId, long assignmentId) {
        assertEnrolled(studentUid, courseId);
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null || !Objects.equals(a.getCourseId(), courseId)
                || !Boolean.TRUE.equals(a.getIsPublished())) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        return a;
    }

    private void requireStarted(Assignment a) {
        if (LocalDateTime.now().isBefore(a.getStartAt())) {
            throw new BizException(ErrorCode.FORBIDDEN, "作业尚未开始");
        }
    }
}
