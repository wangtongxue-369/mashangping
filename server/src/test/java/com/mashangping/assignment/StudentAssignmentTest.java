package com.mashangping.assignment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCase;
import com.mashangping.problem.TestCaseMapper;
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

class StudentAssignmentTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private EnrollmentMapper enrollmentMapper;
    @Autowired private ProblemMapper problemMapper;
    @Autowired private TestCaseMapper testCaseMapper;
    @Autowired private AssignmentMapper assignmentMapper;
    @Autowired private AssignmentProblemMapper assignmentProblemMapper;

    private long teacherUid;
    private long studentUid;
    private long outsiderUid;
    private long courseIdA;
    private long visiblePid;
    private long publishedAssignmentId;
    private long draftAssignmentId;

    @BeforeEach
    void seed() throws Exception {
        teacherUid = ensureUser("stu_t_a", "TEACHER", null, "老师");
        studentUid = ensureUser("stu_s_a", "STUDENT", "20261011", "选课生");
        outsiderUid = ensureUser("stu_s_b", "STUDENT", "20261012", "外班生");

        Course c = new Course();
        c.setName("学生视图课");
        c.setTerm("2025-2026-1");
        c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        courseIdA = c.getId();

        Enrollment e = new Enrollment();
        e.setCourseId(courseIdA);
        e.setStudentId(studentUid);
        e.setStudentNo("20261011");
        e.setStudentName("选课生");
        e.setStatus(Enrollment.STATUS_ACTIVE);
        enrollmentMapper.insert(e);

        long pid = createProblemWithCases("可见题");
        visiblePid = pid;
        publishedAssignmentId = createAssignment("已发布作业", LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(2), true);
        draftAssignmentId = createAssignment("草稿作业", LocalDateTime.now().plusHours(1),
                LocalDateTime.now().plusDays(2), false);
        addProblemToAssignment(publishedAssignmentId, pid, 10, 1);
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

    private String teacher() { return bearer(jwtService, teacherUid, "stu_t_a", "TEACHER"); }
    private String student() { return bearer(jwtService, studentUid, "stu_s_a", "STUDENT"); }
    private String outsider() { return bearer(jwtService, outsiderUid, "stu_s_b", "STUDENT"); }

    private long createProblemWithCases(String title) {
        Problem p = new Problem();
        p.setTeacherId(teacherUid);
        p.setTitle(title);
        p.setDescription("MD 题面 " + title);
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        TestCase sample = new TestCase();
        sample.setProblemId(p.getId());
        sample.setInput("样例输入值");
        sample.setExpectedOutput("样例输出值");
        sample.setIsSample(true);
        testCaseMapper.insert(sample);
        TestCase hidden = new TestCase();
        hidden.setProblemId(p.getId());
        hidden.setInput("隐藏输入不得泄漏XYZ");
        hidden.setExpectedOutput("隐藏输出不得泄漏XYZ");
        hidden.setIsSample(false);
        testCaseMapper.insert(hidden);
        return p.getId();
    }

    private long createAssignment(String title, LocalDateTime start, LocalDateTime due, boolean published)
            throws Exception {
        String body = mockMvc.perform(post("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"startAt\":\"" + start
                                + "\",\"dueAt\":\"" + due + "\",\"isPublished\":" + published + "}"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.data.id")).longValue();
    }

    private void addProblemToAssignment(long assignmentId, long problemId, int score, int sortOrder) {
        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(problemId);
        ap.setScore(score);
        ap.setSortOrder(sortOrder);
        assignmentProblemMapper.insert(ap);
    }

    @Test
    void student_list_shows_only_published_assignments() throws Exception {
        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", student()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("已发布作业"))
                .andExpect(jsonPath("$.data.records[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.records[0].problemCount").value(1))
                // 学生行不含教师字段（结构隔离）
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("totalScore"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"isPublished\""))));
    }

    @Test
    void not_started_assignment_status_not_started() throws Exception {
        // due 取晚于 seed 中「已发布作业」的值，保证 due_at 升序时排在第二位
        long future = createAssignment("未来作业", LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(3), true);
        addProblemToAssignment(future, createProblemWithCases("未来题"), 10, 1);
        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", student()))
                .andExpect(jsonPath("$.data.records[0].title").value("已发布作业"))
                .andExpect(jsonPath("$.data.records[1].title").value("未来作业"))
                .andExpect(jsonPath("$.data.records[1].status").value("NOT_STARTED"));
    }

    @Test
    void problems_denied_before_start_at_with_40300() throws Exception {
        long future = createAssignment("未开始作业", LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(2), true);
        long pid = createProblemWithCases("未来题");
        addProblemToAssignment(future, pid, 10, 1);

        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments/" + future + "/problems")
                        .header("Authorization", student()))
                .andExpect(jsonPath("$.code").value(40300));
        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments/" + future + "/problems/" + pid)
                        .header("Authorization", student()))
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void problem_reading_view_shows_samples_and_leaks_no_hidden_case() throws Exception {
        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments/" + publishedAssignmentId + "/problems")
                        .header("Authorization", student()))
                .andExpect(jsonPath("$.data[0].title").value("可见题"))
                .andExpect(jsonPath("$.data[0].score").value(10));

        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments/" + publishedAssignmentId + "/problems/"
                        + visiblePid)
                        .header("Authorization", student()))
                .andExpect(jsonPath("$.data.description").value("MD 题面 可见题"))
                .andExpect(jsonPath("$.data.samples[0].input").value("样例输入值"))
                .andExpect(jsonPath("$.data.samples[0].output").value("样例输出值"))
                // 隐藏点零泄漏：整响应不得出现隐藏点内容
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("隐藏输入不得泄漏XYZ"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("隐藏输出不得泄漏XYZ"))));
    }

    @Test
    void not_enrolled_student_and_outsider_see_40400() throws Exception {
        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", outsider()))
                .andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments/" + publishedAssignmentId + "/problems")
                        .header("Authorization", outsider()))
                .andExpect(jsonPath("$.code").value(40400));
        // 未在作业范围的题目/作业 40400
        mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments/" + draftAssignmentId + "/problems")
                        .header("Authorization", student()))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void teacher_unpublish_hides_assignment_from_student_list() throws Exception {
        // 终审补强：发布开关双向——true 发布可见，false 撤回后学生列表不再含该作业
        long id = createAssignment("撤回作业", LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(2), true);
        assertThat(studentVisibleIds()).contains(id);

        mockMvc.perform(put("/api/assignments/" + id)
                        .header("Authorization", teacher())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"撤回作业\",\"startAt\":\"" + LocalDateTime.now().minusHours(1)
                                + "\",\"dueAt\":\"" + LocalDateTime.now().plusDays(2)
                                + "\",\"isPublished\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(studentVisibleIds()).doesNotContain(id);
    }

    @Test
    void student_forbidden_on_teacher_assignment_detail_endpoint() throws Exception {
        // 终审补强：学生打教师详情端点（@PreAuthorize TEACHER）→ 真 HTTP 403 + 40300
        mockMvc.perform(get("/api/assignments/" + publishedAssignmentId)
                        .header("Authorization", student()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    private List<Long> studentVisibleIds() throws Exception {
        String body = mockMvc.perform(get("/api/courses/" + courseIdA + "/assignments")
                        .header("Authorization", student()))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return ((List<?>) com.jayway.jsonpath.JsonPath.read(body, "$.data.records[*].id"))
                .stream().map(o -> ((Number) o).longValue()).toList();
    }
}
