package com.mashangping.analytics;

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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 课程/作业聚合端点 IT：概览/AC率/分布/时间线/查重风险 + 越权 40400 + 学生 403。 */
class AnalyticsApiIT extends IntegrationTestBase {

    @Autowired JwtService jwtService;
    @Autowired UserMapper userMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EnrollmentMapper enrollmentMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired CourseProblemMapper courseProblemMapper;
    @Autowired ProblemMapper problemMapper;
    @Autowired SubmissionMapper submissionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long teacherUid;
    private long uidA;
    private long uidB;
    private long courseId;
    private long assignmentId;
    private long problemId;

    @BeforeEach
    void seed() {
        teacherUid = ensureUser("anl_teacher", User.ROLE_TEACHER, null, "分析老师");
        Course c = new Course();
        c.setName("数据分析课");
        c.setTerm("2026-2027-1");
        c.setTeacherId(teacherUid);
        c.setDescription("聚合端点 IT");
        courseMapper.insert(c);
        courseId = c.getId();

        uidA = ensureUser("anl_st_a", User.ROLE_STUDENT, "ANLA001", "学生A");
        uidB = ensureUser("anl_st_b", User.ROLE_STUDENT, "ANLA002", "学生B");
        enroll(c.getId(), uidA, "ANLA001", "学生A");
        enroll(c.getId(), uidB, "ANLA002", "学生B");

        Problem p = new Problem();
        p.setTeacherId(teacherUid);
        p.setTitle("分析题");
        p.setDescription("A+B");
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        problemId = p.getId();

        CourseProblem cp = new CourseProblem();
        cp.setCourseId(courseId);
        cp.setProblemId(problemId);
        courseProblemMapper.insert(cp);

        Assignment a = new Assignment();
        a.setCourseId(courseId);
        a.setTitle("分析作业");
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setDueAt(LocalDateTime.now().plusDays(2));
        a.setLateDays(0);
        a.setIsPublished(true);
        assignmentMapper.insert(a);
        assignmentId = a.getId();

        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(problemId);
        ap.setScore(100);
        ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);

        submission(uidA, ap.getId(), Submission.STATUS_AC, 100);
        submission(uidB, ap.getId(), Submission.STATUS_WA, 0);
        submission(uidB, ap.getId(), Submission.STATUS_AC, 100); // B 后来 AC
    }

    @Test
    @SuppressWarnings("unchecked")
    void overview_and_trend_distribution_acRate_timeline() throws Exception {
        String overview = bodyAsTeacher("/api/courses/" + courseId + "/analytics/overview");
        Map<String, Object> od = (Map<String, Object>) objectMapper.readValue(overview, Map.class).get("data");
        assertThat(((Number) od.get("studentCount")).intValue()).isEqualTo(2);
        assertThat(((Number) od.get("assignmentCount")).intValue()).isEqualTo(1);
        assertThat(((Number) od.get("courseProblemCount")).intValue()).isEqualTo(1);
        assertThat(((Number) od.get("latestAvg")).doubleValue()).isEqualTo(100.0); // A100+B100 平均

        String trend = bodyAsTeacher("/api/courses/" + courseId + "/analytics/score-trend");
        List<Map<String, Object>> trendData = (List<Map<String, Object>>) objectMapper.readValue(trend, Map.class).get("data");
        assertThat(trendData).hasSize(1);
        assertThat(((Number) trendData.get(0).get("avg")).doubleValue()).isEqualTo(100.0);

        String dist = bodyAsTeacher("/api/assignments/" + assignmentId + "/analytics/score-distribution");
        Map<String, Object> dd = (Map<String, Object>) objectMapper.readValue(dist, Map.class).get("data");
        List<Map<String, Object>> buckets = (List<Map<String, Object>>) dd.get("buckets");
        assertThat(buckets.stream().filter(b -> "90-100".equals(b.get("label")))
                .mapToLong(b -> ((Number) b.get("count")).longValue()).sum()).isEqualTo(2);

        String ac = bodyAsTeacher("/api/courses/" + courseId + "/analytics/ac-rate");
        List<Map<String, Object>> acData = (List<Map<String, Object>>) objectMapper.readValue(ac, Map.class).get("data");
        assertThat(acData).hasSize(1);
        assertThat(((Number) acData.get(0).get("acStudents")).longValue()).isEqualTo(2);
        assertThat(((Number) acData.get(0).get("submittedStudents")).longValue()).isEqualTo(2);

        String tl = bodyAsTeacher("/api/courses/" + courseId + "/analytics/submission-timeline");
        List<Map<String, Object>> tlData = (List<Map<String, Object>>) objectMapper.readValue(tl, Map.class).get("data");
        assertThat(((Number) tlData.get(0).get("count")).longValue()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void non_owner_40400_and_student_403() throws Exception {
        long other = ensureUser("anl_other", User.ROLE_TEACHER, null, "别班老师");
        mockMvc.perform(get("/api/courses/" + courseId + "/analytics/overview")
                        .header("Authorization", bearer(jwtService, other, "anl_other", "TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(get("/api/courses/" + courseId + "/analytics/overview")
                        .header("Authorization", bearer(jwtService, uidA, "anl_st_a", "STUDENT")))
                .andExpect(status().isForbidden());
    }

    private String bodyAsTeacher(String path) throws Exception {
        return mockMvc.perform(get(path)
                        .header("Authorization", bearer(jwtService, teacherUid, "anl_teacher", "TEACHER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
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

    private void submission(long uid, long apId, String status, Integer score) {
        Submission s = new Submission();
        s.setProblemId(problemId);
        s.setUserId(uid);
        s.setAssignmentId(assignmentId);
        s.setAssignmentProblemId(apId);
        s.setLanguage("C");
        s.setCode("int main(){return 0;}");
        s.setStatus(status);
        s.setScore(score);
        s.setPassedCount(score == null ? 0 : 1);
        s.setTotalCount(1);
        s.setIsLate(false);
        s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);
    }
}
