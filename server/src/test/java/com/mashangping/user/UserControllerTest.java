package com.mashangping.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest extends IntegrationTestBase {

    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserMapper userMapper;

    /** 实时吊销改造后 token 必须对应库内真实用户：先落库本类的管理端身份 */
    @BeforeEach
    void seedAdmin() {
        ensureUser("p2_admin_admin", "ADMIN");
    }

    /** 找不到同名用户则落库（角色、启用），返回真实 uid */
    private long ensureUser(String username, String role) {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (probe != null) {
            return probe.getId();
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("secret66"));
        u.setRealName("测试" + role);
        u.setRole(role);
        u.setEnabled(true);
        userMapper.insert(u);
        return u.getId();
    }

    private String bearerAs(String role, String username) {
        // 用库内真实用户的 uid 签发，过滤器查库快照才能通过
        return bearer(jwtService, ensureUser(username, role), username, role);
    }

    @Test
    void admin_creates_teacher_successfully() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"t_wang","password":"teach123","realName":"王老师",
                                 "role":"TEACHER","studentNo":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("t_wang"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User saved = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, "t_wang"));
        org.junit.jupiter.api.Assertions.assertNotNull(saved);
        // 库里必须是哈希，不能是明文
        org.junit.jupiter.api.Assertions.assertNotEquals("teach123", saved.getPasswordHash());
    }

    @Test
    void duplicate_username_rejected() throws Exception {
        String body = """
                {"username":"dup_user","password":"pass123","realName":"甲","role":"STUDENT"}
                """;
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void weak_password_or_bad_role_return_PARAM_INVALID() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"weakpw\",\"password\":\"123\",\"realName\":\"乙\",\"role\":\"STUDENT\"}"))
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"badrole\",\"password\":\"pass123\",\"realName\":\"丙\",\"role\":\"SUPERGOD\"}"))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void teacher_cannot_create_users_forbidden_403() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("TEACHER", "tea"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x_user\",\"password\":\"pass123\",\"realName\":\"丁\",\"role\":\"STUDENT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void list_users_paginated_without_password_hash() throws Exception {
        for (int i = 0; i < 3; i++) {
            String body = "{\"username\":\"list_u" + i + "\",\"password\":\"pass123\","
                    + "\"realName\":\"生" + i + "\",\"role\":\"STUDENT\"}";
            mockMvc.perform(post("/api/users")
                            .header("Authorization", bearerAs("ADMIN", "boss"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(jsonPath("$.code").value(0));
        }
        mockMvc.perform(get("/api/users?role=STUDENT&page=1&size=2")
                        .header("Authorization", bearerAs("ADMIN", "boss")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").isNumber())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].passwordHash").doesNotExist());
    }

    @Test
    void disable_user_blocks_login() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"victim1\",\"password\":\"pass123\",\"realName\":\"戊\",\"role\":\"STUDENT\"}"))
                .andExpect(jsonPath("$.code").value(0));

        Long id = userMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                                .eq(User::getUsername, "victim1")).getId();

        mockMvc.perform(put("/api/users/" + id + "/status")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"victim1\",\"password\":\"pass123\"}"))
                .andExpect(jsonPath("$.code").value(40003));
    }

    @Test
    void reset_password_changes_credential() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"resetee\",\"password\":\"oldpass1\",\"realName\":\"己\",\"role\":\"STUDENT\"}"))
                .andExpect(jsonPath("$.code").value(0));

        Long id = userMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                                .eq(User::getUsername, "resetee")).getId();

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass9\"}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"resetee\",\"password\":\"oldpass1\"}"))
                .andExpect(jsonPath("$.code").value(40002));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"resetee\",\"password\":\"newpass9\"}"))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void operate_missing_user_returns_NOT_FOUND() throws Exception {
        mockMvc.perform(put("/api/users/999999/password")
                        .header("Authorization", bearerAs("ADMIN", "boss"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newpass9\"}"))
                .andExpect(jsonPath("$.code").value(40400));
    }
}
