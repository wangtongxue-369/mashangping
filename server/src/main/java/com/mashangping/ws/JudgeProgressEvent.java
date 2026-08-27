package com.mashangping.ws;

/** 推送载荷：固定字段全集，按 type 只填相关键位 */
public record JudgeProgressEvent(String type, Long submissionId, Integer pointIndex,
                                 String status, String message, String finalStatus,
                                 Integer score, Integer passedCount, Integer totalCount) {
}
