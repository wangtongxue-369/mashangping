package com.mashangping.assignment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.problem.CourseProblem;
import com.mashangping.problem.CourseProblemMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentProblemConfigTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private ProblemMapper problemMapper;
    @Autowired private CourseProblemMapper courseProblemMapper;
    @Autowired private AssignmentMapper assignmentMapper;
    @Autowired private AssignmentProblemMapper assignmentProblemMapper;

    private long uidA;
    private long uidB;
    private long courseIdA;
    private long p1;
    private long p2;
    private long assignmentId;

    @BeforeEach
    void seed() throws Exception {
        uidA = ensureUser("apc_t_a", "TEACHER", null, "甲老师");
        uidB = ensureUser("apc_t_b", "TEACHER", null, "乙老师");
        Course c = new Course();
        c.setName("选题配分课");
        c.setTerm("2025-2026-1");
        c.setTeacherId(uidA);
        courseMapper.insert(c);
        courseIdA = c.getId();

        p1 = createProblem(uidA, "题一");
        p2 = createProblem(uidA, "题二");
        selectIntoCourse(p1);
        selectIntoCourse(p2);

        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime due = start.plusHours(2);
        String body = mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"配分作业\",\"startAt\":\"" + start
                                + "\",\"dueAt\":\"" + due + "\"}"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assignmentId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
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

    private String teacherA() { return bearer(jwtService, uidA, "apc_t_a", "TEACHER"); }
    private String teacherB() { return bearer(jwtService, uidB, "apc_t_b", "TEACHER"); }

    private long createProblem(long ownerUid, String title) {
        Problem p = new Problem();
        p.setTeacherId(ownerUid);
        p.setTitle(title);
        p.setDescription("d");
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        return p.getId();
    }

    private void selectIntoCourse(long problemId) {
        CourseProblem cp = new CourseProblem();
        cp.setCourseId(courseIdA);
        cp.setProblemId(problemId);
        cp.setSortOrder(1);
        courseProblemMapper.insert(cp);
    }

    private String batchJson(String... pairs) {
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) items.append(",");
            items.append("{\"problemId\":").append(pairs[i])
                    .append(",\"score\":").append(pairs[i + 1]).append("}");
        }
        // 简报模板漏掉闭合中括号（产生 {"items":[{...},{...} 非法 JSON），按计划缺陷自修规则补上
        items.append("]");
        return "{\"items\":" + items + "}";
    }

    @Test
    void batch_add_lists_with_scores_and_incrementing_sort() throws Exception {
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "10", String.valueOf(p2), "20")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/assignments/" + assignmentId).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.problems.length()").value(2))
                .andExpect(jsonPath("$.data.problems[0].problemId").value((int) p1))
                .andExpect(jsonPath("$.data.problems[0].score").value(10))
                .andExpect(jsonPath("$.data.problems[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.problems[1].problemId").value((int) p2))
                .andExpect(jsonPath("$.data.problems[1].sortOrder").value(2));
    }

    @Test
    void add_is_atomic_when_any_item_invalid() throws Exception {
        // 混入一个不在课程选题范围的题：整批全拒，零写入
        long foreign = createProblem(uidB, "乙的题");
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "10", String.valueOf(foreign), "20")))
                .andExpect(jsonPath("$.code").value(40400));

        Long rows = assignmentProblemMapper.selectCount(
                new LambdaQueryWrapper<AssignmentProblem>()
                        .eq(AssignmentProblem::getAssignmentId, assignmentId));
        assertThat(rows).isZero();
    }

    @Test
    void duplicate_within_items_rejected() throws Exception {
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "10", String.valueOf(p1), "20")))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void add_already_in_assignment_rejected() throws Exception {
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "10")))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "10")))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void update_score_and_remove_problem() throws Exception {
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "10", String.valueOf(p2), "20")))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/assignments/" + assignmentId + "/problems/" + p1)
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":50}"))
                .andExpect(jsonPath("$.code").value(0));

        // 终审补强：改分后详情回读确认落库（p1 排序在前占 problems[0]）
        mockMvc.perform(get("/api/assignments/" + assignmentId).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.problems[0].problemId").value((int) p1))
                .andExpect(jsonPath("$.data.problems[0].score").value(50));

        mockMvc.perform(put("/api/assignments/" + assignmentId + "/problems/" + p1)
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":0}"))
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(put("/api/assignments/" + assignmentId + "/problems/" + p1)
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":10001}"))
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(delete("/api/assignments/" + assignmentId + "/problems/" + p1)
                        .header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/assignments/" + assignmentId).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.problems.length()").value(1));
        // 移除后可重选
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "5")))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void other_teacher_cannot_configure_my_assignment() throws Exception {
        mockMvc.perform(post("/api/assignments/" + assignmentId + "/problems")
                        .header("Authorization", teacherB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batchJson(String.valueOf(p1), "10")))
                .andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(put("/api/assignments/" + assignmentId + "/problems/" + p1)
                        .header("Authorization", teacherB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}"))
                .andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(delete("/api/assignments/" + assignmentId + "/problems/" + p1)
                        .header("Authorization", teacherB()))
                .andExpect(jsonPath("$.code").value(40400));
    }
}
