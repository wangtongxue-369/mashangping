package com.mashangping.auth;

import com.mashangping.IntegrationTestBase;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends IntegrationTestBase {

    @Autowired
    private UserMapper userMapper;

    @BeforeEach
    void seedUser() {
        User probe = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, "login_alice"));
        if (probe == null) {
            User u = new User();
            u.setUsername("login_alice");
            u.setPasswordHash(passwordEncoder.encode("secret66"));
            u.setRealName("爱丽丝");
            u.setRole(User.ROLE_STUDENT);
            u.setEnabled(true);
            userMapper.insert(u);
        }
    }

    @Test
    void login_success_returns_token_and_profile() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"login_alice\",\"password\":\"secret66\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("login_alice"))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void wrong_password_returns_BAD_CREDENTIALS() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"login_alice\",\"password\":\"wrong99\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void unknown_username_also_returns_BAD_CREDENTIALS() throws Exception {
        // 与密码错误同一提示，防止用户名枚举（规格 §9.2 安全意识）
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"no_such_user\",\"password\":\"whatever\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    void disabled_user_returns_USER_DISABLED() throws Exception {
        // 先造一个停用账号
        if (userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, "login_bob")) == null) {
            User u = new User();
            u.setUsername("login_bob");
            u.setPasswordHash(passwordEncoder.encode("secret66"));
            u.setRealName("鲍勃");
            u.setRole(User.ROLE_STUDENT);
            u.setEnabled(false);
            userMapper.insert(u);
        }
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"login_bob\",\"password\":\"secret66\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40003));
    }

    @Test
    void blank_fields_return_PARAM_INVALID() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
