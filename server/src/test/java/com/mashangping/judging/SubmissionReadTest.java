package com.mashangping.judging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生读侧集成测试：历史列表 + 详情。
 * 核心红线——隐藏测试点的 input/expectedOutput/message 物理不出现在响应 JSON（结构性缺席）。
 */
class SubmissionReadTest extends IntegrationTestBase {

    @Autowired UserMapper userMapper; @Autowired JwtService jwtService;
    @Autowired CourseMapper courseMapper; @Autowired EnrollmentMapper enrollmentMapper;
    @Autowired ProblemMapper problemMapper; @Autowired TestCaseMapper testCaseMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired SubmissionMapper submissionMapper; @Autowired JudgeDetailMapper judgeDetailMapper;
    @Autowired JudgeTaskMapper judgeTaskMapper;

    long stuUid; String bearer; Problem problem; AssignmentProblem ap; Assignment assignment; Course course;

    /** PER_CLASS 生命周期下实例字段跨用例留存：记录锚归属，防跨学生误复用 */
    long anchorOwnerUid = -1;

    private static final String SECRET_INPUT = "TOPSECRET-INPUT-9x7";
    private static final String SECRET_EXPECTED = "TOPSECRET-EXPECTED-3k1";

    @BeforeEach
    void seed() {
        User u = new User();
        u.setUsername("stuR" + System.nanoTime());
        u.setPasswordHash(passwordEncoder.encode("pw123456"));
        u.setRealName("读侧生"); u.setRole(User.ROLE_STUDENT); u.setEnabled(true);
        userMapper.insert(u);
        stuUid = u.getId();
        bearer = bearer(jwtService, stuUid, u.getUsername(), u.getRole());

        problem = new Problem();
        problem.setTeacherId(9100L); problem.setTitle("读题");
        problem.setDescription("d"); problem.setIsPublic(true);
        problem.setTimeLimitMs(1000); problem.setMemoryLimitMb(256);
        problemMapper.insert(problem);

        TestCase sample = new TestCase();
        sample.setProblemId(problem.getId()); sample.setInput("1\n");
        sample.setExpectedOutput("2\n"); sample.setIsSample(true);
        testCaseMapper.insert(sample);
        TestCase hidden = new TestCase();
        hidden.setProblemId(problem.getId()); hidden.setInput(SECRET_INPUT + "\n");
        hidden.setExpectedOutput(SECRET_EXPECTED + "\n"); hidden.setIsSample(false);
        testCaseMapper.insert(hidden);
    }

    /**
     * 造一条已判完的作业提交：AC 样例点 + WA 隐藏点(带 message)，返回 submission id。
     * 锚（课程/选课/作业/AP）按 uid 复用：同一学生多次调用落在同一 AP 上，
     * 使"历史列表"语义成立；换学生则重建锚（归属隔离）。
     */
    private long seedJudgedAssignmentSubmission(long uid) {
        if (course == null || anchorOwnerUid != uid) {
            course = new Course();
            course.setName("课R" + System.nanoTime()); course.setTerm("2025-2026-1");
            course.setTeacherId(9101L); courseMapper.insert(course);
            Enrollment e = new Enrollment();
            e.setCourseId(course.getId()); e.setStudentId(uid);
            e.setStudentNo("R" + System.nanoTime()); e.setStudentName("读侧生");
            e.setStatus(Enrollment.STATUS_ACTIVE); enrollmentMapper.insert(e);
            assignment = new Assignment();
            assignment.setCourseId(course.getId()); assignment.setTitle("作R");
            assignment.setDescription("d"); assignment.setStartAt(LocalDateTime.now().minusDays(1));
            assignment.setDueAt(LocalDateTime.now().plusDays(3));
            assignment.setLateDays(0); assignment.setIsPublished(true);
            assignmentMapper.insert(assignment);
            ap = new AssignmentProblem();
            ap.setAssignmentId(assignment.getId()); ap.setProblemId(problem.getId());
            ap.setScore(7); ap.setSortOrder(1);
            assignmentProblemMapper.insert(ap);
            anchorOwnerUid = uid;
        }

        Submission s = new Submission();
        s.setProblemId(problem.getId()); s.setUserId(uid);
        s.setAssignmentId(assignment.getId()); s.setAssignmentProblemId(ap.getId());
        s.setLanguage("CPP"); s.setCode("int main(){return 0;}");
        s.setStatus(Submission.STATUS_WA); s.setScore(0);
        s.setPassedCount(1); s.setTotalCount(2);
        s.setTimeUsedMs(12); s.setMemoryUsedMb(31);
        s.setIsLate(false); s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);

