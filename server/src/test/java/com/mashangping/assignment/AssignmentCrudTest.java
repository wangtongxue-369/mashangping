package com.mashangping.assignment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
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

class AssignmentCrudTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private AssignmentMapper assignmentMapper;

    private long uidA;
    private long uidB;
    private long courseIdA;

    @BeforeEach
    void seed() {
        uidA = ensureUser("asgn_t_a", "TEACHER", null, "甲老师");
        uidB = ensureUser("asgn_t_b", "TEACHER", null, "乙老师");
        Course c = new Course();
        c.setName("作业课");
        c.setTerm("2025-2026-1");
        c.setTeacherId(uidA);
        courseMapper.insert(c);
        courseIdA = c.getId();
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

    private String teacherA() { return bearer(jwtService, uidA, "asgn_t_a", "TEACHER"); }
    private String teacherB() { return bearer(jwtService, uidB, "asgn_t_b", "TEACHER"); }

    private String fmt(LocalDateTime t) { return t.toString(); }

    private String json(String title, LocalDateTime start, LocalDateTime due, Integer lateDays, Boolean pub) {
        return "{\"title\":\"" + title + "\",\"startAt\":\"" + fmt(start) + "\",\"dueAt\":\""
                + fmt(due) + "\",\"lateDays\":" + lateDays + ",\"isPublished\":" + pub + "}";
    }

    @Test
    void create_with_defaults_then_list_then_detail() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime due = start.plusHours(2);
        mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("实验一", start, due, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.title").value("实验一"));

        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].title").value("实验一"))
                .andExpect(jsonPath("$.data.records[0].status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.records[0].problemCount").value(0))
                .andExpect(jsonPath("$.data.records[0].totalScore").value(0))
                .andExpect(jsonPath("$.data.records[0].isPublished").value(false));
    }

    @Test
    void due_not_after_start_rejected() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusHours(1);
        mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("坏作业", start, start, 0, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void late_days_out_of_range_rejected() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime due = start.plusHours(2);
        mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("坏宽限", start, due, 8, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void update_toggles_publish_and_persists() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime due = start.plusHours(2);
        String body = mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("可改作业", start, due, 0, false)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        long id = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();

        mockMvc.perform(put("/api/assignments/" + id)
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("改名作业", start, due, 2, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/assignments/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.title").value("改名作业"))
                .andExpect(jsonPath("$.data.isPublished").value(true))
                .andExpect(jsonPath("$.data.lateDays").value(2));
    }

    @Test
    void other_teacher_sees_40400_and_no_cross_access() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime due = start.plusHours(2);
        long id = createAssignment(start, due);

        mockMvc.perform(get("/api/assignments/" + id).header("Authorization", teacherB()))
                .andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(put("/api/assignments/" + id).header("Authorization", teacherB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("越权改", start, due, 0, false)))
                .andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(delete("/api/assignments/" + id).header("Authorization", teacherB()))
                .andExpect(jsonPath("$.code").value(40400));
        // 乙在甲的课程上建作业
        mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherB())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("越权建", start, due, 0, false)))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void delete_cascades_assignment_problems() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime due = start.plusHours(2);
        long id = createAssignment(start, due);
        long pid = createProblem("级联题");
        insertAssignmentProblem(id, pid, 10, 1);

        mockMvc.perform(delete("/api/assignments/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(0));

        Long left = assignmentMapper.selectCount(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getId, id));
        assertThat(left).isZero();
        Long apLeft = assignmentProblemMapper.selectCount(new LambdaQueryWrapper<AssignmentProblem>()
                .eq(AssignmentProblem::getAssignmentId, id));
        assertThat(apLeft).isZero();
    }

    @Test
    void assigned_problem_cannot_be_deleted_while_locked_body_hidden() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime due = start.plusHours(2);
        long id = createAssignment(start, due);
        long pid = createProblem("锁定题");
        insertAssignmentProblem(id, pid, 10, 1);

        // 作业详情展示题目清单
        mockMvc.perform(get("/api/assignments/" + id).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.data.problems[0].problemId").value((int) pid))
                .andExpect(jsonPath("$.data.problems[0].score").value(10))
                .andExpect(jsonPath("$.data.problems[0].sortOrder").value(1));
    }

    private long createProblem(String title) {
        Problem p = new Problem();
        p.setTeacherId(uidA);
        p.setTitle(title);
        p.setDescription("d");
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        return p.getId();
    }

    private long createAssignment(LocalDateTime start, LocalDateTime due) throws Exception {
        String body = mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("种子作业", start, due, 0, false)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    private void insertAssignmentProblem(long assignmentId, long problemId, int score, int sortOrder) {
        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(problemId);
        ap.setScore(score);
        ap.setSortOrder(sortOrder);
        assignmentProblemMapper.insert(ap);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private ProblemMapper problemMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private AssignmentProblemMapper assignmentProblemMapper;
}
