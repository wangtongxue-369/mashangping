package com.mashangping.plagiarism;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
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

/** 查重端点 IT：取样+报告（改名同构 HIGH/不同算法不入报告）+ 越权 40400 + 学生 403 + compare 取码。 */
class PlagiarismApiIT extends IntegrationTestBase {

    private static final String LOOP_A =
            "int main(){int s=0;for(int i=1;i<=100;i++){s+=i;}printf(\"%d\",s);return 0;}\n";
    private static final String RENAMED_B =
            "int main(){int sum=0;for(int j=1;j<=100;j++){sum+=j;}printf(\"%d\",sum);return 0;}\n";
    private static final String FORMULA_C =
            "int main(){int n=100;int s=n*(n+1)/2;printf(\"%d\",s);return 0;}\n";

    @Autowired JwtService jwtService;
    @Autowired UserMapper userMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired ProblemMapper problemMapper;
    @Autowired SubmissionMapper submissionMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long teacherUid;
    private long uidA;
    private long uidB;
    private long uidC;
    private long uidD;
    private long assignmentId;
    private long apId;
    private long submissionA;
    private long submissionB;

    @BeforeEach
    void seed() {
        teacherUid = ensureUser("pgp_teacher", User.ROLE_TEACHER, null, "查重老师");
        Course c = new Course();
        c.setName("查重课");
        c.setTerm("2026-2027-1");
        c.setTeacherId(teacherUid);
        c.setDescription("查重 IT 专用课程");
        courseMapper.insert(c);

        uidA = ensureUser("pgp_st_a", User.ROLE_STUDENT, "PGPA001", "学生A");
        uidB = ensureUser("pgp_st_b", User.ROLE_STUDENT, "PGPA002", "学生B");
        uidC = ensureUser("pgp_st_c", User.ROLE_STUDENT, "PGPA003", "学生C");
        uidD = ensureUser("pgp_st_d", User.ROLE_STUDENT, "PGPA004", "学生D");

        Problem p = new Problem();
        p.setTeacherId(teacherUid);
        p.setTitle("求和题");
        p.setDescription("1..100 求和");
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);

        Assignment a = new Assignment();
        a.setCourseId(c.getId());
        a.setTitle("查重作业");
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setDueAt(LocalDateTime.now().plusDays(3));
        a.setLateDays(0);
        a.setIsPublished(true);
        assignmentMapper.insert(a);
        assignmentId = a.getId();

        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(p.getId());
        ap.setScore(100);
        ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);
        apId = ap.getId();

        // 最高分取样：A/B 同构改名（应 HIGH），C 不同算法（应低于 0.5 不入报告），D 无提交
        submissionA = submission(uidA, apId, p.getId(), "C", LOOP_A, Submission.STATUS_AC, 100);
        submissionB = submission(uidB, apId, p.getId(), "C", RENAMED_B, Submission.STATUS_AC, 100);
        submission(uidC, apId, p.getId(), "C", FORMULA_C, Submission.STATUS_AC, 100);
    }

    @Test
    @SuppressWarnings("unchecked")
    void report_has_only_high_renamed_pair() throws Exception {
        String body = bodyAsTeacher("/api/assignments/" + assignmentId
                + "/plagiarism/problems/" + problemIdOf(apId));
        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        assertThat(root.get("code")).isEqualTo(0);
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertThat(((Number) data.get("participants")).intValue()).isEqualTo(3);
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");
        // A-B 改名同构应唯一入选且 HIGH；C 不同算法 <0.5 不入；D 无提交不参与
        assertThat(items).hasSize(1);
        Map<String, Object> pair = items.get(0);
        assertThat(pair.get("flag")).isEqualTo("HIGH");
        assertThat(((Number) pair.get("similarity")).doubleValue()).isGreaterThanOrEqualTo(0.9);
        Map<String, Object> a = (Map<String, Object>) pair.get("a");
        Map<String, Object> b = (Map<String, Object>) pair.get("b");
        assertThat(studentNoOf(a)).isIn("PGPA001", "PGPA002");
        assertThat(studentNoOf(b)).isIn("PGPA001", "PGPA002");
    }

    @Test
    void non_owner_teacher_gets_40400() throws Exception {
        long other = ensureUser("pgp_other", User.ROLE_TEACHER, null, "别班老师");
        mockMvc.perform(get("/api/assignments/" + assignmentId
                        + "/plagiarism/problems/" + problemIdOf(apId))
                        .header("Authorization", bearer(jwtService, other, "pgp_other", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void student_token_rejected() throws Exception {
        mockMvc.perform(get("/api/assignments/" + assignmentId
                        + "/plagiarism/problems/" + problemIdOf(apId))
                        .header("Authorization", bearer(jwtService, uidA, "pgp_st_a", "STUDENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @SuppressWarnings("unchecked")
    void compare_returns_both_codes() throws Exception {
        String body = bodyAsTeacher("/api/assignments/" + assignmentId
                + "/plagiarism/problems/" + problemIdOf(apId)
                + "/compare?submissionA=" + submissionA + "&submissionB=" + submissionB);
        Map<String, Object> root = objectMapper.readValue(body, Map.class);
        assertThat(root.get("code")).isEqualTo(0);
        Map<String, Object> data = (Map<String, Object>) root.get("data");
        assertThat(String.valueOf(data.get("codeA"))).isEqualTo(LOOP_A);
        assertThat(String.valueOf(data.get("codeB"))).isEqualTo(RENAMED_B);
    }

    private long problemIdOf(long apId) {
        AssignmentProblem ap = assignmentProblemMapper.selectById(apId);
        return ap.getProblemId();
    }

    private String bodyAsTeacher(String path) throws Exception {
        return mockMvc.perform(get(path)
                        .header("Authorization", bearer(jwtService, teacherUid, "pgp_teacher", "TEACHER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String studentNoOf(Map<String, Object> info) {
        return String.valueOf(info.get("studentNo"));
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

    private long submission(long uid, long apId, long problemId, String language,
                            String code, String status, Integer score) {
        Submission s = new Submission();
        s.setProblemId(problemId);
        s.setUserId(uid);
        s.setAssignmentId(assignmentId);
        s.setAssignmentProblemId(apId);
        s.setLanguage(language);
        s.setCode(code);
        s.setStatus(status);
        s.setScore(score);
        s.setPassedCount(score == null ? 0 : 1);
        s.setTotalCount(1);
        s.setIsLate(false);
        s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);
        return s.getId();
    }
}
