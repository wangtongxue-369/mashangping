package com.mashangping.judging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCase;
import com.mashangping.problem.TestCaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DB 队列调度器（规格 §7）：单飞行轮询 + 提交唤醒；原子抢占；可重试基础设施异常；
 * 启动复位遗留 RUNNING。bean 由 msp.judge.enabled=true 显式开启——
 * 测试上下文缺该属性时本类不装配，保证零后台线程干扰。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "msp.judge", name = "enabled", havingValue = "true")
public class JudgeScheduler implements JudgeDispatcher, ApplicationRunner {

    private final JudgeTaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;
    private final JudgeDetailMapper detailMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final ObjectProvider<JudgeExecutor> executorProvider;
    private final ObjectProvider<JudgeProgressPublisher> publisherProvider;
    private final JudgeProperties properties;

    private final AtomicBoolean polling = new AtomicBoolean();
    private final Semaphore permits;

    public JudgeScheduler(JudgeTaskMapper taskMapper, SubmissionMapper submissionMapper,
                          JudgeDetailMapper detailMapper, ProblemMapper problemMapper,
                          TestCaseMapper testCaseMapper,
                          AssignmentProblemMapper assignmentProblemMapper,
                          ObjectProvider<JudgeExecutor> executorProvider,
                          ObjectProvider<JudgeProgressPublisher> publisherProvider,
                          JudgeProperties properties) {
        this.taskMapper = taskMapper;
        this.submissionMapper = submissionMapper;
        this.detailMapper = detailMapper;
        this.problemMapper = problemMapper;
        this.testCaseMapper = testCaseMapper;
        this.assignmentProblemMapper = assignmentProblemMapper;
        this.executorProvider = executorProvider;
        this.publisherProvider = publisherProvider;
        this.properties = properties;
        this.permits = new Semaphore(properties.getConcurrent());
    }

    @Override
    public void dispatchAsync() {
        runPollCycle();
    }

    /** 兜底轮询：fixedDelay 节流读属性，配置热改无需重启 */
    @Scheduled(fixedDelayString = "${msp.judge.poll-ms:1000}")
    public void pollTick() {
        runPollCycle();
    }

    @Override
    public void run(ApplicationArguments args) {
        resetStrayRunning();
    }

