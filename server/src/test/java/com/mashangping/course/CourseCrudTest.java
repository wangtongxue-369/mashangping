package com.mashangping.course;

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

class CourseCrudTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;

    private long uidA;
    private long uidB;
    private long uidC;

    @BeforeEach
    void seedUsers() {
        uidA = ensureUser("cud_t_a", "TEACHER", null, "甲老师");
        uidB = ensureUser("cud_t_b", "TEACHER", null, "乙老师");
        uidC = ensureUser("cud_s_c", "STUDENT", "20266001", "丙同学");
    }

    /** 实时吊销改造后 Bearer 必须对应库内真实用户，故先落库再签发 */
    private long ensureUser(String username, String role, String studentNo, String realName) {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (probe != null) {
            return probe.getId();
        }
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

    private String teacherA() { return bearer(jwtService, uidA, "cud_t_a", "TEACHER"); }
    private String teacherB() { return bearer(jwtService, uidB, "cud_t_b", "TEACHER"); }
    private String studentC() { return bearer(jwtService, uidC, "cud_s_c", "STUDENT"); }

    private String body(String name) {
        return "{\"name\":\"" + name + "\",\"term\":\"2025-2026-1\",\"description\":\"desc-" + name + "\"}";
    }

    @Test
    void teacher_creates_and_reads_own_course() throws Exception {
        mockMvc.perform(post("/api/courses").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(body("编译原理")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("编译原理"));

        mockMvc.perform(get("/api/courses?keyword=编译").header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].name").value("编译原理"));

        mockMvc.perform(get("/api/courses/" + ownedCourseIdByKeyword("编译原理"))
                        .header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.term").value("2025-2026-1"));
    }

    private long ownedCourseIdByKeyword(String kw) throws Exception {
        String resp = mockMvc.perform(get("/api/courses?keyword=" + kw)
                        .header("Authorization", teacherA()))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // json-smart 将小整数反序列化为 Integer，经 Number 中转避免 Integer→Long 的 ClassCastException
        return ((Number) com.jayway.jsonpath.JsonPath.read(resp, "$.data.records[0].id")).longValue();
    }

    @Test
    void other_teacher_gets_40400_not_403_on_foreign_course() throws Exception {
        mockMvc.perform(post("/api/courses").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(body("机器学习")));
        long courseId = ownedCourseIdByKeyword("机器学习");

        mockMvc.perform(get("/api/courses/" + courseId).header("Authorization", teacherB()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(put("/api/courses/" + courseId).header("Authorization", teacherB())
                        .contentType(MediaType.APPLICATION_JSON).content(body("改名尝试")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void student_is_forbidden_from_teacher_endpoints() throws Exception {
        mockMvc.perform(post("/api/courses").header("Authorization", studentC())
                        .contentType(MediaType.APPLICATION_JSON).content(body("越权课")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void update_changes_fields_within_owner_scope() throws Exception {
        mockMvc.perform(post("/api/courses").header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content(body("软件工程")));
        long id = ownedCourseIdByKeyword("软件工程");

        mockMvc.perform(put("/api/courses/" + id).header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"软件工程导论\",\"term\":\"2025-2026-2\",\"description\":null}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/courses/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.name").value("软件工程导论"))
                .andExpect(jsonPath("$.data.term").value("2025-2026-2"));
    }

    @Test
    void list_pagination_and_keyword_filter() throws Exception {
        for (String n : new String[]{"高数上", "高数下", "线性代数"}) {
            mockMvc.perform(post("/api/courses").header("Authorization", teacherA())
                            .contentType(MediaType.APPLICATION_JSON).content(body(n)))
                    .andExpect(jsonPath("$.code").value(0));
        }
        mockMvc.perform(get("/api/courses?page=1&size=2&keyword=高数")
                        .header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }
}
