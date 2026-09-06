package com.mashangping.gradebook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.assignment.AssignmentService;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
import com.mashangping.gradebook.dto.GradebookView;
import com.mashangping.gradebook.dto.GradebookView.Cell;
import com.mashangping.gradebook.dto.GradebookView.ProblemRow;
import com.mashangping.gradebook.dto.GradebookView.StudentRow;
import com.mashangping.gradebook.dto.GradebookView.Total;
import com.mashangping.gradebook.dto.TeacherSubmissionRow;
import com.mashangping.judging.JudgeDetail;
import com.mashangping.judging.JudgeDetailMapper;
import com.mashangping.judging.Submission;
import com.mashangping.judging.SubmissionMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCase;
import com.mashangping.problem.TestCaseMapper;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GradebookService {

    private final AssignmentService assignmentService;
    private final EnrollmentMapper enrollmentMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final GradebookMapper gradebookMapper;
    private final JudgeDetailMapper judgeDetailMapper;
    private final TestCaseMapper testCaseMapper;
    private final UserMapper userMapper;

    /** 成绩册矩阵：归属校验 + 学生/题目/格/总分一次组装，不循环查库 */
    public GradebookView gradebook(long teacherUid, long assignmentId) {
        Assignment a = assignmentService.getOwned(teacherUid, assignmentId);

        List<Enrollment> enrollments = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, a.getCourseId())
                .eq(Enrollment::getStatus, Enrollment.STATUS_ACTIVE)
                .isNotNull(Enrollment::getStudentId)
                .orderByAsc(Enrollment::getId));
        Map<Long, User> accounts = accountsOf(enrollments);
        List<StudentRow> students = enrollments.stream()
                .map(e -> {
                    User u = e.getStudentId() == null ? null : accounts.get(e.getStudentId());
                    return new StudentRow(e.getStudentId(), e.getStudentNo(),
                            u != null ? u.getRealName() : e.getStudentName());
                })
                .toList();

        List<AssignmentProblem> aps = assignmentProblemMapper.selectList(
                new LambdaQueryWrapper<AssignmentProblem>()
                        .eq(AssignmentProblem::getAssignmentId, assignmentId)
                        .orderByAsc(AssignmentProblem::getSortOrder));
        Map<Long, Problem> problems = aps.isEmpty() ? Map.of()
                : problemMapper.selectBatchIds(aps.stream().map(AssignmentProblem::getProblemId).toList())
                        .stream().collect(Collectors.toMap(Problem::getId, Function.identity()));
        List<ProblemRow> problemRows = aps.stream()
                .map(ap -> {
                    Problem p = problems.get(ap.getProblemId());
                    return new ProblemRow(ap.getId(), ap.getProblemId(),
                            p != null ? p.getTitle() : "",
                            ap.getScore(), ap.getSortOrder());
                })
                .toList();

        List<Cell> cells = gradebookMapper.selectBestScores(assignmentId).stream()
                .map(m -> new Cell(num(m.get("studentId")), num(m.get("assignmentProblemId")),
                        Math.toIntExact(num(m.get("bestScore")))))
                .toList();

        List<Total> totals = gradebookMapper.selectTotals(assignmentId).stream()
                .map(m -> new Total(num(m.get("studentId")),
                        Math.toIntExact(num(m.get("total")))))
                .toList();

        return new GradebookView(a.getId(), a.getTitle(), students, problemRows, cells, totals);
    }

    /** 教师全局提交历史（分页）：归属校验 + 可选题目/学生筛选 */
    public Page<TeacherSubmissionRow> submissions(long teacherUid, long assignmentId, int page, int size,
                                                  Long assignmentProblemId, Long studentId) {
        Assignment a = assignmentService.getOwned(teacherUid, assignmentId);
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentId, assignmentId)
                .eq(assignmentProblemId != null, Submission::getAssignmentProblemId, assignmentProblemId)
                .eq(studentId != null, Submission::getUserId, studentId)
                .orderByDesc(Submission::getId);
        Page<Submission> result = submissionMapper.selectPage(new Page<>(page, size), wrapper);
        List<Submission> rows = result.getRecords();
        Map<Long, User> users = usersOf(rows);
        Map<Long, Problem> problems = problemsOf(rows);
        Page<TeacherSubmissionRow> views = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        views.setRecords(rows.stream().map(s -> {
            User u = users.get(s.getUserId());
            Problem p = problems.get(s.getProblemId());
            return new TeacherSubmissionRow(s.getId(),
                    u != null ? u.getStudentNo() : null,
                    u != null ? u.getRealName() : null,
                    p != null ? p.getTitle() : null,
                    s.getLanguage(), s.getStatus(), s.getScore(),
                    s.getPassedCount(), s.getTotalCount(),
                    s.getTimeUsedMs(), s.getMemoryUsedMb(),
                    s.getSubmittedAt(), Boolean.TRUE.equals(s.getIsLate()));
        }).collect(Collectors.toList()));
        return views;
    }

    /** 教师提交详情：样例点七字段全量、隐藏点瘦身（message 零泄漏） */
    public TeacherSubmissionRow.Detail submissionDetail(long teacherUid, long assignmentId,
                                                        long submissionId) {
        assignmentService.getOwned(teacherUid, assignmentId);
        Submission s = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getId, submissionId)
                .eq(Submission::getAssignmentId, assignmentId));
        if (s == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提交不存在");
        }
        List<JudgeDetail> details = judgeDetailMapper.selectList(new LambdaQueryWrapper<JudgeDetail>()
                .eq(JudgeDetail::getSubmissionId, submissionId)
                .orderByAsc(JudgeDetail::getPointIndex));
        List<Long> caseIds = details.stream().map(JudgeDetail::getTestCaseId).toList();
        Map<Long, TestCase> caseById = caseIds.isEmpty() ? Map.of()
                : testCaseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(TestCase::getId, Function.identity()));
        List<TeacherSubmissionRow.SamplePoint> samples = details.stream()
                .filter(d -> caseById.containsKey(d.getTestCaseId())
                        && Boolean.TRUE.equals(caseById.get(d.getTestCaseId()).getIsSample()))
                .map(d -> {
                    TestCase tc = caseById.get(d.getTestCaseId());
                    return new TeacherSubmissionRow.SamplePoint(
                            d.getPointIndex(), d.getStatus(),
                            d.getTimeUsedMs(), d.getMemoryUsedMb(),
                            tc != null ? tc.getInput() : null,
                            tc != null ? tc.getExpectedOutput() : null,
                            d.getMessage(), d.getActualOutput());
                })
                .toList();
        // 教师属主完整诊断：隐藏点同样下发输入/预期/错误信息/实际输出（零泄漏仅约束学生端）
        List<TeacherSubmissionRow.MaskedPoint> masked = details.stream()
                .filter(d -> !caseById.containsKey(d.getTestCaseId())
                        || !Boolean.TRUE.equals(caseById.get(d.getTestCaseId()).getIsSample()))
                .map(d -> {
                    TestCase tc = caseById.get(d.getTestCaseId());
                    return new TeacherSubmissionRow.MaskedPoint(
                            d.getPointIndex(), d.getStatus(),
                            d.getTimeUsedMs(), d.getMemoryUsedMb(),
                            tc != null ? tc.getInput() : null,
                            tc != null ? tc.getExpectedOutput() : null,
                            d.getMessage(), d.getActualOutput());
                })
                .toList();
        return new TeacherSubmissionRow.Detail(s.getId(), s.getCode(), s.getLanguage(),
                s.getStatus(), samples, masked);
    }

    /** CSV 导出：UTF-8 BOM + 未做=0 + RFC4180 转义 + 公式注入前缀防御 */
    public byte[] csv(long teacherUid, long assignmentId) {
        GradebookView view = gradebook(teacherUid, assignmentId);
        Map<Long, Map<Long, Integer>> best = new HashMap<>();
        for (Cell c : view.cells()) {
            best.computeIfAbsent(c.studentId(), k -> new HashMap<>())
                    .put(c.assignmentProblemId(), c.bestScore());
        }
        Map<Long, Integer> totals = new HashMap<>();
        for (Total t : view.totals()) {
            totals.put(t.studentId(), t.total());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("学号,姓名");
        for (ProblemRow p : view.problems()) {
            sb.append(',').append(escape(p.title() + "(" + p.score() + ")"));
        }
        sb.append(",总分\n");
        for (StudentRow stu : view.students()) {
            sb.append(escape(stu.studentNo())).append(',').append(escape(stu.realName()));
            Map<Long, Integer> cells = best.getOrDefault(stu.studentId(), Map.of());
            for (ProblemRow p : view.problems()) {
                sb.append(',').append(cells.getOrDefault(p.assignmentProblemId(), 0));
            }
            sb.append(',').append(totals.getOrDefault(stu.studentId(), 0)).append('\n');
        }
        // BOM 用转义写法，避免不可见字面量被编辑器清理；字节输出不变。
        return ("\uFEFF" + sb).getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String field) {
        if (field == null) {
            return "";
        }
        String s = field;
        if (!s.isEmpty()) {
            char c = s.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@') {
                s = "'" + s;
            }
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private Map<Long, User> accountsOf(List<Enrollment> enrollments) {
        List<Long> uids = enrollments.stream().map(Enrollment::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        if (uids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(uids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, User> usersOf(List<Submission> rows) {
        List<Long> uids = rows.stream().map(Submission::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (uids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(uids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private Map<Long, Problem> problemsOf(List<Submission> rows) {
        List<Long> ids = rows.stream().map(Submission::getProblemId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return problemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity()));
    }

    private long num(Object o) {
        return ((Number) o).longValue();
    }
}