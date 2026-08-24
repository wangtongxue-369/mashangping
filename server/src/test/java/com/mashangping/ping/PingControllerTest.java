package com.mashangping.ping;

import com.mashangping.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PingControllerTest extends IntegrationTestBase {

    @Test
    void ping_returns_unified_response() throws Exception {
        mockMvc.perform(get("/api/ping").with(user("tester").roles("TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void invalid_body_returns_PARAM_INVALID_code() throws Exception {
        // echo 要求 name 非空，空 body 应触发参数校验异常 → code=40000
        mockMvc.perform(post("/api/ping/echo")
                                .contentType("application/json")
                                .content("{}")
                                .with(user("tester").roles("TEACHER"))
                                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").exists());
    }
}
