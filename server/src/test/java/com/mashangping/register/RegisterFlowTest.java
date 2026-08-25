package com.mashangping.register;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
import com.mashangping.emailverify.EmailVerification;
import com.mashangping.emailverify.EmailVerificationMapper;
import com.mashangping.mail.MailSender;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegisterFlowTest extends IntegrationTestBase {

    @Autowired private UserMapper userMapper;
    @Autowired private EnrollmentMapper enrollmentMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private EmailVerificationMapper emailVerificationMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @MockBean private MailSender mailSender;   // 捕获发信内容

    private static final Pattern CODE_IN_BODY = Pattern.compile("\\b(\\d{6})\\b");

    /** 走完整发码流程并从捕获的邮件正文中提取验证码 */
    private String obtainCode(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailSender, times(1)).send(org.mockito.ArgumentMatchers.eq(email),
                org.mockito.ArgumentMatchers.any(), body.capture());
        Matcher m = CODE_IN_BODY.matcher(body.getValue());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private String registerJson(String email, String code, String no, String name, String pw) {
        return "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"studentNo\":\""
                + no + "\",\"realName\":\"" + name + "\",\"password\":\"" + pw + "\"}";
    }

    @Test
    void full_register_flow_creates_student_account() throws Exception {
        String email = "fresh@stu.example.edu.cn";
        String code = obtainCode(email);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, code, "20261001", "王小明", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.user.username").value("20261001"));

        User saved = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, "20261001"));
        assertThat(saved).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("20261001");
        assertThat(passwordEncoder.matches("pass123", saved.getPasswordHash())).isTrue();
        assertThat(Boolean.TRUE.equals(saved.getEnabled())).isTrue();
    }

    @Test
    void registering_activates_pending_enrollments_in_all_courses() throws Exception {
        // 两门课都给 20261002 预置 PENDING 名单（教师 uid 仅作占位，归属不在本用例范围）
        Course c1 = new Course();
        c1.setName("数据结构"); c1.setTerm("2025-2026-1"); c1.setTeacherId(8888L);
        courseMapper.insert(c1);
        Course c2 = new Course();
        c2.setName("操作系统"); c2.setTerm("2025-2026-1"); c2.setTeacherId(8888L);
        courseMapper.insert(c2);
        enrollmentMapper.insert(pendingRow(c1.getId(), "20261002", "李四"));
        enrollmentMapper.insert(pendingRow(c2.getId(), "20261002", "李四"));

        String code = obtainCode("lisi@stu.example.edu.cn");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("lisi@stu.example.edu.cn", code,
                                "20261002", "李四", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<Enrollment> rows = enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getStudentNo, "20261002"));
        assertThat(rows).hasSize(2);
        Long newUid = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, "20261002")).getId();
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(Enrollment.STATUS_ACTIVE);
            assertThat(r.getStudentId()).isEqualTo(newUid);
        });
    }

    private Enrollment pendingRow(Long courseId, String no, String name) {
        Enrollment e = new Enrollment();
        e.setCourseId(courseId);
        e.setStudentNo(no);
        e.setStudentName(name);
        e.setStatus(Enrollment.STATUS_PENDING);
        return e;
    }
    @Test
    void wrong_or_expired_code_rejected() throws Exception {
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"wrong@stu.example.edu.cn\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("wrong@stu.example.edu.cn", "000000",
                                "20261003", "赵六", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40011));
    }

    @Test
    void domain_outside_whitelist_rejected_with_40010() throws Exception {
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eve@gmail.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40010));
    }

    @Test
    void resend_within_60s_rate_limited_with_40012() throws Exception {
        String email = "ratelimit@stu.example.edu.cn";
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40012));
    }

    @Test
    void daily_limit_of_ten_codes_enforced() throws Exception {
        String email = "daily@stu.example.edu.cn";
        // 直接落库 10 条当日记录（created_at 显式取 JVM 时钟：容器 CURRENT_TIMESTAMP 走 UTC，
        // 与 JVM 本地时钟存在时区偏移，不能依赖 DB 默认值做"当日"判定）；
        // 无论命中 60 秒限频还是日上限分支，对外都是同一错误码 40012
        for (int i = 0; i < 10; i++) {
            EmailVerification v = new EmailVerification();
            v.setEmail(email);
            v.setCode("0000" + i);
            v.setPurpose("REGISTER");
            v.setCreatedAt(LocalDateTime.now());
            v.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            v.setUsed(false);
            emailVerificationMapper.insert(v);
        }
        mockMvc.perform(post("/api/auth/register/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40012));
    }
    @Test
    void duplicate_student_no_conflicts_with_40013() throws Exception {
        String email = "dup@stu.example.edu.cn";
        String code = obtainCode(email);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, code, "20261999", "孙七", "pass123")))
                .andExpect(jsonPath("$.code").value(0));

        String email2 = "dup2@stu.example.edu.cn";
        String code2 = obtainCode(email2);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email2, code2, "20261999", "孙七二", "pass123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40013));
    }

    @Test
    void concurrent_same_student_no_registrations_yield_exactly_one_success() throws Exception {
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                String email = "race" + idx + "@stu.example.edu.cn";
                // 发码走各自邮箱，不受单邮箱限频影响；MockMvc 线程安全
                mockMvc.perform(post("/api/auth/register/code")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\"}"))
                        .andExpect(jsonPath("$.code").value(0));
                // 绕过 Mockito 跨线程取值的竞态，直接查库拿最新未用验证码
                EmailVerification v = emailVerificationMapper.selectOne(
                        new LambdaQueryWrapper<EmailVerification>()
                                .eq(EmailVerification::getEmail, email)
                                .eq(EmailVerification::getUsed, false)
                                .orderByDesc(EmailVerification::getId)
                                .last("LIMIT 1"));
                assertNotNull(v);
                String json = registerJson(email, v.getCode(),
                        "20268888", "并发者" + idx, "pass123");

                ready.countDown();
                go.await(); // 全线程就绪后同时放行，制造竞争窗口
                var resp = mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andReturn();
                String responseBody = resp.getResponse().getContentAsString(
                        java.nio.charset.StandardCharsets.UTF_8);
                boolean ok = responseBody.contains("\"code\":0");
                boolean conflict = responseBody.contains("\"code\":40013");
                assertThat(ok || conflict).as(responseBody).isTrue();
                return ok ? 1 : 0;
            }));
        }
        ready.await();
        go.countDown();
        int successes = 0;
        for (Future<Integer> f : futures) {
            successes += f.get();
        }
        pool.shutdown();

        // 唯一约束兜底：恰好一个建号成功，其余全部归一为 40013（无论落在哪个检查点）
        assertThat(successes).isEqualTo(1);
    }
}
