package com.mashangping.judging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCase;
import com.mashangping.problem.TestCaseMapper;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 队列机制套件：手动 new JudgeScheduler（不开 @Scheduled 定时，
 * 全部用例显式调用 dispatchAsync()/pollTick()，时序完全可控且无轮询线程）。
 * msp.judge.enabled 属性缺席 → Spring 容器内本就没有调度器 bean；
 * 替身执行器经 ObjectProvider 匿名实现手动注入构造器。
 */
class JudgeQueueFlowTest extends IntegrationTestBase {

    @Autowired UserMapper userMapper;
    @Autowired ProblemMapper problemMapper;
    @Autowired TestCaseMapper testCaseMapper;
    @Autowired SubmissionMapper submissionMapper;
    @Autowired JudgeTaskMapper judgeTaskMapper;
    @Autowired JudgeDetailMapper judgeDetailMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;

    long uid;
    Problem problem;

    /** 每用例独立替身：可编程健康/行为/并发观测 */
    StubExecutor stub;

    @BeforeEach
    void seed() {
        stub = new StubExecutor();
        User u = new User();
        u.setUsername("stuQ" + System.nanoTime());
        u.setPasswordHash(passwordEncoder.encode("pw123456"));
        u.setRealName("队测");
        u.setRole(User.ROLE_STUDENT);
        u.setEnabled(true);
        userMapper.insert(u);
        uid = u.getId();

        problem = new Problem();
        problem.setTeacherId(9200L);
        problem.setTitle("队题");
        problem.setDescription("d");
        problem.setIsPublic(false);
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitMb(256);
        problemMapper.insert(problem);
        TestCase tc = new TestCase();
        tc.setProblemId(problem.getId());
        tc.setInput("1\n");
        tc.setExpectedOutput("2\n");
        tc.setIsSample(true);
        testCaseMapper.insert(tc);

        // 清干净本测试域的调度状态
        judgeTaskMapper.delete(new LambdaQueryWrapper<>());
        judgeDetailMapper.delete(new LambdaQueryWrapper<>());
        submissionMapper.delete(new LambdaQueryWrapper<>());
    }

    private JudgeScheduler newScheduler(int concurrent) {
        return new JudgeScheduler(judgeTaskMapper, submissionMapper, judgeDetailMapper,
                problemMapper, testCaseMapper, assignmentProblemMapper,
                providerOf(stub), emptyPublisher(), props(concurrent),
                transactionManager);
    }

    private ObjectProvider<JudgeProgressPublisher> emptyPublisher() {
        // ObjectProvider 抽象方法共四个：getObject/getIfAvailable/getIfUnique（stream 有默认实现也一并覆写更直观）
        return new ObjectProvider<>() {
            @Override public JudgeProgressPublisher getIfAvailable() { return null; }
            @Override public JudgeProgressPublisher getIfUnique() { return null; }
            @Override public JudgeProgressPublisher getObject() { return null; }
            @Override public JudgeProgressPublisher getObject(Object... args) { return getObject(); }
            @Override public java.util.stream.Stream<JudgeProgressPublisher> stream() {
                return java.util.stream.Stream.empty();
            }
        };
    }

    private JudgeProperties props(int concurrent) {
        return new JudgeProperties(
                true, concurrent, 1000L, 2, 30000, 500, 1048576, 65536, 10, 64, 64, 192,
                "CC=msp-judge-cc,CPP=msp-judge-cc,JAVA=msp-judge-java,PYTHON=msp-judge-python");
    }

