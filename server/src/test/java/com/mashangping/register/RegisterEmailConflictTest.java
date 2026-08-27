package com.mashangping.register;

import com.mashangping.IntegrationTestBase;
import com.mashangping.emailverify.EmailVerification;
import com.mashangping.emailverify.EmailVerificationMapper;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterEmailConflictTest extends IntegrationTestBase {

    @Autowired private UserMapper userMapper;
    @Autowired private EmailVerificationMapper emailVerificationMapper;

    @Test
    void register_with_occupied_email_returns_40017() throws Exception {
        // 预置一个已占用该邮箱的账号（直接落库，绕过发码流程）
        User existing = new User();
        existing.setUsername("20269991");
        existing.setPasswordHash(passwordEncoder.encode("secret66"));
        existing.setRealName("已有者");
        existing.setStudentNo("20269991");
        existing.setEmail("occupied@stu.example.edu.cn");
        existing.setRole(User.ROLE_STUDENT);
        existing.setEnabled(true);
        userMapper.insert(existing);

        // 为新注册者预置验证码（该邮箱可发码，但邮箱已被占用）
        EmailVerification v = new EmailVerification();
        v.setEmail("occupied@stu.example.edu.cn");
        v.setCode("123456");
        v.setPurpose("REGISTER");
        v.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        v.setCreatedAt(LocalDateTime.now());
        v.setUsed(false);
        emailVerificationMapper.insert(v);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"occupied@stu.example.edu.cn\",\"code\":\"123456\","
                                + "\"studentNo\":\"20269992\",\"realName\":\"新人\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40017));
    }
}
