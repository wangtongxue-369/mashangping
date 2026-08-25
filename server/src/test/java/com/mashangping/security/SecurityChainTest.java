package com.mashangping.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityChainTest extends IntegrationTestBase {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserMapper userMapper;

    private long studentUid;

    /** 实时吊销改造后 token 须对应库内真实用户；DataInitializer 的 admin 固定占据 id=1，
     *  故专用种子 chain_student，用其真实 uid 签发，不与假 uid/角色冲突 */
    @BeforeEach
    void seedChainStudent() {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "chain_student"));
        if (probe == null) {
            User u = new User();
            u.setUsername("chain_student");
            u.setPasswordHash(passwordEncoder.encode("secret66"));
            u.setRealName("链路学生");
            u.setRole(User.ROLE_STUDENT);
            u.setEnabled(true);
            userMapper.insert(u);
            probe = u;
        }
        studentUid = probe.getId();
    }

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
                        .header("Authorization", bearer(jwtService, studentUid, "chain_student", "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void insufficient_role_returns_403_with_code_40300() throws Exception {
        mockMvc.perform(get("/api/admin-only-probe")
                        .header("Authorization", bearer(jwtService, studentUid, "chain_student", "STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
