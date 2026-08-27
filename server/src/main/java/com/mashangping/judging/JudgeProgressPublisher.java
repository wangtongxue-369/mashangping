package com.mashangping.judging;

/** 判题进度推送 SPI：T6 STOMP 实现；缺席时调度器静默跳过 */
public interface JudgeProgressPublisher {

    void judging(long userId, long submissionId);

    void point(long userId, long submissionId, int pointIndex, String status);

    void compileError(long userId, long submissionId, String message);

    void finished(long userId, long submissionId, String status,
                  Integer score, int passedCount, int totalCount);
}
