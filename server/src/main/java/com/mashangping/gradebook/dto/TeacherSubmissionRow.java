package com.mashangping.gradebook.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TeacherSubmissionRow(
        long submissionId, String studentNo, String studentName,
        String problemTitle, String language, String status,
        Integer score, Integer passedCount, Integer totalCount,
        Integer timeUsedMs, Integer memoryUsedMb,
        LocalDateTime submittedAt, boolean isLate) {

    /** 样例点：属主视角完整（含实际输出，WA 诊断用）。零泄漏仅约束学生端，教师不设掩码。 */
    public record SamplePoint(int pointIndex, String status, Integer timeUsedMs,
                              Integer memoryUsedMb, String input, String expectedOutput,
                              String message, String actualOutput) {}

    /** 隐藏点（教师属主完整视图：输入/预期/错误信息/实际输出都可诊断 WA）。 */
    public record MaskedPoint(int pointIndex, String status, Integer timeUsedMs,
                              Integer memoryUsedMb, String input, String expectedOutput,
                              String message, String actualOutput) {}

    public record Detail(long submissionId, String code, String language, String status,
                         List<SamplePoint> samples, List<MaskedPoint> maskedPoints) {}
}