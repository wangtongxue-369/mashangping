package com.mashangping.security;

import com.mashangping.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityChainTest extends IntegrationTestBase {

    @Autowired
    private JwtService jwtService;

    @Test
    void no_token_returns_401_with_code_40100() throws Exception {
        mockMvc.perform(get("/api/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void garbage_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void valid_token_passes() throws Exception {
        mockMvc.perform(get("/api/ping")
                        .header("Authorization", bearer(jwtService, 1L, "t1", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void insufficient_role_returns_403_with_code_40300() throws Exception {
        mockMvc.perform(get("/api/admin-only-probe")
                        .header("Authorization", bearer(jwtService, 1L, "s1", "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
