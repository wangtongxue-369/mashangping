package com.mashangping.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(min = 6, max = 64, message = "密码长度须为6~64位") String newPassword) {}
