package com.mashangping.assignment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** 批量选入入参：原子批量，任一非法全拒 */
public record AssignmentProblemsRequest(
        @NotEmpty(message = "items 不能为空") List<@Valid Item> items) {

    public record Item(
            @NotNull(message = "problemId 必填") Long problemId,
            @NotNull(message = "score 必填") @Min(value = 1, message = "分值须在1~10000") @Max(value = 10000, message = "分值须在1~10000") Integer score) {
    }
}
