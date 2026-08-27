package com.mashangping.judging;

import java.util.List;

/** 单次判题工单：执行器所需全部输入快照 */
public record JudgeWork(long submissionId, long userId, long problemId,
                        JudgeLanguage language, String code,
                        int timeLimitMs, int memoryLimitMb,
                        List<WorkCase> cases) {

    public record WorkCase(long testCaseId, int pointIndex,
                           String input, String expectedOutput) {
    }
}
