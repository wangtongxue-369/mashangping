package com.mashangping.judging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.assignment.AssignmentStatus;
import com.mashangping.assignment.StudentAssignmentService;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.judging.dto.SubmissionCreateRequest;
import com.mashangping.judging.dto.SubmissionCreatedView;
import com.mashangping.problem.Languages;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCase;
import com.mashangping.problem.TestCaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交受理九道门（规格 §4）：任一门失败即业务码拒绝且零落行。
 * 目标分流：作业路径挂双锚计分并参与状态门/迟交判定；练习路径仅公开题不计分。
 */
@Service
@RequiredArgsConstructor
public class SubmissionService {

    private final StudentAssignmentService studentAssignmentService;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final SubmissionMapper submissionMapper;
    private final JudgeTaskMapper judgeTaskMapper;
    private final ObjectProvider<JudgeDispatcher> dispatcherProvider;
    private final JudgeProperties properties;

    @Transactional
    public SubmissionCreatedView submit(long uid, SubmissionCreateRequest req) {
        // ② 目标分流 + 双锚归属校验（①鉴权已在控制器 hasRole('STUDENT') 完成）
        Target target = resolveTarget(uid, req);

        // ③ 语言白名单交集
        JudgeLanguage lang = JudgeLanguage.of(req.language());
        Problem p = problemMapper.selectById(target.problemId());
        if (!Languages.parse(p.getAllowedLanguages()).contains(lang.name())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "该题目不允许使用此语言提交");
        }

        // ④ 限流：同用户同目标 rateLimitSeconds 内只收一笔
        assertRateLimitedNotHit(uid, target);

        // ⑤ 源码字节上限
        if (req.code().getBytes(StandardCharsets.UTF_8).length > properties.getCodeMaxBytes()) {
            throw new BizException(ErrorCode.CODE_TOO_LARGE);
        }

        // 测试点计数先行（total_count 口径 = 该题测试点总数，CE/SYSTEM_ERROR 也非零）
        Long total = testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, p.getId()));

        // ⑥ 两行同事务落库
        LocalDateTime now = LocalDateTime.now();
        Submission s = new Submission();
        s.setProblemId(p.getId());
        s.setUserId(uid);
        s.setAssignmentId(target.assignmentId());
        s.setAssignmentProblemId(target.assignmentProblemId());
        s.setLanguage(lang.name());
        s.setCode(req.code());
        s.setStatus(Submission.STATUS_PENDING);
        s.setScore(null);
        s.setPassedCount(0);
        s.setTotalCount(total == null ? 0 : total.intValue());
        s.setIsLate(target.late());
        s.setSubmittedAt(now);
        submissionMapper.insert(s);

        JudgeTask task = new JudgeTask();
        task.setSubmissionId(s.getId());
        task.setStatus(JudgeTask.STATUS_PENDING);
        task.setRetryCount(0);
        judgeTaskMapper.insert(task);

        // ⑦ 事务外唤醒（此处仍在事务内但 dispatchAsync 是异步线程读取，
        //    调度器领取受 status='PENDING' 原子 UPDATE 保护；最坏情况本轮抢不到由定时器兜底）
        JudgeDispatcher dispatcher = dispatcherProvider.getIfAvailable();
        if (dispatcher != null) {
            dispatcher.dispatchAsync();
        }

        // ⑧ 受理回执
        return new SubmissionCreatedView(s.getId(), s.getStatus());
    }

    /** 解析目标：返回 problemId/双锚/是否迟交；所有归属与可见性业务码在此决出 */
    private Target resolveTarget(long uid, SubmissionCreateRequest req) {
        boolean hasAp = req.assignmentProblemId() != null;
        boolean hasP = req.problemId() != null;
        if (hasAp == hasP) {   // 双传或皆缺
            throw new BizException(ErrorCode.PARAM_INVALID,
                    "assignmentProblemId 与 problemId 必须二选一");
        }
        if (hasP) {
            Problem prob = problemMapper.selectById(req.problemId());
            // 私有题或不存在一律 40400：对学生隐藏一切私有题存在性
            if (prob == null || !Boolean.TRUE.equals(prob.getIsPublic())) {
                throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
            }
            return new Target(prob.getId(), null, null, false);
        }
        // ---- 作业路径 ----
        AssignmentProblem ap = assignmentProblemMapper.selectById(req.assignmentProblemId());
        if (ap == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        Assignment a = assignmentMapper.selectById(ap.getAssignmentId());
        if (a == null || !Boolean.TRUE.equals(a.getIsPublished())) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        // 有效选课门（40400 隐藏课程存在性）
        studentAssignmentService.assertEnrolled(uid, a.getCourseId());

        AssignmentStatus st = AssignmentStatus.of(LocalDateTime.now(),
                a.getStartAt(), a.getDueAt(), a.getLateDays());
        switch (st) {
            case NOT_STARTED -> throw new BizException(ErrorCode.FORBIDDEN, "作业尚未开始");
            case CLOSED -> throw new BizException(ErrorCode.FORBIDDEN, "作业已截止，无法提交");
            case IN_PROGRESS -> { /* 放行 is_late=0 */ }
            case LATE_WINDOW -> { /* 放行 is_late=1 */ }
        }
        return new Target(ap.getProblemId(), a.getId(), ap.getId(), st == AssignmentStatus.LATE_WINDOW);
    }

    private void assertRateLimitedNotHit(long uid, Target target) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, uid)
                .orderByDesc(Submission::getSubmittedAt)
                .last("LIMIT 1");
        if (target.assignmentProblemId() != null) {
            wrapper.eq(Submission::getAssignmentProblemId, target.assignmentProblemId());
        } else {
            wrapper.eq(Submission::getProblemId, target.problemId())
                    .isNull(Submission::getAssignmentProblemId);
        }
        List<Submission> recent = submissionMapper.selectList(wrapper);
        if (!recent.isEmpty()) {
            LocalDateTime last = recent.get(0).getSubmittedAt();   // Java 17：无 List.getFirst()
            if (last.isAfter(LocalDateTime.now().minusSeconds(properties.getRateLimitSeconds()))) {
                throw new BizException(ErrorCode.SUBMISSION_TOO_FREQUENT);
            }
        }
    }

    private record Target(long problemId, Long assignmentId,
                          Long assignmentProblemId, boolean late) {
    }
}
