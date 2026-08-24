package com.mashangping.probe;

import com.mashangping.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅用于回归验证 hasRole('ADMIN') 的 403 分路 */
@RestController
public class AdminProbeController {

    @GetMapping("/api/admin-only-probe")
    public ApiResponse<String> adminOnlyProbe() {
        return ApiResponse.ok("admin-ok");
    }
}
