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
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionEntryTest extends IntegrationTestBase {

    @Autowired UserMapper userMapper; @Autowired JwtService jwtService;
    @Autowired CourseMapper courseMapper; @Autowired EnrollmentMapper enrollmentMapper;
    @Autowired ProblemMapper problemMapper; @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired SubmissionMapper submissionMapper;

    long stuUid; String bearer; Problem problem;

    /** 落库真实用户并签发可用 JWT（JWT 实时吊销铁律） */
    @BeforeEach
    void seed() {
        User u = new User();
        u.setUsername("stuS" + System.nanoTime());
        u.setPasswordHash(passwordEncoder.encode("pw123456"));
        u.setRealName("测试生");
        u.setRole(User.ROLE_STUDENT);
        u.setEnabled(true);
        userMapper.insert(u);
        stuUid = u.getId();
        bearer = bearer(jwtService, stuUid, u.getUsername(), u.getRole());

        problem = new Problem();
        problem.setTeacherId(9000L);
        problem.setTitle("双锚题");
        problem.setDescription("desc");
        problem.setTimeLimitMs(1000);
        problem.setMemoryLimitMb(256);
        problem.setIsPublic(true);
        problemMapper.insert(problem);
    }

    private String practiceJson(long pid, String language, String code) {
        return "{\"problemId\":" + pid + ",\"language\":\"" + language + "\",\"code\":\""
                + code.replace("\n", "\\n").replace("\"", "\\\"") + "\"}";
    }

    private String assignmentJson(long apId, String language, String code) {
        return "{\"assignmentProblemId\":" + apId + ",\"language\":\"" + language
                + "\",\"code\":\"" + code.replace("\n", "\\n") + "\"}";
    }

    /** 铺一条 作业=进行中+已发布 的完整链：course→enrollment→assignment(is_pub,due +3d)→ap(score 7)，返回 apId */
    private long seedAssignmentChain(LocalDateTime startAt, LocalDateTime dueAt,
                                     int lateDays, boolean published) {
        Course c = new Course();
        c.setName("课" + System.nanoTime());
        c.setTerm("2025-2026-1");
        c.setTeacherId(9001L);
        courseMapper.insert(c);
        Enrollment e = new Enrollment();
        e.setCourseId(c.getId());
        e.setStudentId(stuUid);
        e.setStudentNo("S" + stuUid);   // NOT NULL 身份锚点（简报漏列，按 schema 补齐）
        e.setStudentName("测试生");      // NOT NULL（简报漏列，按 schema 补齐）
        e.setStatus(Enrollment.STATUS_ACTIVE);
        enrollmentMapper.insert(e);
        Assignment a = new Assignment();
        a.setCourseId(c.getId());
        a.setTitle("作业A");
        a.setDescription("d");
        a.setStartAt(startAt);
        a.setDueAt(dueAt);
        a.setLateDays(lateDays);
        a.setIsPublished(published);
        assignmentMapper.insert(a);
        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(a.getId());
        ap.setProblemId(problem.getId());
        ap.setScore(7);
        ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);
        return ap.getId();
    }

    private void assertOnePendingRow(boolean withApAnchor) {
        var rows = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, stuUid));
        org.assertj.core.api.Assertions.assertThat(rows).hasSize(1);
        Submission s = rows.get(0);
        org.assertj.core.api.Assertions.assertThat(s.getStatus()).isEqualTo(Submission.STATUS_PENDING);
        org.assertj.core.api.Assertions.assertThat(s.getScore()).isNull();
        org.assertj.core.api.Assertions.assertThat(s.getTotalCount()).isZero();
        if (withApAnchor) {
            org.assertj.core.api.Assertions.assertThat(s.getAssignmentId()).isNotNull();
            org.assertj.core.api.Assertions.assertThat(s.getAssignmentProblemId()).isNotNull();
            org.assertj.core.api.Assertions.assertThat(s.getIsLate()).isFalse();
        } else {
            org.assertj.core.api.Assertions.assertThat(s.getAssignmentId()).isNull();
            org.assertj.core.api.Assertions.assertThat(s.getAssignmentProblemId()).isNull();
        }
    }

    /** 本用例（当前学生）零落行——按 user 收窄，避免污染容器跨测试残留行 */
    private long myRowCount() {
        return submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, stuUid));
    }

    @Test
    void practice_submission_creates_pending_rows() throws Exception {
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(practiceJson(problem.getId(), "CPP", "int main(){return 0;}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
        assertOnePendingRow(false);
    }

    @Test
    void assignment_submission_in_progress_marks_not_late() throws Exception {
        long apId = seedAssignmentChain(LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(3), 0, true);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apId, "JAVA", "public class Main{}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertOnePendingRow(true);
    }

    @Test
    void late_window_submission_marked_late() throws Exception {
        long apId = seedAssignmentChain(LocalDateTime.now().minusDays(3),
                LocalDateTime.now().minusHours(1), 2, true);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apId, "C", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(0));
        var rows = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getUserId, stuUid));
        org.assertj.core.api.Assertions.assertThat(rows.get(0).getIsLate()).isTrue();
    }

    @Test
    void not_started_assignment_rejected_40300_and_no_row() throws Exception {
        long apId = seedAssignmentChain(LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(3), 0, true);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apId, "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(40300));
        org.assertj.core.api.Assertions.assertThat(myRowCount()).isZero();
    }

    @Test
    void closed_assignment_rejected_40300_even_with_late_days_elapsed() throws Exception {
        long apId = seedAssignmentChain(LocalDateTime.now().minusDays(9),
                LocalDateTime.now().minusDays(3), 2, true);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apId, "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(40300));
        org.assertj.core.api.Assertions.assertThat(myRowCount()).isZero();
    }

    @Test
    void unpublished_assignment_hidden_as_40400() throws Exception {
        long apId = seedAssignmentChain(LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(3), 0, false);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apId, "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void not_enrolled_course_hidden_as_40400() throws Exception {
        // 同结构但 enrollment 缺席 → 绝对不能出现旧行，且 40400
        long apId = seedAssignmentChain(LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(3), 0, true);
        enrollmentMapper.delete(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, stuUid));
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apId, "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void private_practice_problem_hidden_as_40400() throws Exception {
        problem.setIsPublic(false);
        problemMapper.updateById(problem);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(practiceJson(problem.getId(), "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void both_or_neither_target_keys_rejected_40000() throws Exception {
        long apId = seedAssignmentChain(LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(3), 0, true);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"problemId\":" + problem.getId()
                                + ",\"assignmentProblemId\":" + apId
                                + ",\"language\":\"CPP\",\"code\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(40000));
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"CPP\",\"code\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void disallowed_language_rejected_40000_and_no_row() throws Exception {
        problem.setAllowedLanguages("C");
        problemMapper.updateById(problem);
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(practiceJson(problem.getId(), "PYTHON", "print(1)")))
                .andExpect(jsonPath("$.code").value(40000));
        org.assertj.core.api.Assertions.assertThat(myRowCount()).isZero();
    }

    @Test
    void rate_limit_same_target_within_10s_gives_40018_but_other_target_free() throws Exception {
        long apA = seedAssignmentChain(LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(3), 0, true);
        // 第一次作业路径成功
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apA, "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(0));
        // 同目标 10 秒内再交 → 40018
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentJson(apA, "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(40018));
        // 另一目标（练习同题）不受该限流影响 → 0
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(practiceJson(problem.getId(), "CPP", "int main(){return 0;}")))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void oversized_code_rejected_40019() throws Exception {
        StringBuilder sb = new StringBuilder("//");
        sb.append("x".repeat(70000));  // >64KB
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(practiceJson(problem.getId(), "CPP", sb.toString())))
                .andExpect(jsonPath("$.code").value(40019));
        org.assertj.core.api.Assertions.assertThat(myRowCount()).isZero();
    }

    @Test
    void teacher_role_forbidden_to_submit() throws Exception {
        User t = new User();
        t.setUsername("teaS" + System.nanoTime());
        t.setPasswordHash(passwordEncoder.encode("pw123456"));
        t.setRealName("老师");
        t.setRole(User.ROLE_TEACHER);
        t.setEnabled(true);
        userMapper.insert(t);
        String tb = bearer(jwtService, t.getId(), t.getUsername(), t.getRole());
        mockMvc.perform(post("/api/submissions")
                        .header("Authorization", tb)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(practiceJson(problem.getId(), "CPP", "int main(){return 0;}")))
                .andExpect(status().isForbidden());
    }
}
