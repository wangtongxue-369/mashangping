package com.mashangping.ping;

import com.mashangping.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ping")
public class PingController {

    public record EchoRequest(@NotBlank(message = "不能为空") String name) {}

    @GetMapping
    public ApiResponse<String> ping() {
        return ApiResponse.ok("pong");
    }

    /** 仅供参数校验链路测试使用 */
    @PostMapping("/echo")
    public ApiResponse<String> echo(@Valid @RequestBody EchoRequest req) {
        return ApiResponse.ok(req.name());
    }
}
