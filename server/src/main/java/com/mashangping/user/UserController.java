package com.mashangping.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.ApiResponse;
import com.mashangping.user.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @PostMapping
    public ApiResponse<UserView> create(@Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.ok(UserView.from(userService.createUser(request)));
    }

    @GetMapping
    public ApiResponse<Page<UserView>> list(
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(userService.listUsers(role, page, size));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable long id,
                                          @Valid @RequestBody UserStatusRequest request) {
        userService.updateStatus(id, request.enabled());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable long id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
        return ApiResponse.ok();
    }
}
