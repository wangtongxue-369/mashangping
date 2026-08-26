package com.mashangping.problem;

/** 属主视角测试点出参：含隐藏点（仅在教师可达响应中出现） */
public record TestCaseView(long id, String input, String expectedOutput, boolean isSample) {

    public static TestCaseView from(TestCase tc) {
        return new TestCaseView(tc.getId(), tc.getInput(), tc.getExpectedOutput(),
                Boolean.TRUE.equals(tc.getIsSample()));
    }
}
