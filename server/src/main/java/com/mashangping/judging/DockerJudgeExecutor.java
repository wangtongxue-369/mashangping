package com.mashangping.judging;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 一次性沙箱执行器：容器参数五连+禁swap；TLE/MLE 按 timeout/内核信号约定映射 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "msp.judge", name = "enabled", havingValue = "true")
public class DockerJudgeExecutor implements JudgeExecutor {

    private final JudgeProperties properties;
    private final DockerClient client;

    /** 生产装配入口：Spring 自动从 properties 构造（无参+多构造器歧义，须显式 @Autowired 指明） */
    @Autowired
    public DockerJudgeExecutor(JudgeProperties properties) {
        this(properties, defaultClient());
    }

    /** 测试注入共享客户端（Testcontainers 已配置好 DOCKER_HOST 探测） */
    public DockerJudgeExecutor(JudgeProperties properties, DockerClient shared) {
        this.properties = properties;
        this.client = shared;
    }

    private static DockerClient defaultClient() {
        DefaultDockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        DockerHttpClient http = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .maxConnections(50)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(300))
                .build();
        return DockerClientImpl.getInstance(config, http);
    }

    @Override
    public boolean isHealthy() {
        try {
            client.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.warn("docker daemon unreachable, judge degrade to queueing");
            return false;
        }
    }

    // === Part B: execute 主流程 ===

    @Override
    public void execute(JudgeWork work, JudgeEventSink sink) {
        String image = properties.imageFor(work.language());
        String cid = null;
        try {
            long memBytes = (long) (work.memoryLimitMb()
                    + properties.memOverheadMb(work.language())) * 1024 * 1024;
            Map<String, String> tmpfs = new HashMap<>();
            // 观察事实：--tmpfs 默认 noexec 且目录 root:root（镜像内 chown 被挂载遮蔽），
            // 不加 exec 则 ./main Permission denied(exit126)、不加 uid/gid 则源码写不进 /work；
            // uid/gid 与下方 withUser 的沙箱常量保持一致
            tmpfs.put("/work", "rw,nosuid,exec,uid=2000,gid=2000,"
                    + "size=" + properties.getTmpfsMb() + "m");
            tmpfs.put("/tmp", "rw,nosuid,exec,size=" + properties.getTmpfsMb() + "m");

            CreateContainerResponse created = client.createContainerCmd(image)
                    .withWorkingDir("/work")
                    // 基础镜像默认 CMD 是交互式 shell，start 后即退出导致 exec 无处附着；
                    // 常驻 sleep 作为 init 进程，判题全程由多次 exec 驱动
                    .withCmd("sleep", "infinity")
                    .withUser("2000:2000")
                    .withHostConfig(com.github.dockerjava.api.model.HostConfig.newHostConfig()
                            .withMemory(memBytes)
                            .withMemorySwap(memBytes)
                            .withPidsLimit(64L)
                            .withNetworkMode("none")
                            .withReadonlyRootfs(true)
                            .withTmpFs(tmpfs))
                    .exec();
            cid = created.getId();
            client.startContainerCmd(cid).exec();

            copySource(cid, work.language(), work.code());

            ExecResult compile = execRun(cid, work.language().compileCommand(),
                    null, Duration.ofMillis(properties.getCompileTimeoutMs()));
            if (compile.timedOut()) {
                sink.compileError("编译超时");
                return;
            }
            if (compile.exitCode() == null || compile.exitCode() != 0) {
                String msg = compile.stderr().isBlank()
                        ? "编译失败" : tailLines(compile.stderr(), 2000);
                sink.compileError(msg);
                return;
            }

            java.util.concurrent.atomic.AtomicInteger sampledPeakMb =
                    new java.util.concurrent.atomic.AtomicInteger(0);
            long effectiveMs = (long) work.timeLimitMs() * work.language().timeMultiplier()
                    + properties.getTimeSlackMs();
            for (JudgeWork.WorkCase wc : work.cases()) {
                sink.point(judgePoint(cid, work.language(), wc, effectiveMs, sampledPeakMb));
            }
        } catch (RuntimeException rt) {
            throw new InfraBrokenException("sandbox pipeline broken: " + rt.getMessage(), rt);
        } finally {
            destroyQuietly(cid);
        }
    }

    // === Part C: judgePoint / copySource ===

    private PointOutcome judgePoint(String cid, JudgeLanguage lang,
                                    JudgeWork.WorkCase wc, long effectiveMs,
                                    java.util.concurrent.atomic.AtomicInteger sampledPeakMb) {
        String[] runCmd = wrapTimeout(lang.runCommand(), effectiveMs);
        ExecResult r = execRun(cid, runCmd, wc.input(), Duration.ofMillis(effectiveMs));
        Integer t = r.timedOut() ? null : (int) Math.min(r.elapsedMs(), Integer.MAX_VALUE);
        if (r.timedOut()) {
            return new PointOutcome(wc.pointIndex(), wc.testCaseId(),
                    Submission.STATUS_TLE, null, null, null, null);
        }
        Integer exit = r.exitCode();
        if (exit != null && exit == 124) {                      // timeout 杀掉=超时
            return new PointOutcome(wc.pointIndex(), wc.testCaseId(),
                    Submission.STATUS_TLE, t, null, null, null);
        }
        if (exit != null && exit == 137) {                      // cgroup OOM 击杀
            return new PointOutcome(wc.pointIndex(), wc.testCaseId(),
                    Submission.STATUS_MLE, t, null, null, null);
        }
        if (exit == null || exit != 0) {
            return new PointOutcome(wc.pointIndex(), wc.testCaseId(),
                    Submission.STATUS_RE, t, null,
                    r.stderr() == null ? "" : tailLines(r.stderr(), 2000), null);
        }
        // 规格回显语义（§6）：逐点结束后单发一次 stats 取 cgroup usage 叠加进本次峰值。
        // 比持续流式采样便宜且足够；JAVA 底座基线会抬高读数——显示口径为容器 RSS，教学够用。
        sampleMemory(cid, sampledPeakMb);
        boolean overflow = r.stdoutTruncated();                 // 防刷输出硬上限
        boolean match = !overflow
                && OutputComparator.compare(wc.expectedOutput(), r.stdout());
        // 计划10：WA 时写回该点实际输出尾段（≤2000），供样例点「你的输出」与教师隐藏点诊断；
        // 溢出/AC 不写回（输出不可信或无需诊断）。
        String actual = (match || overflow) ? null : tailLines(r.stdout(), 2000);
        return new PointOutcome(wc.pointIndex(), wc.testCaseId(),
                match ? Submission.STATUS_AC : Submission.STATUS_WA,
                t, sampledPeakMb.get(), null, actual);
    }

    /** 单发 stats 采样（no-stream），叠加维护本提交的内存峰值(MB)；失败静默 */
    private void sampleMemory(String cid, java.util.concurrent.atomic.AtomicInteger peakMb) {
        try {
            final java.util.concurrent.atomic.AtomicReference<
                    com.github.dockerjava.api.model.Statistics> box =
                    new java.util.concurrent.atomic.AtomicReference<>();
            client.statsCmd(cid).withNoStream(true)
                    .exec(new ResultCallback.Adapter<
                            com.github.dockerjava.api.model.Statistics>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Statistics s) {
                            box.set(s);
                        }
                    })
                    .awaitCompletion(2, TimeUnit.SECONDS);
            var st = box.get();
            if (st != null && st.getMemoryStats() != null
                    && st.getMemoryStats().getUsage() != null) {
                int nowMb = (int) Math.min(Integer.MAX_VALUE,
                        st.getMemoryStats().getUsage() / (1024L * 1024L));
                peakMb.accumulateAndGet(nowMb, Math::max);
            }
        } catch (Exception e) {
            log.debug("stats sample skipped: {}", String.valueOf(e));
        }
    }

    /** GNU timeout 包裹：时限到先 TERM 后 0.1s KILL；非法时限属配置错误由启动期发现 */
    static String[] wrapTimeout(String[] cmd, long limitMs) {
        String[] wrapped = new String[cmd.length + 4];
        wrapped[0] = "timeout";
        wrapped[1] = "-k";
        wrapped[2] = "0.1s";
        wrapped[3] = String.format(Locale.ROOT, "%.3fs", limitMs / 1000.0);
        System.arraycopy(cmd, 0, wrapped, 4, cmd.length);
        return wrapped;
    }

    /**
     * 源码注入。两条被证伪的路：①ReadonlyRootfs 下 docker cp(PUT /archive) 遭守护进程整体拒绝
     * （即便落点是可写 tmpfs）；②exec 附带 stdin 在本机 Docker Desktop 双传输(Apache/Zerodep)
     * 均不可用——字节不下发、退出码长期为空、回调迟滞报"管道已结束"。终态实现改为
     * 与编译同级的安全通路：stdin 不挂载的普通 exec，经 base64 解码落盘。
     */
    private void copySource(String cid, JudgeLanguage lang, String code) {
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(code.getBytes(StandardCharsets.UTF_8));
        // 墙钟上限走 msp.judge.compile-timeout-ms：注源与编译同为客户端墙钟上限语义
        ExecResult r = execRun(cid,
                new String[]{"sh", "-c",
                        "printf %s " + encoded + " | base64 -d > " + lang.sourceFileName()},
                null, Duration.ofMillis(properties.getCompileTimeoutMs()));
        if (r.timedOut() || r.exitCode() == null || r.exitCode() != 0) {
            throw new IllegalStateException(
                    "source injection failed, timedOut=" + r.timedOut()
                            + ", exit=" + r.exitCode());
        }
    }

    private void destroyQuietly(String cid) {
        if (cid == null) {
            return;
        }
        try {
            client.removeContainerCmd(cid).withForce(true).withRemoveVolumes(true).exec();
        } catch (Exception e) {
            log.debug("container {} cleanup ignored: {}", cid, String.valueOf(e));
        }
    }

    private static String tailLines(String s, int max) {
        String trimmed = s.stripTrailing();
        return trimmed.length() <= max
                ? trimmed
                : trimmed.substring(trimmed.length() - max);   // stderr 尾段
    }

    // === Part D: execRun 与 FrameCollector ===

    /**
     * 单次 exec：stdin ByteArrayInputStream 直喂；墙钟控场。
     * 完成探测不依赖回调 onComplete——观察事实二（Windows/Docker Desktop 下经注入客户端，
     * exec 实际已退出且 exit=0，而 ResultCallback.awaitCompletion 永不返回，全部用例
     * 干等 60s 超时）：改以 15ms 间隔轮询 inspectExec 的退出码作完成信号，
     * 墙钟耗尽仍未观测到退出码即按超时处置。回调仅承担帧收集职责。
     */
    private ExecResult execRun(String cid, String[] cmd, String input, Duration wall) {
        try {
            String execId = client.execCreateCmd(cid)
                    .withCmd(cmd)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withAttachStdin(input != null)
                    .exec()
                    .getId();
            FrameCollector collector = new FrameCollector(properties.getOutputMaxBytes());
            var start = client.execStartCmd(execId);
            if (input != null) {
                start.withStdIn(new ByteArrayInputStream(
                        input.getBytes(StandardCharsets.UTF_8)));
            }
            long beginNanos = System.nanoTime();
            start.exec(collector);
            Long exit = null;
            final long deadlineNanos = beginNanos + wall.toMillis() * 1_000_000L;
            try {
                while (System.nanoTime() < deadlineNanos) {
                    Thread.sleep(15L);
                    exit = client.inspectExecCmd(execId).exec().getExitCodeLong();
                    if (exit != null) {
                        break;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new InfraBrokenException("judge exec interrupted", ie);
            } finally {
                try {
                    collector.close();
                } catch (Exception e) {
                    log.debug("collector close ignored: {}", String.valueOf(e));
                }
            }
            long elapsedMs = (System.nanoTime() - beginNanos) / 1_000_000L;
            boolean timedOut = exit == null;
            return new ExecResult(collector.stdout(), collector.stderr(),
                    timedOut ? null : exit.intValue(), timedOut, elapsedMs,
                    collector.truncated());
        } catch (RuntimeException rt) {
            throw rt instanceof InfraBrokenException ibe
                    ? ibe
                    : new InfraBrokenException("exec broken: " + rt.getMessage(), rt);
        }
    }

    private record ExecResult(String stdout, String stderr, Integer exitCode,
                              boolean timedOut, long elapsedMs, boolean stdoutTruncated) {
    }

    /** 帧收集器：stdout/stderr 分路；超过上限即停累积并打标（残余帧继续丢弃读尽） */
    private static final class FrameCollector extends ResultCallback.Adapter<Frame> {
        private final int cap;
        private final ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        private volatile boolean truncated;

        FrameCollector(int cap) {
            this.cap = cap;
        }

        @Override
        public void onNext(Frame frame) {
            byte[] payload = frame.getPayload();
            boolean isErr = frame.getStreamType() == StreamType.STDERR;
            ByteArrayOutputStream sink = isErr ? errBuf : outBuf;
            synchronized (sink) {
                if (sink.size() + payload.length <= cap) {
                    sink.write(payload, 0, payload.length);
                } else if (!truncated) {
                    int room = Math.max(0, cap - sink.size());
                    sink.write(payload, 0, room);
                    truncated = true;
                }
            }
        }

        String stdout() {
            synchronized (outBuf) {
                return outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        String stderr() {
            synchronized (errBuf) {
                return errBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            }
        }

        boolean truncated() {
            return truncated;
        }
    }
}
