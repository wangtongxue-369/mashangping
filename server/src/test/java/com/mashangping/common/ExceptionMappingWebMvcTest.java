package com.mashangping.common;

import com.mashangping.ping.PingController;
import com.mashangping.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 异常映射的独立 MVC 切片测试：不依赖数据库与 Docker，任何环境都真实执行。
 * 覆盖 GlobalExceptionHandler 的专用映射（此前仅静态推演、无自动化用例）。
 */
@WebMvcTest(PingController.class)
@AutoConfigureMockMvc
class ExceptionMappingWebMvcTest {

    /** 切片会扫描到 Filter 类型的 JwtAuthFilter（其依赖 JwtService），用 Mock 补齐即可 */
    @MockBean
    private JwtService jwtService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void malformed_json_maps_to_PARAM_INVALID() throws Exception {
        mockMvc.perform(post("/api/ping/echo")
                        .with(user("tester").roles("TEACHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void unknown_path_maps_to_NOT_FOUND() throws Exception {
        mockMvc.perform(get("/api/no/such/path")
                        .with(user("tester").roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void unsupported_method_maps_to_PARAM_INVALID() throws Exception {
        mockMvc.perform(delete("/api/ping")
                        .with(user("tester").roles("TEACHER")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
