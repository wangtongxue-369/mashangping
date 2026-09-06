package com.mashangping.judging;

/** 逐点结果；message 仅 RE stderr 尾段有内容；actualOutput 仅 WA 时写回该点实际输出尾段 */
public record PointOutcome(int pointIndex, long testCaseId, String status,
                           Integer timeUsedMs, Integer memoryUsedMb, String message,
                           String actualOutput) {
}
