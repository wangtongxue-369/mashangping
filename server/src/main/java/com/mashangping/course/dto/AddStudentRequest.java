package com.mashangping.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddStudentRequest(
        @NotBlank @Pattern(regexp = "\\w{3,30}", message = "学号须为3~30位字母/数字/下划线") String studentNo,
        @NotBlank(message = "姓名不能为空") @Size(max = 50) String studentName) {}
