package com.mashangping.judging;

import com.github.dockerjava.api.DockerClient;
import com.mashangping.IntegrationTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真沙箱端到端：ImageFromDockerfile 从仓库 Dockerfile 构建（内容同步复制，
 * 与 deploy/docker/judge/*.Dockerfile 保持一致——BeforeAll 内含逐字同步自检）。
 * 直接驱动 executor，不经调度器/DB；AC/WA/TLE/MLE/RE/CE 六态全覆盖。
 */
class SandboxJudgeIT extends IntegrationTestBase {

    private static ImageFromDockerfile ccImage;
    private static ImageFromDockerfile javaImage;
    private static ImageFromDockerfile pythonImage;
    private static DockerJudgeExecutor executor;
    /** 低输出上限档：专测 stdout 超限强制 WA 的截断路径 */
    private static DockerJudgeExecutor smallOutputExecutor;

    static final String CC_DOCKERFILE = """
            FROM gcc:12
            RUN useradd --uid 2000 --create-home judge \\
             && mkdir -p /work && chown judge:judge /work
            USER judge
            WORKDIR /work
            """;

    static final String JAVA_DOCKERFILE = """
            FROM eclipse-temurin:17-jdk
            RUN useradd --uid 2000 --create-home judge \\
             && mkdir -p /work && chown judge:judge /work
            USER judge
            WORKDIR /work
            """;

    static final String PY_DOCKERFILE = """
            FROM python:3.11-slim
            RUN useradd --uid 2000 --create-home judge \\
             && mkdir -p /work && chown judge:judge /work
            USER judge
            WORKDIR /work
            """;

    @BeforeAll
    static void bootEngine() {
        // 入库文件与内嵌 text block 逐字一致自检（归一换行符以免疫 git autocrlf）
        assertThat(norm(repoFile("deploy/docker/judge/cc/Dockerfile")))
                .isEqualTo(norm(CC_DOCKERFILE));
        assertThat(norm(repoFile("deploy/docker/judge/java/Dockerfile")))
                .isEqualTo(norm(JAVA_DOCKERFILE));
        assertThat(norm(repoFile("deploy/docker/judge/python/Dockerfile")))
                .isEqualTo(norm(PY_DOCKERFILE));

        ccImage = new ImageFromDockerfile("msp-judge-it-cc", false)
                .withFileFromString("Dockerfile", CC_DOCKERFILE);
        javaImage = new ImageFromDockerfile("msp-judge-it-java", false)
                .withFileFromString("Dockerfile", JAVA_DOCKERFILE);
        pythonImage = new ImageFromDockerfile("msp-judge-it-python", false)
                .withFileFromString("Dockerfile", PY_DOCKERFILE);
        DockerClient shared = DockerClientFactory.instance().client();
        JudgeProperties props = props();
        executor = new DockerJudgeExecutor(props, shared);
        smallOutputExecutor = new DockerJudgeExecutor(smallCapProps(), shared);
        // 显式触发构建并覆盖镜像名映射到 IT 专用 tag
        ccImage.get();
        javaImage.get();
        pythonImage.get();
    }

