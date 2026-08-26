package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TestCaseCrudTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private TestCaseMapper testCaseMapper;

    private long uidA;
    private long uidB;

    @BeforeEach
    void seedUsers() {
        uidA = ensureUser("tc_t_a", "TEACHER", null, "甲老师");
        uidB = ensureUser("tc_t_b", "TEACHER", null, "乙老师");
    }

    private long ensureUser(String username, String role, String studentNo, String realName) {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (probe != null) return probe.getId();
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("secret66"));
        u.setRealName(realName);
        u.setRole(role);
        u.setStudentNo(studentNo);
        u.setEnabled(true);
        userMapper.insert(u);
        return u.getId();
    }

    private String teacherA() { return bearer(jwtService, uidA, "tc_t_a", "TEACHER"); }
    private String teacherB() { return bearer(jwtService, uidB, "tc_t_b", "TEACHER"); }

    private long createProblem(String token, String title) throws Exception {
        mockMvc.perform(post("/api/problems").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"d\"}"))
                .andExpect(jsonPath("$.code").value(0));
        String resp = mockMvc.perform(get("/api/problems?keyword=" + title)
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return ((Number) com.jayway.jsonpath.JsonPath.read(resp, "$.data.records[0].id")).longValue();
    }

    private String caseBody(String input, String output, boolean sample) {
        return "{\"input\":\"" + input + "\",\"expectedOutput\":\"" + output
                + "\",\"isSample\":" + sample + "}";
    }

    @Test
    void create_read_update_delete_full_cycle_ordered_by_id() throws Exception {
        long pid = createProblem(teacherA(), "周期题");

        mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(caseBody("1\\n", "1\\n", true)))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(caseBody("2\\n", "2\\n", false)))
                .andExpect(jsonPath("$.code").value(0));

        String detail = mockMvc.perform(get("/api/problems/" + pid).header("Authorization", teacherA()))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(((Number) com.jayway.jsonpath.JsonPath.read(detail, "$.data.testCases.length()")).intValue())
                .isEqualTo(2);
        // JsonPath.read 泛型返回需显式定型，否则 assertThat 多重载歧义（同 Number 中转模式）
        assertThat((String) com.jayway.jsonpath.JsonPath.read(detail, "$.data.testCases[0].input"))
                .isEqualTo("1\n"); // 展示顺序=id 升序
        assertThat((boolean) com.jayway.jsonpath.JsonPath.read(detail, "$.data.testCases[0].isSample"))
                .isTrue();

        long tcId = ((Number) com.jayway.jsonpath.JsonPath.read(detail, "$.data.testCases[1].id")).longValue();
        mockMvc.perform(put("/api/problems/" + pid + "/test-cases/" + tcId)
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(caseBody("22\\n", "22\\n", true)))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(testCaseMapper.selectById(tcId).getInput()).isEqualTo("22\n");

        mockMvc.perform(delete("/api/problems/" + pid + "/test-cases/" + tcId)
                        .header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(testCaseMapper.selectById(tcId)).isNull();
    }

    @Test
    void fifty_cases_allowed_fifty_first_rejected() throws Exception {
        long pid = createProblem(teacherA(), "上限题");
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                            .header("Authorization", teacherA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(caseBody("in" + i, "out" + i, false)))
                    .andExpect(jsonPath("$.code").value(0));
        }
        mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(caseBody("over", "over", false)))
                .andExpect(jsonPath("$.code").value(40000)); // 第51个 PARAM_INVALID
    }

    @Test
    void sixty_four_kib_boundary_exact_pass_one_byte_over_rejected() throws Exception {
        long pid = createProblem(teacherA(), "边界题");
        // 恰 65536 UTF-8 字节（ASCII 单字节）
        String exact = "x".repeat(65536);
        mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(caseBody(exact, "ok", false)))
                .andExpect(jsonPath("$.code").value(0));

        String over = "x".repeat(65537);
        mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(caseBody(over, "ok", false)))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void update_allowed_when_at_case_limit() throws Exception {
        long pid = createProblem(teacherA(), "满员可改题");
        for (int i = 0; i < 50; i++) {
            mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                            .header("Authorization", teacherA())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(caseBody("in" + i, "out" + i, false)))
                    .andExpect(jsonPath("$.code").value(0));
        }
        // 满员后第 51 个新增仍被拒（不可增）
        mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(caseBody("over", "over", false)))
                .andExpect(jsonPath("$.code").value(40000));

        // 满员状态下更新第 1 点应放行且落库（可改）
        String detail = mockMvc.perform(get("/api/problems/" + pid).header("Authorization", teacherA()))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long firstTcId = ((Number) com.jayway.jsonpath.JsonPath.read(detail, "$.data.testCases[0].id")).longValue();
        mockMvc.perform(put("/api/problems/" + pid + "/test-cases/" + firstTcId)
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(caseBody("fixed", "fixed-out", false)))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(testCaseMapper.selectById(firstTcId).getInput()).isEqualTo("fixed");
    }

    @Test
    void foreign_teacher_gets_40400_on_case_endpoints() throws Exception {
        long pid = createProblem(teacherA(), "他师题");
        mockMvc.perform(post("/api/problems/" + pid + "/test-cases")
                        .header("Authorization", teacherB())
                        .contentType(MediaType.APPLICATION_JSON).content(caseBody("1", "1", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(delete("/api/problems/" + pid + "/test-cases/999999")
                        .header("Authorization", teacherB()))
                .andExpect(jsonPath("$.code").value(40400));
    }
}
