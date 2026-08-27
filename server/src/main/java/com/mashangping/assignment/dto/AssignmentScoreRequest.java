package com.mashangping.assignment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 单题改分入参 */
public record AssignmentScoreRequest(
        @NotNull(message = "score 必填") @Min(value = 1, message = "分值须在1~10000") @Max(value = 10000, message = "分值须在1~10000") Integer score) {
}
