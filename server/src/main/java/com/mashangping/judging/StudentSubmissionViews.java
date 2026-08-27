package com.mashangping.judging;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/** 学生视角出参装配（可见性过滤在 service 层结构性完成，不在序列化层打补丁） */
public final class StudentSubmissionViews {

    /** 列表项：未判分(PENDING)时 score 为 null，出参直接不含该键 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Summary(long id, String status, Integer score, boolean isLate,
                          String language, LocalDateTime submittedAt) {
    }

    /** 样例点全量视图 */
    public record SamplePoint(int pointIndex, String status, Integer timeUsedMs,
                              Integer memoryUsedMb, String input, String expectedOutput) {
    }

    /** 隐藏点瘦视图：除这四个字段外不携带任何内容 */
    public record MaskedPoint(int pointIndex, String status,
                              Integer timeUsedMs, Integer memoryUsedMb) {
    }

    public record Detail(long id, long problemId, String language, String code,
                         String status, Integer score, Integer passedCount, Integer totalCount,
                         Integer timeUsedMs, Integer memoryUsedMb, boolean isLate,
                         LocalDateTime submittedAt,
                         List<SamplePoint> samples,
                         List<MaskedPoint> maskedPoints) {
    }
}
