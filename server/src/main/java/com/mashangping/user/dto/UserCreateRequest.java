package com.mashangping.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
        @NotBlank @Pattern(regexp = "\\w{3,50}", message = "用户名须为3~50位字母/数字/下划线") String username,
        @NotBlank @Size(min = 6, max = 64, message = "密码长度须为6~64位") String password,
        @NotBlank @Size(max = 50) String realName,
        @NotNull String role,
        @Size(max = 30) String studentNo) {}
