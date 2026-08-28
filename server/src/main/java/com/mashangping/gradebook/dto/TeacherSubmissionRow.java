package com.mashangping.gradebook.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeacherSubmissionRow(
        long submissionId, String studentNo, String studentName,
        String problemTitle, String language, String status,
        Integer score, Integer passedCount, Integer totalCount,
        Integer timeUsedMs, Integer memoryUsedMb,
        LocalDateTime submittedAt, boolean isLate) {

    public record SamplePoint(int pointIndex, String status, Integer timeUsedMs,
                              Integer memoryUsedMb, String input, String expectedOutput,
                              String message) {}

    public record MaskedPoint(int pointIndex, String status, Integer timeUsedMs,
                              Integer memoryUsedMb) {}

    public record Detail(long submissionId, String code, String language, String status,
                         List<SamplePoint> samples, List<MaskedPoint> maskedPoints) {}
}