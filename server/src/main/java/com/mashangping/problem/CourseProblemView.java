package com.mashangping.problem;

import java.util.List;

/** 课程已选题目出参：摘要 + 测试点计数 + 排序位次 */
public record CourseProblemView(long problemId, String title, List<String> languages,
                                int timeLimitMs, int memoryLimitMb, boolean isPublic,
                                long testCaseCount, int sortOrder) {
}