    private Submission seedPendingSubmission() {
        Submission s = new Submission();
        s.setProblemId(problem.getId());
        s.setUserId(uid);
        s.setLanguage("CPP");
        s.setCode("int main(){return 0;}");
        s.setStatus(Submission.STATUS_PENDING);
        s.setPassedCount(0);
        s.setTotalCount(1);
        s.setIsLate(false);
        s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);
        JudgeTask t = new JudgeTask();
        t.setSubmissionId(s.getId());
        t.setStatus(JudgeTask.STATUS_PENDING);
        t.setRetryCount(0);
        judgeTaskMapper.insert(t);
        return s;
    }

    // ---------------- 用例 ----------------

    @Test
    void pending_task_gets_judged_and_terminal_state_written() throws Exception {
        Submission s = seedPendingSubmission();
        stub.behavior = (work, sink) ->
                sink.point(new PointOutcome(1, work.cases().get(0).testCaseId(),
                        "AC", 10, 25, null, null));
        JudgeScheduler scheduler = newScheduler(3);
        scheduler.dispatchAsync();
        awaitTerminal(s.getId(), "AC", 6000);
        assertThat(taskOf(s.getId()).getStatus()).isEqualTo(JudgeTask.STATUS_DONE);
        assertThat(submissionOf(s.getId()).getScore()).isNull();          // 练习路径无分
        List<JudgeDetail> details = judgeDetailMapper.selectList(new LambdaQueryWrapper<JudgeDetail>()
                .eq(JudgeDetail::getSubmissionId, s.getId()));
        assertThat(details).hasSize(1);
        assertThat(details.get(0).getStatus()).isEqualTo("AC");
    }

    @Test
    void wa_point_persists_actual_output_for_diagnosis() throws Exception {
        // 计划10：WA 点把实际输出尾段落库 judge_detail.actual_output（样例点「你的输出」/教师隐藏点诊断）
        Submission s = seedPendingSubmission();
        stub.behavior = (work, sink) ->
                sink.point(new PointOutcome(1, work.cases().get(0).testCaseId(),
                        "WA", 10, 25, null, "我的实际输出\n第二行"));
        JudgeScheduler scheduler = newScheduler(3);
        scheduler.dispatchAsync();
        awaitTerminal(s.getId(), "WA", 6000);
        List<JudgeDetail> details = judgeDetailMapper.selectList(new LambdaQueryWrapper<JudgeDetail>()
                .eq(JudgeDetail::getSubmissionId, s.getId()));
        assertThat(details).hasSize(1);
        assertThat(details.get(0).getStatus()).isEqualTo("WA");
        assertThat(details.get(0).getActualOutput()).isEqualTo("我的实际输出\n第二行");
        assertThat(details.get(0).getMessage()).isNull();
    }

    @Test
    void assignment_anchored_full_ac_scores_assignment_score() throws Exception {
        // 补作业锚并给 ap.score=9
        Course c = new Course();
        c.setName("课Q" + System.nanoTime());
        c.setTerm("2025-2026-1");
        c.setTeacherId(9201L);
        courseMapper.insert(c);
        Assignment a = new Assignment();
        a.setCourseId(c.getId());
        a.setTitle("作Q");
        a.setDescription("d");
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setDueAt(LocalDateTime.now().plusDays(3));
        a.setLateDays(0);
        a.setIsPublished(true);
        assignmentMapper.insert(a);
        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(a.getId());
        ap.setProblemId(problem.getId());
        ap.setScore(9);
        ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);

        Submission s = seedPendingSubmission();
        s.setAssignmentId(a.getId());
        s.setAssignmentProblemId(ap.getId());
        submissionMapper.updateById(s);

        stub.behavior = (work, sink) ->
                sink.point(new PointOutcome(1, work.cases().get(0).testCaseId(), "AC", 10, 25, null, null));
        newScheduler(3).dispatchAsync();
        awaitTerminal(s.getId(), "AC", 6000);
        assertThat(submissionOf(s.getId()).getScore()).isEqualTo(9);
    }

    @Test
    void compile_error_yields_ce_zero_points_and_message_in_submission_status() throws Exception {
        Submission s = seedPendingSubmission();
        stub.behavior = (work, sink) -> sink.compileError("main.c:1: error expected ;");
        newScheduler(3).dispatchAsync();
        awaitTerminal(s.getId(), "CE", 6000);
        List<JudgeDetail> details = judgeDetailMapper.selectList(new LambdaQueryWrapper<JudgeDetail>()
                .eq(JudgeDetail::getSubmissionId, s.getId()));
        assertThat(details).isEmpty();                                    // CE 无明细行
        assertThat(submissionOf(s.getId()).getPassedCount()).isZero();
        assertThat(submissionOf(s.getId()).getTimeUsedMs()).isNull();
    }

    @Test
    void worst_severity_wins_among_mixed_points() throws Exception {
        Submission s = seedPendingSubmission();
        TestCase second = new TestCase();
        second.setProblemId(problem.getId());
        second.setInput("2\n");
        second.setExpectedOutput("3\n");
        second.setIsSample(false);
        testCaseMapper.insert(second);
        submissionMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Submission>()
                        .eq(Submission::getId, s.getId())
                        .set(Submission::getTotalCount, 2));
        stub.behavior = (work, sink) -> {
            for (JudgeWork.WorkCase wc : work.cases()) {
                sink.point(new PointOutcome(wc.pointIndex(), wc.testCaseId(),
                        wc.pointIndex() == 1 ? "AC" : "TLE", 1000, 30, null, null));
            }
        };
        newScheduler(3).dispatchAsync();
        awaitTerminal(s.getId(), "TLE", 6000);
        assertThat(submissionOf(s.getId()).getPassedCount()).isEqualTo(1);
    }

    @Test
    void engine_absent_leaves_tasks_pending() throws Exception {
        Submission s = seedPendingSubmission();
        JudgeScheduler scheduler = new JudgeScheduler(judgeTaskMapper, submissionMapper,
                judgeDetailMapper, problemMapper, testCaseMapper, assignmentProblemMapper,
                emptyExecutor(), emptyPublisher(), props(3),
                transactionManager);
        scheduler.pollTick();
        assertThat(taskOf(s.getId()).getStatus()).isEqualTo(JudgeTask.STATUS_PENDING);
        assertThat(submissionOf(s.getId()).getStatus()).isEqualTo(Submission.STATUS_PENDING);
    }

    @Test
    void unhealthy_engine_degrades_gracefully() throws Exception {
        Submission s = seedPendingSubmission();
        stub.healthy = false;
        newScheduler(3).dispatchAsync();
        Thread.sleep(200);   // 给可能的错误路径留现场时间；无任务应完全无副作用
        assertThat(taskOf(s.getId()).getStatus()).isEqualTo(JudgeTask.STATUS_PENDING);
    }

    @Test
    void infra_failure_retries_then_system_error() throws Exception {
        Submission s = seedPendingSubmission();
        AtomicInteger calls = new AtomicInteger();
        stub.behavior = (work, sink) -> {
            if (calls.incrementAndGet() <= 3) {   // 初次+2次重试全炸
                try {
                    // 让领取循环的尾部 select 落在回队之前：一次 dispatch 恰好一轮尝试，相位确定
                    Thread.sleep(15);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                throw new InfraBrokenException("daemon gone", null);
            }
            sink.point(new PointOutcome(1, work.cases().get(0).testCaseId(),
                    "AC", 5, 20, null, null));
        };
        JudgeScheduler scheduler = newScheduler(3);
        scheduler.dispatchAsync();
        awaitIdle();
        assertThat(taskOf(s.getId()).getStatus()).isEqualTo(JudgeTask.STATUS_PENDING);
        assertThat(taskOf(s.getId()).getRetryCount()).isEqualTo(1);
        scheduler.dispatchAsync();
        awaitIdle();
        assertThat(taskOf(s.getId()).getRetryCount()).isEqualTo(2);
        scheduler.dispatchAsync();
        awaitIdle();   // 第3次失败 → FAILED/SYSTEM_ERROR
        assertThat(taskOf(s.getId()).getStatus()).isEqualTo(JudgeTask.STATUS_FAILED);
        assertThat(submissionOf(s.getId()).getStatus())
                .isEqualTo(Submission.STATUS_SYSTEM_ERROR);
    }

    @Test
    void stray_running_reset_on_startup_runner() throws Exception {
        Submission s = seedPendingSubmission();
        jdbc.update("UPDATE judge_task SET status='RUNNING' WHERE submission_id=?", s.getId());
        jdbc.update("UPDATE submission SET status='RUNNING' WHERE id=?", s.getId());
        newScheduler(3).resetStrayRunning();
        assertThat(taskOf(s.getId()).getStatus()).isEqualTo(JudgeTask.STATUS_PENDING);
        assertThat(submissionOf(s.getId()).getStatus()).isEqualTo(Submission.STATUS_PENDING);
    }

    @Test
    void concurrency_cap_respected_across_parallel_polls() throws Exception {
        for (int i = 0; i < 5; i++) {
            seedPendingSubmission();
        }
        // 预裁决修正①：单飞行护栏下 3 个触发线程只有 1 个真正领取——
        // 等待"同时进入 probe 的 worker 数"达并发上限 3 即可；任务 4/5 保持 PENDING 属预期
        CountDownLatch inFlight = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger peak = new AtomicInteger();
        stub.concurrentProbe = (runningCount) -> {
            int now = runningCount.get();
            peak.accumulateAndGet(now, Math::max);
            inFlight.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };
        JudgeScheduler scheduler = newScheduler(3);
        Thread[] triggers = new Thread[3];
        for (int i = 0; i < 3; i++) {
            triggers[i] = new Thread(scheduler::dispatchAsync);
            triggers[i].start();
        }
        for (Thread t : triggers) {
            t.join(2000);
        }
        assertThat(inFlight.await(8, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        awaitAllDone(10_000);
        assertThat(peak.get()).isLessThanOrEqualTo(3);
    }

    // ---------------- 工具 ----------------

    private ObjectProvider<JudgeExecutor> emptyExecutor() {
        return providerOf(null);
    }

    private ObjectProvider<JudgeExecutor> providerOf(JudgeExecutor e) {
        return new ObjectProvider<>() {
            @Override public JudgeExecutor getIfAvailable() { return e; }
            @Override public JudgeExecutor getIfUnique() { return e; }
            @Override public JudgeExecutor getObject() {
                throw new IllegalStateException("no executor configured");
            }
            @Override public JudgeExecutor getObject(Object... args) { return getObject(); }
            @Override public java.util.stream.Stream<JudgeExecutor> stream() {
                return e == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(e);
            }
        };
    }

    private JudgeTask taskOf(long sid) {
        return judgeTaskMapper.selectOne(new LambdaQueryWrapper<JudgeTask>()
                .eq(JudgeTask::getSubmissionId, sid));
    }

    private Submission submissionOf(long sid) {
        return submissionMapper.selectById(sid);
    }

    /** 纯 sleep 轮询读终态（预裁决修正②：无轮询驱动线程，替身驱动型 worker 异步落终态即可） */
    private void awaitTerminal(long sid, String status, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Submission cur = submissionOf(sid);
            if (cur != null && status.equals(cur.getStatus())) {
                return;
            }
            Thread.sleep(50);
        }
        org.junit.jupiter.api.Assertions.fail(
                "terminal status not reached within " + timeoutMs + "ms");
    }

    /**
     * 等待本用户的任务全部退出 RUNNING（无在飞）。只在 RUNNING 上等：
     * 重试回队后的 PENDING 属于中间态，下一轮 dispatchAsync 再领取。
     * 不在等待环里代为驱动 pollTick——否则会把三次尝试连滚到终态、抹掉中间断言窗口。
     */
    private void awaitIdle() throws Exception {
        long deadline = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < deadline) {
            Integer busy = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM judge_task WHERE status = 'RUNNING' "
                            + "AND submission_id IN (SELECT id FROM submission WHERE user_id=?)",
                    Integer.class, uid);
            if (busy != null && busy == 0) {
                return;
            }
            Thread.sleep(80);
        }
        org.junit.jupiter.api.Assertions.fail("scheduler idle timeout");
    }

    private void awaitAllDone(long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            Integer busy = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM judge_task WHERE status = 'RUNNING'", Integer.class);
            if (busy != null && busy == 0) {
                return;
            }
            Thread.sleep(60);
        }
    }

    @AfterEach
    void cleanupPollFlag() {
        // 显式触发型调度无残留线程；防御性等待一下所有 worker 自然退场
        try {
            awaitAllDone(4000);
        } catch (Exception ignored) {
        }
    }

    // ---------------- 替身 ----------------

    static class StubExecutor implements JudgeExecutor {

        interface Behavior {
            void run(JudgeWork work, JudgeEventSink sink) throws Exception;
        }

        Behavior behavior = (work, sink) ->
                sink.point(new PointOutcome(1, work.cases().get(0).testCaseId(),
                        "AC", 10, 25, null, null));

        volatile boolean healthy = true;
        java.util.function.Consumer<AtomicInteger> concurrentProbe;   // 参数为当前在飞计数

        final AtomicInteger inFlight = new AtomicInteger();

        @Override
        public boolean isHealthy() {
            return healthy;
        }

        @Override
        public void execute(JudgeWork work, JudgeEventSink sink) {
            inFlight.incrementAndGet();
            try {
                if (concurrentProbe != null) {
                    concurrentProbe.accept(inFlight);
                }
                behavior.run(work, sink);
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }
}
