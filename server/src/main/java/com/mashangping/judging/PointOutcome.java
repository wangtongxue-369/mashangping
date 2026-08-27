package com.mashangping.judging;

/** 逐点结果；message 仅 RE 有内容 */
public record PointOutcome(int pointIndex, long testCaseId, String status,
                           Integer timeUsedMs, Integer memoryUsedMb, String message) {
}
