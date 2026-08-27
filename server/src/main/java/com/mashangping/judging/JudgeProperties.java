package com.mashangping.judging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 判题域集中配置：全部走 msp.judge.* 属性（application.yml 块随 T7 落地，
 * 所有键都带默认值，配置缺席时行为=规格 §10 默认表）
 */
@Component
public class JudgeProperties {

    private final boolean enabled;
    private final int concurrent;
    private final long pollMs;
    private final int retryMax;
    private final int compileTimeoutMs;
    private final int timeSlackMs;
    private final int outputMaxBytes;
    private final int codeMaxBytes;
    private final int rateLimitSeconds;
    private final int tmpfsMb;
    private final int memOverheadDefaultMb;
    private final int memOverheadJavaMb;
    private final Map<String, String> images;

    public JudgeProperties(
            @Value("${msp.judge.enabled:false}") boolean enabled,
            @Value("${msp.judge.concurrent:3}") int concurrent,
            @Value("${msp.judge.poll-ms:1000}") long pollMs,
            @Value("${msp.judge.retry-max:2}") int retryMax,
            @Value("${msp.judge.compile-timeout-ms:30000}") int compileTimeoutMs,
            @Value("${msp.judge.time-slack-ms:500}") int timeSlackMs,
            @Value("${msp.judge.output-max-bytes:1048576}") int outputMaxBytes,
            @Value("${msp.judge.code-max-bytes:65536}") int codeMaxBytes,
            @Value("${msp.judge.rate-limit-seconds:10}") int rateLimitSeconds,
            @Value("${msp.judge.tmpfs-mb:64}") int tmpfsMb,
            @Value("${msp.judge.mem-overhead-mb.default:64}") int memOverheadDefaultMb,
            @Value("${msp.judge.mem-overhead-mb.JAVA:192}") int memOverheadJavaMb,
            @Value("${msp.judge.images:"
                    + "C=msp-judge-cc,CC=msp-judge-cc,CPP=msp-judge-cc,"
                    + "JAVA=msp-judge-java,PYTHON=msp-judge-python}")
            String imagesCsv) {
        this.enabled = enabled;
        this.concurrent = concurrent;
        this.pollMs = pollMs;
        this.retryMax = retryMax;
        this.compileTimeoutMs = compileTimeoutMs;
        this.timeSlackMs = timeSlackMs;
        this.outputMaxBytes = outputMaxBytes;
        this.codeMaxBytes = codeMaxBytes;
        this.rateLimitSeconds = rateLimitSeconds;
        this.tmpfsMb = tmpfsMb;
        this.memOverheadDefaultMb = memOverheadDefaultMb;
        this.memOverheadJavaMb = memOverheadJavaMb;
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String pair : imagesCsv.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && !kv[0].isBlank()) {
                parsed.put(kv[0].trim().toUpperCase(java.util.Locale.ROOT), kv[1].trim());
            }
        }
        this.images = java.util.Collections.unmodifiableMap(parsed);
    }

    public boolean isEnabled() { return enabled; }
    public int getConcurrent() { return concurrent; }
    public long getPollMs() { return pollMs; }
    public int getRetryMax() { return retryMax; }
    public int getCompileTimeoutMs() { return compileTimeoutMs; }
    public int getTimeSlackMs() { return timeSlackMs; }
    public int getOutputMaxBytes() { return outputMaxBytes; }
    public int getCodeMaxBytes() { return codeMaxBytes; }
    public int getRateLimitSeconds() { return rateLimitSeconds; }
    public int getTmpfsMb() { return tmpfsMb; }
    public Map<String, String> getImages() { return images; }

    /** 容器内存底座余量：JVM 需要 192MB，其余 64MB（规格 §6 表格） */
    public int memOverheadMb(JudgeLanguage lang) {
        return lang == JudgeLanguage.JAVA ? memOverheadJavaMb : memOverheadDefaultMb;
    }

    public String imageFor(JudgeLanguage lang) {
        String image = images.get(lang.name());
        if (image == null || image.isBlank()) {
            throw new IllegalStateException("msp.judge.images 未覆盖语言键: " + lang);
        }
        return image;
    }
}
