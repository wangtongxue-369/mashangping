package com.mashangping.problem;

import java.time.LocalDateTime;
import java.util.List;

/** 详情出参（属主视角）：MD 原文；测试点列表由 Task 3 扩展进本视图 */
public record ProblemDetailView(long id, String title, String description,
                                List<String> languages, int timeLimitMs, int memoryLimitMb,
                                boolean isPublic, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static ProblemDetailView from(Problem p) {
        return new ProblemDetailView(p.getId(), p.getTitle(), p.getDescription(),
                Languages.parse(p.getAllowedLanguages()), p.getTimeLimitMs(), p.getMemoryLimitMb(),
                Boolean.TRUE.equals(p.getIsPublic()), p.getCreatedAt(), p.getUpdatedAt());
    }
}
