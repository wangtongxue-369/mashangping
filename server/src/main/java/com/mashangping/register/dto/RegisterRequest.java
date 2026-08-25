package com.mashangping.register.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email(message = "邮箱格式不正确") String email,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "验证码须为6位数字") String code,
        @NotBlank @Pattern(regexp = "\\w{3,30}", message = "学号须为3~30位字母/数字/下划线") String studentNo,
        @NotBlank @Size(max = 50, message = "姓名过长") String realName,
        @NotBlank @Size(min = 6, max = 64, message = "密码长度须为6~64位") String password) {}
