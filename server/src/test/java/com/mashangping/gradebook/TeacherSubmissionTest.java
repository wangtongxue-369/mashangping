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
import com.mashangping.judging.JudgeDetail;
import com.mashangping.judging.JudgeDetailMapper;
import com.mashangping.judging.Submission;
import com.mashangping.judging.SubmissionMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeacherSubmissionTest extends IntegrationTestBase {

    @Autowired JwtService jwtService;
    @Autowired UserMapper userMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EnrollmentMapper enrollmentMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired ProblemMapper problemMapper;
    @Autowired TestCaseMapper testCaseMapper;
    @Autowired SubmissionMapper submissionMapper;
    @Autowired JudgeDetailMapper judgeDetailMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long teacherUid;
    private long otherUid;
    private long assignmentId;
    private long ap1;
    private long ap2;
    private long uidA;
    private long uidB;
    private long sampleTcId;
    private long hiddenTcId;
    private long acSubmissionId;

    @BeforeEach
    void seed() {
        teacherUid = ensureUser("gb_t_a2", User.ROLE_TEACHER, null, "甲老师");
        otherUid = ensureUser("gb_t_b2", User.ROLE_TEACHER, null, "乙老师");
        Course c = new Course();
        c.setName("历史课"); c.setTerm("2025-2026-1"); c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        uidA = ensureUser("gb_st_a2", User.ROLE_STUDENT, "S001", "学生A");
        uidB = ensureUser("gb_st_b2", User.ROLE_STUDENT, "S002", "学生B");
        enroll(c.getId(), uidA, "S001", "学生A");
        enroll(c.getId(), uidB, "S002", "学生B");

        Problem p1 = problem("题1");
        Problem p2 = problem("题2");
        TestCase tc1 = new TestCase();
        tc1.setProblemId(p1.getId()); tc1.setInput("1\n"); tc1.setExpectedOutput("2\n");
        tc1.setIsSample(true); testCaseMapper.insert(tc1); sampleTcId = tc1.getId();
        TestCase tc2 = new TestCase();
        tc2.setProblemId(p1.getId()); tc2.setInput("9\n"); tc2.setExpectedOutput("10\n");
        tc2.setIsSample(false); testCaseMapper.insert(tc2); hiddenTcId = tc2.getId();

        Assignment a = new Assignment();
        a.setCourseId(c.getId()); a.setTitle("提交史");
        a.setStartAt(LocalDateTime.now().minusDays(1)); a.setDueAt(LocalDateTime.now().plusDays(3));
        a.setLateDays(0); a.setIsPublished(true);
        assignmentMapper.insert(a);
        assignmentId = a.getId();
        ap1 = addAp(assignmentId, p1.getId(), 20, 1);
        ap2 = addAp(assignmentId, p2.getId(), 30, 2);

        long s1 = submission(uidA, ap1, p1.getId(), Submission.STATUS_AC, 20);
        submission(uidA, ap2, p2.getId(), Submission.STATUS_WA, 0);
        submission(uidB, ap1, p1.getId(), Submission.STATUS_AC, 20);
        acSubmissionId = s1;

        JudgeDetail d1 = new JudgeDetail();
        d1.setSubmissionId(s1); d1.setTestCaseId(sampleTcId); d1.setPointIndex(1);
        d1.setStatus("AC"); d1.setTimeUsedMs(2); d1.setMemoryUsedMb(1);
        d1.setMessage("样例通过"); judgeDetailMapper.insert(d1);
        JudgeDetail d2 = new JudgeDetail();
        d2.setSubmissionId(s1); d2.setTestCaseId(hiddenTcId); d2.setPointIndex(2);
        d2.setStatus("AC"); d2.setTimeUsedMs(5); d2.setMemoryUsedMb(2);
        d2.setMessage("隐藏点stderr"); judgeDetailMapper.insert(d2);
    }

    @Test
    void history_paginates_and_filters() throws Exception {
        String body = submissionsBody(teacherUid, "gb_t_a2", "assign", 1, 2, null, null);
        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertThat(((List<?>) data.get("records"))).hasSize(2);
        assertThat(((Number) data.get("total")).intValue()).isEqualTo(3);

        body = submissionsBody(teacherUid, "gb_t_a2", "assign", 1, 20, ap1, null);
        root = objectMapper.readValue(body, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) ((Map<String, Object>) root.get("data")).get("records");
        assertThat(records).hasSize(2);
        assertThat(records).allSatisfy(r -> assertThat(r.get("problemTitle")).isEqualTo("题1"));

        body = submissionsBody(teacherUid, "gb_t_a2", "assign", 1, 20, null, uidA);
        root = objectMapper.readValue(body, Map.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recordsA = (List<Map<String, Object>>) ((Map<String, Object>) root.get("data")).get("records");
        assertThat(recordsA).hasSize(2);
        assertThat(recordsA).allSatisfy(r -> assertThat(r.get("studentNo")).isEqualTo("S001"));
    }

    @Test
    void non_owner_teacher_gets_40400() throws Exception {
        mockMvc.perform(get("/api/assignments/" + assignmentId + "/submissions")
                        .header("Authorization", bearer(jwtService, otherUid, "gb_t_b2", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void detail_samples_full_hidden_points_masked() throws Exception {
        String body = detailBody(teacherUid, "gb_t_a2", acSubmissionId);
        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertThat(data.get("code")).isEqualTo("int main(){return 0;}");
        assertThat(data.get("language")).isEqualTo("C");
        assertThat(data.get("status")).isEqualTo("AC");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> samples = (List<Map<String, Object>>) data.get("samples");
        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).get("message")).isEqualTo("样例通过");
        assertThat(samples.get(0).get("input")).isEqualTo("1\n");
        assertThat(samples.get(0).get("expectedOutput")).isEqualTo("2\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> masked = (List<Map<String, Object>>) data.get("maskedPoints");
        assertThat(masked).hasSize(1);
        assertThat(masked.get(0)).doesNotContainKey("message");
        assertThat(masked.get(0)).doesNotContainKey("input");
        assertThat(masked.get(0)).doesNotContainKey("expectedOutput");
        assertThat(masked.get(0).get("status")).isEqualTo("AC");
    }

    @Test
    void detail_submission_not_in_assignment_gets_40400() throws Exception {
        mockMvc.perform(get("/api/assignments/" + assignmentId + "/submissions/" + 99999L)
                        .header("Authorization", bearer(jwtService, teacherUid, "gb_t_a2", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    private String submissionsBody(long uid, String username, String apParam, int page, int size,
                                   Long ap, Long sid) throws Exception {
        var builder = get("/api/assignments/" + assignmentId + "/submissions")
                .header("Authorization", bearer(jwtService, uid, username, "TEACHER"))
                .param("page", String.valueOf(page))
                .param("size", String.valueOf(size));
        if (ap != null) builder.param("assignmentProblemId", String.valueOf(ap));
        if (sid != null) builder.param("studentId", String.valueOf(sid));
        return mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String detailBody(long uid, String username, long submissionId) throws Exception {
        return mockMvc.perform(get("/api/assignments/" + assignmentId + "/submissions/" + submissionId)
                        .header("Authorization", bearer(jwtService, uid, username, "TEACHER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
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

    private long submission(long uid, long apId, long problemId, String status, Integer score) {
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
        return s.getId();
    }
}