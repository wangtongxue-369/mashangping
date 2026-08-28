package com.mashangping.gradebook;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
import com.mashangping.judging.Submission;
import com.mashangping.judging.SubmissionMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GradebookAggregationTest extends IntegrationTestBase {

    @Autowired JwtService jwtService;
    @Autowired UserMapper userMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EnrollmentMapper enrollmentMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired ProblemMapper problemMapper;
    @Autowired SubmissionMapper submissionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long teacherUid;
    private long uidA;
    private long uidB;
    private long uidC;
    private long assignmentId;
    private long ap1;
    private long ap2;

    @BeforeEach
    void seed() {
        long teacherUid = ensureUser("gb_t_a", User.ROLE_TEACHER, null, "甲老师");
        this.teacherUid = teacherUid;
        Course c = new Course();
        c.setName("成绩课");
        c.setTerm("2025-2026-1");
        c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        uidA = ensureUser("gb_st_a", User.ROLE_STUDENT, "S001", "学生A");
        uidB = ensureUser("gb_st_b", User.ROLE_STUDENT, "S002", "学生B");
        uidC = ensureUser("gb_st_c", User.ROLE_STUDENT, "S003", "学生C");
        enroll(c.getId(), uidA, "S001", "学生A");
        enroll(c.getId(), uidB, "S002", "学生B");
        enroll(c.getId(), uidC, "S003", "学生C");

        Problem p1 = problem("题1");
        Problem p2 = problem("题2");

        Assignment a = new Assignment();
        a.setCourseId(c.getId());
        a.setTitle("作一");
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setDueAt(LocalDateTime.now().plusDays(3));
        a.setLateDays(0);
        a.setIsPublished(true);
        assignmentMapper.insert(a);
        assignmentId = a.getId();
        ap1 = addAp(assignmentId, p1.getId(), 20, 1);
        ap2 = addAp(assignmentId, p2.getId(), 30, 2);

        submission(uidA, ap1, p1.getId(), Submission.STATUS_WA, 0);
        submission(uidA, ap1, p1.getId(), Submission.STATUS_AC, 20);
        submission(uidB, ap1, p1.getId(), Submission.STATUS_AC, 20);
        submission(uidB, ap2, p2.getId(), Submission.STATUS_WA, 0);
    }

    @Test
    void matrix_best_scores_totals_semantics() throws Exception {
        String body = gradebookBody(teacherUid, "gb_t_a");
        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        assertThat(root.get("code")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertThat(data.get("assignmentId")).isEqualTo(assignmentId);
        assertThat(data.get("assignmentTitle")).isEqualTo("作一");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> students = (List<Map<String, Object>>) data.get("students");
        assertThat(students).hasSize(3);
        assertThat(students).extracting(s -> ((Number) s.get("studentId")).longValue())
                .containsExactlyInAnyOrder(uidA, uidB, uidC);
        assertThat(students).anySatisfy(s -> {
            assertThat(s.get("studentNo")).isEqualTo("S001");
            assertThat(s.get("realName")).isEqualTo("学生A");
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> problems = (List<Map<String, Object>>) data.get("problems");
        assertThat(problems).hasSize(2);
        assertThat(problems).extracting(p -> ((Number) p.get("score")).intValue())
                .containsExactlyInAnyOrder(20, 30);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cells = (List<Map<String, Object>>) data.get("cells");
        assertThat(cells).hasSize(3);
        assertThat(cells).anySatisfy(c -> {
            assertThat(((Number) c.get("studentId")).longValue()).isEqualTo(uidA);
            assertThat(((Number) c.get("assignmentProblemId")).longValue()).isEqualTo(ap1);
            assertThat(((Number) c.get("bestScore")).intValue()).isEqualTo(20);
        });
        assertThat(cells).noneMatch(c ->
                ((Number) c.get("studentId")).longValue() == uidA
                        && ((Number) c.get("assignmentProblemId")).longValue() == ap2);
        assertThat(cells).anySatisfy(c -> {
            assertThat(((Number) c.get("studentId")).longValue()).isEqualTo(uidB);
            assertThat(((Number) c.get("assignmentProblemId")).longValue()).isEqualTo(ap2);
            assertThat(((Number) c.get("bestScore")).intValue()).isZero();
        });
        assertThat(cells).noneMatch(c -> ((Number) c.get("studentId")).longValue() == uidC);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> totals = (List<Map<String, Object>>) data.get("totals");
        assertThat(totalOf(totals, uidA)).isEqualTo(20);
        assertThat(totalOf(totals, uidB)).isEqualTo(20);
        assertThat(totalOf(totals, uidC)).isEqualTo(0);
    }

    @Test
    void non_owner_teacher_gets_40400() throws Exception {
        long other = ensureUser("gb_t_b", User.ROLE_TEACHER, null, "乙老师");
        mockMvc.perform(get("/api/assignments/" + assignmentId + "/gradebook")
                        .header("Authorization", bearer(jwtService, other, "gb_t_b", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    private String gradebookBody(long uid, String username) throws Exception {
        return mockMvc.perform(get("/api/assignments/" + assignmentId + "/gradebook")
                        .header("Authorization", bearer(jwtService, uid, username, "TEACHER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private int totalOf(List<Map<String, Object>> totals, long uid) {
        return totals.stream()
                .filter(t -> ((Number) t.get("studentId")).longValue() == uid)
                .map(t -> ((Number) t.get("total")).intValue())
                .findFirst().orElse(0);
    }

    private long ensureUser(String username, String role, String studentNo, String realName) {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
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

    private void enroll(long courseId, long studentUid, String studentNo, String studentName) {
        Enrollment e = new Enrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentUid);
        e.setStudentNo(studentNo);
        e.setStudentName(studentName);
        e.setStatus(Enrollment.STATUS_ACTIVE);
        enrollmentMapper.insert(e);
    }

    private Problem problem(String title) {
        Problem p = new Problem();
        p.setTeacherId(teacherUid);
        p.setTitle(title);
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        return p;
    }

    private long addAp(long assignmentId, long problemId, int score, int sortOrder) {
        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(problemId);
        ap.setScore(score);
        ap.setSortOrder(sortOrder);
        assignmentProblemMapper.insert(ap);
        return ap.getId();
    }

    private void submission(long uid, long apId, long problemId, String status, Integer score) {
        Submission s = new Submission();
        s.setProblemId(problemId);
        s.setUserId(uid);
        s.setAssignmentId(assignmentId);
        s.setAssignmentProblemId(apId);
        s.setLanguage("C");
        s.setCode("int main(){return 0;}");
        s.setStatus(status);
        s.setScore(score);
        s.setPassedCount(0);
        s.setTotalCount(2);
        s.setIsLate(false);
        s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);
    }
}