    /** 崩溃恢复：应用就绪钩子，遗留 RUNNING 全部回 PENDING（task 与 submission 双表同步） */
    public void resetStrayRunning() {
        List<JudgeTask> strays = taskMapper.selectList(new LambdaQueryWrapper<JudgeTask>()
                .eq(JudgeTask::getStatus, JudgeTask.STATUS_RUNNING));
        if (strays.isEmpty()) {
            return;
        }
        for (JudgeTask t : strays) {
            taskMapper.update(null, new LambdaUpdateWrapper<JudgeTask>()
                    .eq(JudgeTask::getId, t.getId())
                    .eq(JudgeTask::getStatus, JudgeTask.STATUS_RUNNING)
                    .set(JudgeTask::getStatus, JudgeTask.STATUS_PENDING));
        }
        List<Long> ids = strays.stream().map(JudgeTask::getSubmissionId).toList();
        submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                .in(Submission::getId, ids)
                .eq(Submission::getStatus, Submission.STATUS_RUNNING)
                .set(Submission::getStatus, Submission.STATUS_PENDING));
        log.warn("judge recovery: reset {} stray RUNNING tasks to PENDING", ids.size());
    }

    /** 投递一轮（去重）；调度线程自身执行 pollOnce，单飞行语义 */
    private void runPollCycle() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            pollOnce();
        } catch (Throwable t) {
            log.error("judge poll cycle failed", t);
        } finally {
            polling.set(false);
        }
    }

    private void pollOnce() {
        JudgeExecutor executor = executorProvider.getIfAvailable();
        // 引擎缺席或探活失败：整轮让出，任务原地保留（降级排队）
        if (executor == null || !executor.isHealthy()) {
            return;
        }
        while (true) {
            if (!permits.tryAcquire()) {
                return;   // 并发满
            }
            boolean claimed = false;
            try {
                List<JudgeTask> candidates = taskMapper.selectList(
                        new LambdaQueryWrapper<JudgeTask>()
                                .eq(JudgeTask::getStatus, JudgeTask.STATUS_PENDING)
                                .orderByAsc(JudgeTask::getId)
                                .last("LIMIT " + properties.getConcurrent()));
                for (JudgeTask candidate : candidates) {
                    // 原子抢占：仅 PENDING 可标 RUNNING，抢到即跑、抢不到换下一个候选
                    if (taskMapper.claimById(candidate.getId()) == 1) {
                        claimed = true;
                        startWorker(executor, candidate);   // 许可由 worker 完成时归还
                        break;
                    }
                }
                if (!claimed) {
                    return;   // 无任务可领
                }
            } finally {
                if (!claimed) {
                    permits.release();   // 未领到任务的许可当场归还
                }
            }
        }
    }

    private void startWorker(JudgeExecutor executor, JudgeTask task) {
        Thread w = new Thread(() -> {
            try {
                process(executor, task);
            } catch (Throwable fatal) {
                log.error("judge worker crashed on task {}", task.getId(), fatal);
            } finally {
                permits.release();
            }
        }, "judge-worker-" + task.getId());
        w.setDaemon(true);
        w.start();
    }

    /** worker 主路径：RUNNING 化 → 执行（sink 同联收发）→ 终态写回 / 异常分类 */
    private void process(JudgeExecutor executor, JudgeTask task) {
        Submission s = submissionMapper.selectById(task.getSubmissionId());
        JudgeProgressPublisher publisher = publisherProvider.getIfAvailable();
        if (s == null) {
            permanentFail(publisher, task, null);
            return;
        }
        submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                .eq(Submission::getId, s.getId())
                .eq(Submission::getStatus, Submission.STATUS_PENDING)
                .set(Submission::getStatus, Submission.STATUS_RUNNING));

        try {
            Problem p = problemMapper.selectById(s.getProblemId());
            if (p == null) {
                throw new InfraBrokenException("problem vanished: " + s.getProblemId(), null);
            }
            List<TestCase> cases = testCaseMapper.selectList(new LambdaQueryWrapper<TestCase>()
                    .eq(TestCase::getProblemId, p.getId())
                    .orderByAsc(TestCase::getId));
            JudgeLanguage lang = JudgeLanguage.of(s.getLanguage());
            // pointIndex 显式编号（test_case.id 升序，1-based）
            List<JudgeWork.WorkCase> workCases = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                TestCase tc = cases.get(i);
                workCases.add(new JudgeWork.WorkCase(tc.getId(), i + 1,
                        tc.getInput(), tc.getExpectedOutput()));
            }
            JudgeWork work = new JudgeWork(s.getId(), s.getUserId(), p.getId(), lang,
                    s.getCode(), p.getTimeLimitMs(), p.getMemoryLimitMb(), workCases);

            List<PointOutcome> collected =
                    Collections.synchronizedList(new ArrayList<>());
            String[] compileErrorBox = new String[1];

            notifyQuietly(() -> {
                if (publisher != null) {
                    publisher.judging(s.getUserId(), s.getId());
                }
            });

            executor.execute(work, new JudgeEventSink() {
                @Override
                public void judging() {
                    // 开判通知已在进入执行器前发出；此处为执行器自发事件，保持幂等
                }

                @Override
                public void point(PointOutcome o) {
                    collected.add(o);
                    notifyQuietly(() -> {
                        if (publisher != null) {
                            publisher.point(s.getUserId(), s.getId(),
                                    o.pointIndex(), o.status());
                        }
                    });
                }

                @Override
                public void compileError(String msg) {
                    compileErrorBox[0] = msg;
                    notifyQuietly(() -> {
                        if (publisher != null) {
                            publisher.compileError(s.getUserId(), s.getId(), msg);
                        }
                    });
                }
            });

            persistTerminal(publisher, s, compileErrorBox[0], collected);
        } catch (Throwable t) {
            handleFailure(publisher, task, s, t);
        }
    }

    /** 终态写回：明细行/聚合状态/计分/任务 DONE，一次交互完成 */
    private void persistTerminal(JudgeProgressPublisher publisher, Submission s,
                                 String compileError, List<PointOutcome> points) {
        int total = s.getTotalCount() == null ? 0 : s.getTotalCount();
        int passed = (int) points.stream()
                .filter(o -> Submission.STATUS_AC.equals(o.status())).count();

        boolean fullAc = compileError == null && !points.isEmpty()
                && points.stream().allMatch(o -> Submission.STATUS_AC.equals(o.status()));
        Integer score;
        if (!fullAc || s.getAssignmentProblemId() == null) {
            score = s.getAssignmentProblemId() != null ? 0 : null;   // 有锚非满分=0；练习恒 NULL
        } else {
            AssignmentProblem ap = assignmentProblemMapper.selectById(s.getAssignmentProblemId());
            score = ap != null ? ap.getScore() : 0;   // 锚被并发移除的兜底：按 0 分保守处理
        }
        String status = compileError != null ? Submission.STATUS_CE : aggregateOf(points);

        Integer timeMax = points.stream().map(PointOutcome::timeUsedMs)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(null);
        Integer memMax = points.stream().map(PointOutcome::memoryUsedMb)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(null);

        for (PointOutcome o : points) {
            JudgeDetail d = new JudgeDetail();
            d.setSubmissionId(s.getId());
            d.setTestCaseId(o.testCaseId());
            d.setPointIndex(o.pointIndex());
            d.setStatus(o.status());
            d.setTimeUsedMs(o.timeUsedMs());
            d.setMemoryUsedMb(o.memoryUsedMb());
            d.setMessage(truncate(o.message(), 2000));
            detailMapper.insert(d);
        }
        submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                .eq(Submission::getId, s.getId())
                .set(Submission::getStatus, status)
                .set(Submission::getScore, score)
                .set(Submission::getPassedCount, passed)
                .set(Submission::getTimeUsedMs, timeMax)
                .set(Submission::getMemoryUsedMb, memMax));
        taskMapper.update(null, new LambdaUpdateWrapper<JudgeTask>()
                .eq(JudgeTask::getSubmissionId, s.getId())
                .eq(JudgeTask::getStatus, JudgeTask.STATUS_RUNNING)
                .set(JudgeTask::getStatus, JudgeTask.STATUS_DONE));

        final String st = status;
        final Integer sc = score;
        final int ps = passed;
        notifyQuietly(() -> {
            if (publisher != null) {
                publisher.finished(s.getUserId(), s.getId(), st, sc, ps, total);
            }
        });
    }

    /**
     * 聚合状态规则（计划文档既定）：严重度 AC(0) &lt; WA(1) &lt; MLE(2) &lt; TLE(3) &lt; RE(4)，
     * 取已执行点中最高严重度。points 为空且非 CE 视为异常（抛 InfraBroken 进入重试）。
     */
    static String aggregateOf(List<PointOutcome> points) {
        if (points.isEmpty()) {
            throw new InfraBrokenException("no point executed and no compile error", null);
        }
        String worst = Submission.STATUS_AC;
        for (PointOutcome o : points) {
            worst = worse(worst, o.status());
        }
        return worst;
    }

    private static int severity(String status) {
        return switch (status) {
            case Submission.STATUS_AC -> 0;
            case Submission.STATUS_WA -> 1;
            case Submission.STATUS_MLE -> 2;
            case Submission.STATUS_TLE -> 3;
            default -> 4;   // RE 与未知状态一律最重档
        };
    }

    private static String worse(String a, String b) {
        return severity(a) >= severity(b) ? a : b;
    }

    private void handleFailure(JudgeProgressPublisher publisher, JudgeTask task,
                               Submission s, Throwable t) {
        int attempted = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (attempted < properties.getRetryMax()) {
            taskMapper.requeueForRetry(task.getId());
            if (s != null) {
                submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                        .eq(Submission::getId, s.getId())
                        .eq(Submission::getStatus, Submission.STATUS_RUNNING)
                        .set(Submission::getStatus, Submission.STATUS_PENDING));
            }
            log.warn("judge task {} requeued ({}/{}): {}", task.getId(),
                    attempted + 1, properties.getRetryMax(), String.valueOf(t));
            return;
        }
        permanentFail(publisher, task, s);
    }

    /** 终局失败：task FAILED + submission SYSTEM_ERROR（不计成绩不锁提交），尽力推送 FINISHED */
    private void permanentFail(JudgeProgressPublisher publisher, JudgeTask task, Submission s) {
        taskMapper.update(null, new LambdaUpdateWrapper<JudgeTask>()
                .eq(JudgeTask::getId, task.getId())
                .in(JudgeTask::getStatus, JudgeTask.STATUS_RUNNING, JudgeTask.STATUS_PENDING)
                .set(JudgeTask::getStatus, JudgeTask.STATUS_FAILED));
        long uid = s != null ? s.getUserId() : -1;
        long sid = s != null ? s.getId() : task.getSubmissionId();
        int total = s != null && s.getTotalCount() != null ? s.getTotalCount() : 0;
        if (s != null) {
            submissionMapper.update(null, new LambdaUpdateWrapper<Submission>()
                    .eq(Submission::getId, s.getId())
                    .set(Submission::getStatus, Submission.STATUS_SYSTEM_ERROR)
                    .set(Submission::getScore, s.getAssignmentProblemId() != null ? 0 : null));
        }
        log.error("judge task {} permanent FAIL after retries", task.getId());
        final long u = uid;
        final long sd = sid;
        final int tt = total;
        if (u > 0) {
            notifyQuietly(() -> {
                if (publisher != null) {
                    publisher.finished(u, sd, Submission.STATUS_SYSTEM_ERROR, null, 0, tt);
                }
            });
        }
    }

    private void notifyQuietly(Runnable r) {
        try {
            r.run();
        } catch (Throwable notifyFailure) {
            log.warn("judge progress notify failed (ignored)", notifyFailure);
        }
    }

    private static String truncate(String raw, int max) {
        if (raw == null || raw.length() <= max) {
            return raw;
        }
        return raw.substring(raw.length() - max);   // stderr 取尾段
    }
}
