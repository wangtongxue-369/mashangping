package com.mashangping.problem;

import java.time.LocalDateTime;
import java.util.List;

/** 列表出参：不含 MD 正文 */
public record ProblemSummaryView(long id, String title, List<String> languages,
                                 int timeLimitMs, int memoryLimitMb, boolean isPublic,
                                 LocalDateTime createdAt) {

    public static ProblemSummaryView from(Problem p) {
        return new ProblemSummaryView(p.getId(), p.getTitle(),
                Languages.parse(p.getAllowedLanguages()),
                p.getTimeLimitMs(), p.getMemoryLimitMb(),
                Boolean.TRUE.equals(p.getIsPublic()), p.getCreatedAt());
    }
}
