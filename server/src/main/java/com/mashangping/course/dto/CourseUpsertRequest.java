package com.mashangping.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseUpsertRequest(
        @NotBlank(message = "课程名不能为空") @Size(max = 100) String name,
        @NotBlank(message = "学期不能为空") @Size(max = 50) String term,
        @Size(max = 500, message = "描述过长") String description) {}
