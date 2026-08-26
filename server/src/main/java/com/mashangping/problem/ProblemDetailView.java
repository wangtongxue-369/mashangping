package com.mashangping.problem;

import java.time.LocalDateTime;
import java.util.List;

/** 详情出参（属主视角）：MD 原文 + 全部测试点（含隐藏点） */
public record ProblemDetailView(long id, String title, String description,
                                List<String> languages, int timeLimitMs, int memoryLimitMb,
                                boolean isPublic, List<TestCaseView> testCases,
                                LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static ProblemDetailView from(Problem p, List<TestCase> cases) {
        List<TestCaseView> views = cases.stream().map(TestCaseView::from).toList();
        return new ProblemDetailView(p.getId(), p.getTitle(), p.getDescription(),
                Languages.parse(p.getAllowedLanguages()), p.getTimeLimitMs(), p.getMemoryLimitMb(),
                Boolean.TRUE.equals(p.getIsPublic()), views, p.getCreatedAt(), p.getUpdatedAt());
    }
}
