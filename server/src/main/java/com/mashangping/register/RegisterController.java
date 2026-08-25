package com.mashangping.register;

import com.mashangping.auth.dto.LoginResponse;
import com.mashangping.common.ApiResponse;
import com.mashangping.register.dto.RegisterRequest;
import com.mashangping.register.dto.SendCodeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegisterController {

    private final VerificationCodeService verificationCodeService;
    private final RegisterService registerService;

    @PostMapping("/register/code")
    public ApiResponse<Void> sendCode(@Valid @RequestBody SendCodeRequest request) {
        verificationCodeService.issue(request.email());
        return ApiResponse.ok();
    }

    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(registerService.register(request));
    }
}
