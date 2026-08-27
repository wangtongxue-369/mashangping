package com.mashangping.judging;

import java.util.List;

/** 判题收束：compileErrorMessage 非空表示 CE 整局；points 只含已执行到的测试点 */
public record JudgeOutcome(String compileErrorMessage, List<PointOutcome> points) {

    public static JudgeOutcome compileError(String message) {
        return new JudgeOutcome(message, List.of());
    }

    public boolean isCompileError() {
        return compileErrorMessage != null;
    }
}
