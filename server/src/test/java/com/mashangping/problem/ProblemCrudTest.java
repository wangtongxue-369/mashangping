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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemCrudTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseProblemMapper courseProblemMapper;

    private long uidA;
    private long uidB;
    private long uidStu;

    @BeforeEach
    void seedUsers() {
        uidA = ensureUser("pb_t_a", "TEACHER", null, "甲老师");
        uidB = ensureUser("pb_t_b", "TEACHER", null, "乙老师");
        uidStu = ensureUser("pb_s_c", "STUDENT", "20267101", "丙同学");
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

    private String teacherA() { return bearer(jwtService, uidA, "pb_t_a", "TEACHER"); }
    private String teacherB() { return bearer(jwtService, uidB, "pb_t_b", "TEACHER"); }

    private String createBody(String title) {
        return "{\"title\":\"" + title + "\",\"description\":\"# 题面\"}";
    }

    private long ownedProblemIdByTitle(String token, String title) throws Exception {
        String resp = mockMvc.perform(get("/api/problems?keyword=" + title)
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // json-smart 小整数返回 Integer，经 Number 中转（执行注记）
        return ((Number) com.jayway.jsonpath.JsonPath.read(resp, "$.data.records[0].id")).longValue();
    }

    @Test
    void teacher_creates_with_defaults_and_reads_back() throws Exception {
        mockMvc.perform(post("/api/problems").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("A+B")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").exists());

        long id = ownedProblemIdByTitle(teacherA(), "A+B");
        mockMvc.perform(get("/api/problems/" + id).header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("A+B"))
                .andExpect(jsonPath("$.data.languages.length()").value(4))   // 缺省=全支持
                .andExpect(jsonPath("$.data.timeLimitMs").value(1000))
                .andExpect(jsonPath("$.data.memoryLimitMb").value(256))
                .andExpect(jsonPath("$.data.isPublic").value(false));
    }

    @Test
    void language_whitelist_rejects_unknown_key_and_accepts_subset_csv() throws Exception {
        mockMvc.perform(post("/api/problems").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"L1\",\"description\":\"d\",\"allowedLanguages\":[\"JAVA\",\"RUST\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000)); // 非法键 PARAM_INVALID

        mockMvc.perform(post("/api/problems").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"L2\",\"description\":\"d\",\"allowedLanguages\":[\"java\",\"cpp\"]}"))
                .andExpect(jsonPath("$.code").value(0));

        long id = ownedProblemIdByTitle(teacherA(), "L2");
        mockMvc.perform(get("/api/problems/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.languages.length()").value(2))
                .andExpect(jsonPath("$.data.languages[0]").value("JAVA"));
    }

    @Test
    void other_teacher_gets_40400_on_foreign_problem() throws Exception {
        mockMvc.perform(post("/api/problems").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("私题")));
        long id = ownedProblemIdByTitle(teacherA(), "私题");

        mockMvc.perform(get("/api/problems/" + id).header("Authorization", teacherB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(delete("/api/problems/" + id).header("Authorization", teacherB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void update_changes_fields_partial_semantics() throws Exception {
        mockMvc.perform(post("/api/problems").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"原题\",\"description\":\"d\",\"timeLimitMs\":2000}"));
        long id = ownedProblemIdByTitle(teacherA(), "原题");

        mockMvc.perform(put("/api/problems/" + id).header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"新题名\",\"description\":\"d2\",\"allowedLanguages\":[\"CPP\"]}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/problems/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.title").value("新题名"))
                .andExpect(jsonPath("$.data.timeLimitMs").value(2000)) // 未传字段保持
                .andExpect(jsonPath("$.data.languages.length()").value(1));
    }

    @Test
    void delete_fails_40015_when_referenced_then_ok_after_unlink() throws Exception {
        mockMvc.perform(post("/api/problems").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("被引题")));
        long id = ownedProblemIdByTitle(teacherA(), "被引题");

        // 直接落库模拟"已被某课程选用"（选题 API 属 Task 4，此处只验证保护本身）
        CourseProblem ref = new CourseProblem();
        ref.setCourseId(887001L);
        ref.setProblemId(id);
        ref.setSortOrder(1);
        courseProblemMapper.insert(ref);

        mockMvc.perform(delete("/api/problems/" + id).header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40015));

        courseProblemMapper.deleteById(ref.getId()); // 移除引用
        mockMvc.perform(delete("/api/problems/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/problems/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void list_pagination_keyword_and_scoped_to_owner() throws Exception {
        for (String t : new String[]{"排序一", "排序二", "别人看不见"}) {
            mockMvc.perform(post("/api/problems").header("Authorization",
                            t.equals("别人看不见") ? teacherB() : teacherA())
                            .contentType(MediaType.APPLICATION_JSON).content(createBody(t)))
                    .andExpect(jsonPath("$.code").value(0));
        }
        mockMvc.perform(get("/api/problems?page=1&size=10&keyword=排序")
                        .header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    void student_is_forbidden_from_teacher_endpoints() throws Exception {
        String stu = bearer(jwtService, uidStu, "pb_s_c", "STUDENT");
        mockMvc.perform(post("/api/problems").header("Authorization", stu)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("越权")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void oversize_description_rejected_param_invalid() throws Exception {
        String bigDescription = "# 超\n" + "长".repeat(70000); // UTF-8 下 >128KB
        mockMvc.perform(post("/api/problems").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"大题面\",\"description\":\"" + bigDescription + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