        var cases = testCaseMapper.selectList(new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProblemId, problem.getId()).orderByAsc(TestCase::getId));
        insertDetail(s.getId(), 1, cases.get(0).getId(), "AC", 8L, 30L, null);
        // 隐藏点的 RE message 携带 stderr 尾段——绝不能出现在学生视图
        insertDetail(s.getId(), 2, cases.get(1).getId(), "RE", 3L, 29L,
                "secret path /root/data/" + SECRET_INPUT);
        return s.getId();
    }

    private void insertDetail(long subId, int idx, long tcId, String st,
                              Long timeMs, Long memMb, String msg) {
        JudgeDetail d = new JudgeDetail();
        d.setSubmissionId(subId); d.setTestCaseId(tcId); d.setPointIndex(idx);
        d.setStatus(st); d.setTimeUsedMs(timeMs == null ? null : timeMs.intValue());
        d.setMemoryUsedMb(memMb == null ? null : memMb.intValue()); d.setMessage(msg);
        judgeDetailMapper.insert(d);
    }

    @Test
    void my_list_ordered_desc_with_summary_fields() throws Exception {
        seedJudgedAssignmentSubmission(stuUid);
        seedJudgedAssignmentSubmission(stuUid);   // 第二笔历史（同一 AP 锚）
        mockMvc.perform(get("/api/submissions/my")
                        .header("Authorization", bearer)
                        .param("assignmentProblemId", String.valueOf(ap.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").isNumber())
                .andExpect(jsonPath("$.data[0].status").exists())
                .andExpect(jsonPath("$.data[0].score").exists())
                .andExpect(jsonPath("$.data[0].code").doesNotExist())   // 列表不带代码原文
                .andExpect(jsonPath("$.data[0].isLate").value(false))
                .andExpect(jsonPath("$.data[0].submittedAt").isNotEmpty());
        // id 倒序
        var rows = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, stuUid).orderByDesc(Submission::getId));
        mockMvc.perform(get("/api/submissions/my")
                        .header("Authorization", bearer)
                        .param("assignmentProblemId", String.valueOf(ap.getId())))
                .andExpect(jsonPath("$.data[0].id").value(rows.get(0).getId()));
    }

    @Test
    void my_list_requires_exactly_one_target() throws Exception {
        mockMvc.perform(get("/api/submissions/my")
                        .header("Authorization", bearer)
                        .param("assignmentProblemId", "5")
                        .param("problemId", "6"))
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(get("/api/submissions/my")
                        .header("Authorization", bearer))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void detail_full_common_fields_for_owner() throws Exception {
        long sid = seedJudgedAssignmentSubmission(stuUid);
        mockMvc.perform(get("/api/submissions/" + sid).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("int main(){return 0;}"))
                .andExpect(jsonPath("$.data.status").value("WA"))
                .andExpect(jsonPath("$.data.score").value(0))
                .andExpect(jsonPath("$.data.passedCount").value(1))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.samples.length()").value(1))
                .andExpect(jsonPath("$.data.maskedPoints.length()").value(1))
                .andExpect(jsonPath("$.data.isLate").value(false));
    }

    @Test
    void detail_sample_point_carries_io_but_hidden_point_four_fields_only() throws Exception {
        long sid = seedJudgedAssignmentSubmission(stuUid);
        var body = mockMvc.perform(get("/api/submissions/" + sid)
                        .header("Authorization", bearer)).andReturn()
                .getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        // 零泄漏红线：隐藏点的输入/期望输出/RE消息任何痕迹都不得出现
        assertThat(body).doesNotContain(SECRET_INPUT).doesNotContain(SECRET_EXPECTED);
        assertThat(body).doesNotContain("secret path");
        // 样例点完整 IO（jsonPath 精确断言，规避字段顺序脆弱性）
        assertThat(body).contains("\"samples\"");
        mockMvc.perform(get("/api/submissions/" + sid).header("Authorization", bearer))
                .andExpect(jsonPath("$.data.samples.length()").value(1))
                .andExpect(jsonPath("$.data.samples[0].pointIndex").value(1))
                .andExpect(jsonPath("$.data.samples[0].status").value("AC"))
                .andExpect(jsonPath("$.data.samples[0].input").value("1\n"))
                .andExpect(jsonPath("$.data.samples[0].expectedOutput").value("2\n"))
                // 隐藏点(pointIndex=2)：四字段，结构上不含 input/expectedOutput/message
                .andExpect(jsonPath("$.data.maskedPoints[0].pointIndex").value(2))
                .andExpect(jsonPath("$.data.maskedPoints[0].status").value("RE"))
                .andExpect(jsonPath("$.data.maskedPoints[0].timeUsedMs").value(3))
                .andExpect(jsonPath("$.data.maskedPoints[0].memoryUsedMb").value(29))
                .andExpect(jsonPath("$.data.maskedPoints[0].input").doesNotExist())
                .andExpect(jsonPath("$.data.maskedPoints[0].expectedOutput").doesNotExist())
                .andExpect(jsonPath("$.data.maskedPoints[0].message").doesNotExist());
    }

    @Test
    void other_users_submission_hidden_as_40400() throws Exception {
        long sid = seedJudgedAssignmentSubmission(stuUid);
        User other = new User();
        other.setUsername("stuO" + System.nanoTime());
        other.setPasswordHash(passwordEncoder.encode("pw123456"));
        other.setRealName("旁人"); other.setRole(User.ROLE_STUDENT); other.setEnabled(true);
        userMapper.insert(other);
        String ob = bearer(jwtService, other.getId(), other.getUsername(), other.getRole());
        mockMvc.perform(get("/api/submissions/" + sid).header("Authorization", ob))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void practice_listing_works_by_problem_key() throws Exception {
        Submission s = new Submission();
        s.setProblemId(problem.getId()); s.setUserId(stuUid);
        s.setLanguage("PYTHON"); s.setCode("print(1)");
        s.setStatus(Submission.STATUS_PENDING);
        s.setPassedCount(0); s.setTotalCount(2);
        s.setIsLate(false); s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);
        mockMvc.perform(get("/api/submissions/my")
                        .header("Authorization", bearer)
                        .param("problemId", String.valueOf(problem.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].score").doesNotExist());
    }

    @Test
    void practice_target_rejects_private_problem_as_40400() throws Exception {
        problem.setIsPublic(false);
        problemMapper.updateById(problem);
        mockMvc.perform(get("/api/submissions/my")
                        .header("Authorization", bearer)
                        .param("problemId", String.valueOf(problem.getId())))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void teacher_role_cannot_read_student_read_apis() throws Exception {
        long sid = seedJudgedAssignmentSubmission(stuUid);
        User t = new User();
        t.setUsername("teaR" + System.nanoTime());
        t.setPasswordHash(passwordEncoder.encode("pw123456"));
        t.setRealName("师"); t.setRole(User.ROLE_TEACHER); t.setEnabled(true);
        userMapper.insert(t);
        String tb = bearer(jwtService, t.getId(), t.getUsername(), t.getRole());
        mockMvc.perform(get("/api/submissions/" + sid).header("Authorization", tb))
                .andExpect(status().isForbidden());
    }
}
