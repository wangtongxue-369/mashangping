package com.mashangping.assignment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 建/改作业共用入参；title/startAt/dueAt 必填，lateDays 缺省=0、isPublished 缺省=false */
public record AssignmentUpsertRequest(
        @NotBlank(message = "作业标题不能为空") @Size(max = 200) String title,
        String description,
        @NotNull(message = "开始时间必填") LocalDateTime startAt,
        @NotNull(message = "结束时间必填") LocalDateTime dueAt,
        @Min(value = 0, message = "宽限天数须在0~7") @Max(value = 7, message = "宽限天数须在0~7") Integer lateDays,
        Boolean isPublished) {
}