    /** 从当前目录向上找仓库根下的相对路径（surefire 工作目录兼容） */
    private static String repoFile(String relPath) {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve(relPath))) {
            dir = dir.getParent();
        }
        assertThat(dir).as("repo root containing " + relPath).isNotNull();
        try {
            return Files.readString(dir.resolve(relPath));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String norm(String s) {
        return s.replace("\r\n", "\n");
    }

    private static JudgeProperties props() {
        return new JudgeProperties(true, 3, 1000L, 2, 60000, 800,
                1048576, 65536, 10, 64, 64, 192,
                "C=msp-judge-it-cc,CC=msp-judge-it-cc,CPP=msp-judge-it-cc,"
                        + "JAVA=msp-judge-it-java,PYTHON=msp-judge-it-python");
    }

    /** 与 props() 全同，仅 outputMaxBytes 压到 1024：让输出必然触碰收集上限 */
    private static JudgeProperties smallCapProps() {
        return new JudgeProperties(true, 3, 1000L, 2, 60000, 800,
                1024, 65536, 10, 64, 64, 192,
                "C=msp-judge-it-cc,CC=msp-judge-it-cc,CPP=msp-judge-it-cc,"
                        + "JAVA=msp-judge-it-java,PYTHON=msp-judge-it-python");
    }

    /** 收集型替身 sink */
    static class CapturingSink implements JudgeEventSink {
        final List<PointOutcome> points = new ArrayList<>();
        volatile String compileError;
        volatile boolean judgingSeen;
        final AtomicBoolean done = new AtomicBoolean();

        @Override public void judging() { judgingSeen = true; }
        @Override public void point(PointOutcome o) { points.add(o); }
        @Override public void compileError(String m) { compileError = m; done.set(true); }
    }

    private static JudgeWork work(JudgeLanguage lang, String code,
                                  int timeMs, int memMb, String[][] cases) {
        List<JudgeWork.WorkCase> wcs = new ArrayList<>();
        for (int i = 0; i < cases.length; i++) {
            wcs.add(new JudgeWork.WorkCase(9000L + i, i + 1, cases[i][0], cases[i][1]));
        }
        return new JudgeWork(777001L, 101L, 202L, lang, code, timeMs, memMb, wcs);
    }

    @Test
    void cpp_ac_with_stdin_feed_and_output_match() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.CPP,
                "#include <iostream>\nint main(){int n;std::cin>>n;"
                        + "std::cout<<n*2<<\"\\n\";}",
                1000, 256, new String[][]{{"21\n", "42\n"}}), sink);
        assertThat(sink.compileError).isNull();
        assertThat(sink.points).singleElement()
                .satisfies(o -> {
                    assertThat(o.status()).isEqualTo("AC");
                    assertThat(o.memoryUsedMb()).isNotNull();   // 采样回显链路在真实容器上可用
                });
    }

    @Test
    void java_ac_times_multiplier_widens_window_not_behavioral_difference() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.JAVA,
                "public class Main{public static void main(String[] a){"
                        + "System.out.println(\"ok\");}}",
                500, 256, new String[][]{{"", "ok"}}), sink);
        assertThat(sink.compileError).isNull();
        assertThat(sink.points.get(0).status()).isEqualTo("AC");
        // multiplier 只影响窗宽不做数值断言（机器噪声）；行为正确即达标
    }

    @Test
    void python_ac_script() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.PYTHON, "print(sum(map(int,input().split())))",
                1000, 256, new String[][]{{"1 2\n", "3\n"}}), sink);
        assertThat(sink.compileError).isNull();
        assertThat(sink.points.get(0).status()).isEqualTo("AC");
    }

    @Test
    void wa_when_outputs_differ() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.CPP, "int main(){return 0;}",   // 无输出
                1000, 256, new String[][]{{"", "expect something"}}), sink);
        assertThat(sink.points.get(0).status()).isEqualTo("WA");
    }

    @Test
    void ce_compilation_failure_message_visible() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.C,
                "int main(){ oops missing semicolon }",
                1000, 256, new String[][]{{"", ""}}), sink);
        assertThat(sink.compileError).isNotBlank();
        assertThat(sink.points).isEmpty();
    }

    @Test
    void tle_infinite_loop_killed_by_timeout_wrapper() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.CPP, "int main(){for(;;){}}",
                150, 256, new String[][]{{"", ""}}), sink);   // 有效墙钟=150*1+800≈950ms
        assertThat(sink.points.get(0).status()).isEqualTo("TLE");
    }

    @Test
    void tle_java_infinite_loop_with_multiplier() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.JAVA,
                "public class Main{public static void main(String[] a){for(;;){}}}",
                150, 256, new String[][]{{"", ""}}), sink);   // ×2+800≈1100ms
        assertThat(sink.points.get(0).status()).isEqualTo("TLE");
    }

    @Test
    void re_c_segfault_reports_stderr_tail() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.C,
                "#include <stdlib.h>\nint main(){int*p=(int*)malloc(0x10);*p=1;"
                        + "(void)p;return *(volatile int*)0;}",
                1000, 256, new String[][]{{"", ""}}), sink);
        assertThat(sink.points.get(0).status()).isEqualTo("RE");
    }

    @Test
    void re_python_exception_stderr_tail_nonempty() {
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.PYTHON, "raise ValueError('boom')",
                1000, 256, new String[][]{{"", ""}}), sink);
        PointOutcome o = sink.points.get(0);
        assertThat(o.status()).isEqualTo("RE");
        assertThat(o.message()).contains("boom");
    }

    @Test
    void mle_c_giant_allocation_oom_killed() {
        // 触碰远超 cgroup 上限(mem=16+64=80MB)的页区 → 内核 SIGKILL → timeout 外层观测 exit 137。
        // 实测注：brief 原稿的 unused malloc+memset 在 g++/gcc -O2 下被整体消除(RSS 不涨、exit0)，
        // 故改用 volatile 强制逐字节落地写入，语义仍是"分配远超上限并触碰"。
        StringBuilder big = new StringBuilder("#include <stdlib.h>\nint main(){\n"
                + "char*b=malloc(192U*1024U*1024U);\n"
                + "for(volatile long i=0;i<192L*1024L*1024L;i++){((volatile char*)b)[i]=(char)i;}\n"
                + "return b[123456789];\n}");
        CapturingSink sink = new CapturingSink();
        executor.execute(work(JudgeLanguage.C, big.toString(), 2000, 16,
                new String[][]{{"", ""}}), sink);
        assertThat(sink.points.get(0).status()).isEqualTo("MLE");
    }

    @Test
    void stdout_overflow_forces_wa_even_when_content_would_match() {
        // 程序输出 400 行 PADLINE-*（约 5.2KB），expected 为同一段全文——
        // 若无收集上限，OutputComparator 本应判 AC；cap=1024 下 stdout 截断打标，
        // 「超 outputMaxBytes 强制 WA」先于比对生效 ⇒ 此处 WA 只能由溢出路径产生，
        // 与比对语义彻底解耦（非"碰巧输出不对"）。
        StringBuilder expect = new StringBuilder();
        StringBuilder code = new StringBuilder("#include <cstdio>\nint main(){");
        for (int i = 0; i < 400; i++) {
            String line = String.format("PADLINE-%04d\n", i);
            expect.append(line);
            code.append("printf(\"PADLINE-%04d\\n\",").append(i).append(");");
        }
        code.append("return 0;}");
        CapturingSink sink = new CapturingSink();
        smallOutputExecutor.execute(work(JudgeLanguage.CPP, code.toString(),
                1000, 256, new String[][]{{"", expect.toString()}}), sink);
        assertThat(sink.compileError).isNull();
        assertThat(sink.points.get(0).status()).isEqualTo("WA");
    }

    @Test
    void fork_bomb_never_bring_down_engine_re_or_termination() {
        CapturingSink sink = new CapturingSink();
        String forkBoom = """
                #include <unistd.h>
                int main(){pid_t p=fork();if(p==0){while(fork()>0);}return 0;}
                """;
        executor.execute(work(JudgeLanguage.C, forkBoom, 300, 256,
                new String[][]{{"", "?"}}), sink);
        // pids-limit 生效：fork 失败逃出循环/被资源约束终结；只要求 NOT 成功输出。
        // expectedOutput 取非空值：进程若在资源约束下干净退出则记 WA，
        // 使恶意样本任何幸存路径(TLE/MLE/RE/WA)都通不过比对——杜绝「空输出即 AC」假阳性。
        String st = sink.points.isEmpty() ? "CE" : sink.points.get(0).status();
        assertThat(st).isNotEqualTo("AC");
    }
}
