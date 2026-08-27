package com.mashangping.judging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.assignment.StudentAssignmentService;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCase;
import com.mashangping.problem.TestCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学生读侧：严格 owner 维度查询（user_id 必须等于本人）；
 * 详情明细按测试点 is_sample 分流为样例六字段 / 隐藏四字段两套 record，
 * 隐藏内容在视图构造层即缺席（不是序列化时打补丁），结构性零泄漏。
 */
@Service
@RequiredArgsConstructor
public class StudentReadService {

    private final SubmissionMapper submissionMapper;
    private final JudgeDetailMapper judgeDetailMapper;
    private final TestCaseMapper testCaseMapper;
    private final StudentAssignmentService studentAssignmentService;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final ProblemMapper problemMapper;

    public List<StudentSubmissionViews.Summary> listMine(long uid,
                                                         Long assignmentProblemId, Long problemId) {
        if ((assignmentProblemId != null) == (problemId != null)) {  // 双传或皆缺
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "assignmentProblemId 与 problemId 必须二选一");
        }
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, uid)
                .orderByDesc(Submission::getId);
        if (assignmentProblemId != null) {
            requireOwnedAp(uid, assignmentProblemId);
            wrapper.eq(Submission::getAssignmentProblemId, assignmentProblemId);
        } else {
            Problem p = problemMapper.selectById(problemId);
            if (p == null || !Boolean.TRUE.equals(p.getIsPublic())) {
                throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
            }
            wrapper.eq(Submission::getProblemId, problemId)
                    .isNull(Submission::getAssignmentProblemId);
        }
        return submissionMapper.selectList(wrapper).stream()
                .map(s -> new StudentSubmissionViews.Summary(s.getId(), s.getStatus(),
                        s.getScore(), Boolean.TRUE.equals(s.getIsLate()),
                        s.getLanguage(), s.getSubmittedAt()))
                .toList();
    }

    public StudentSubmissionViews.Detail detail(long uid, long submissionId) {
        Submission s = submissionMapper.selectOne(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getId, submissionId)
                .eq(Submission::getUserId, uid));     // owner 过滤即隐身
        if (s == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "提交不存在");
        }
        List<JudgeDetail> details = judgeDetailMapper.selectList(
                new LambdaQueryWrapper<JudgeDetail>()
                        .eq(JudgeDetail::getSubmissionId, submissionId)
                        .orderByAsc(JudgeDetail::getPointIndex));

        List<Long> caseIds = details.stream().map(JudgeDetail::getTestCaseId).toList();
        // MyBatis-Plus 坑：.in(false,...) 空集合省略 IN 致全表加载——空则短路
        Map<Long, Boolean> sampleFlags = caseIds.isEmpty() ? Map.of()
                : testCaseMapper.selectBatchIds(caseIds).stream()
                        .collect(Collectors.toMap(TestCase::getId,
                                tc -> Boolean.TRUE.equals(tc.getIsSample())));

        List<StudentSubmissionViews.SamplePoint> samples = details.stream()
                .filter(d -> sampleFlags.getOrDefault(d.getTestCaseId(), false))
                .map(d -> {
                    TestCase tc = testCaseMapper.selectById(d.getTestCaseId());
                    return new StudentSubmissionViews.SamplePoint(
                            d.getPointIndex(), d.getStatus(),
                            d.getTimeUsedMs(), d.getMemoryUsedMb(),
                            tc.getInput(), tc.getExpectedOutput(), d.getMessage());
                }).toList();

        List<StudentSubmissionViews.MaskedPoint> masked = details.stream()
                .filter(d -> !sampleFlags.getOrDefault(d.getTestCaseId(), false))
                .map(d -> new StudentSubmissionViews.MaskedPoint(
                        d.getPointIndex(), d.getStatus(),
                        d.getTimeUsedMs(), d.getMemoryUsedMb()))
                .toList();

        return new StudentSubmissionViews.Detail(s.getId(), s.getProblemId(),
                s.getLanguage(), s.getCode(), s.getStatus(), s.getScore(),
                s.getPassedCount(), s.getTotalCount(), s.getTimeUsedMs(),
                s.getMemoryUsedMb(), Boolean.TRUE.equals(s.getIsLate()), s.getSubmittedAt(),
                samples, masked);
    }

    /** 作业锚归属门：未选课者一律 40400 隐藏课程存在性 */
    private void requireOwnedAp(long uid, long assignmentProblemId) {
        AssignmentProblem ap = assignmentProblemMapper.selectById(assignmentProblemId);
        if (ap == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        Assignment a = assignmentMapper.selectById(ap.getAssignmentId());
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        studentAssignmentService.assertEnrolled(uid, a.getCourseId());
    }
}
