package com.mashangping.assignment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 计划10：GET /api/assignments/{id} 角色分流——教师取 TeacherDetail(带 courseName)，
 * 学生取 StudentAssignmentHeader(课程名/作业名/状态/题数)。门禁：已发布 + 已选课，越权 40400。
 */
class AssignmentHeaderApiIT extends IntegrationTestBase {

    @Autowired JwtService jwtService;
    @Autowired UserMapper userMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EnrollmentMapper enrollmentMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired CourseProblemMapper courseProblemMapper;
    @Autowired ProblemMapper problemMapper;

    private long teacherUid;
    private long studentUid;
    private long outsiderUid;
    private long courseId;
    private long assignmentId;

    private long ensureUser(String username, String role, String studentNo) {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (probe != null) {
            return probe.getId();
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("secret123"));
        u.setRealName(username);
        u.setRole(role);
        u.setEnabled(true);
        if (studentNo != null) {
            u.setStudentNo(studentNo);
        }
        userMapper.insert(u);
        return u.getId();
    }

    @BeforeEach
    void seed() {
        teacherUid = ensureUser("hdr_teacher", User.ROLE_TEACHER, null);
        Course c = new Course();
        c.setName("上下文课程");
        c.setTerm("2026-2027-1");
        c.setTeacherId(teacherUid);
        c.setDescription("header IT");
        courseMapper.insert(c);
        courseId = c.getId();

        studentUid = ensureUser("hdr_stu", User.ROLE_STUDENT, "HDR001");
        outsiderUid = ensureUser("hdr_out", User.ROLE_STUDENT, "HDR002");
        Enrollment e = new Enrollment();
        e.setCourseId(courseId);
        e.setStudentId(studentUid);
        e.setStudentNo("HDR001");
        e.setStudentName("同学A");
        e.setStatus(Enrollment.STATUS_ACTIVE);
        enrollmentMapper.insert(e);

        Problem p = new Problem();
        p.setTeacherId(teacherUid);
        p.setTitle("上下文题");
        p.setDescription("A+B");
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);

        CourseProblem cp = new CourseProblem();
        cp.setCourseId(courseId);
        cp.setProblemId(p.getId());
        courseProblemMapper.insert(cp);

        Assignment a = new Assignment();
        a.setCourseId(courseId);
        a.setTitle("上下文作业");
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setDueAt(LocalDateTime.now().plusDays(2));
        a.setLateDays(2);
        a.setIsPublished(true);
        assignmentMapper.insert(a);
        assignmentId = a.getId();

        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(p.getId());
        ap.setScore(100);
        ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);
    }

    @Test
    void teacher_detail_includes_course_name() throws Exception {
        mockMvc.perform(get("/api/assignments/{id}", assignmentId)
                        .header("Authorization", bearer(jwtService, teacherUid, "hdr_teacher", "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(assignmentId))
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.courseName").value("上下文课程"))
                .andExpect(jsonPath("$.data.title").value("上下文作业"));
    }

    @Test
    void student_header_returns_context_with_course_name_status_and_count() throws Exception {
        mockMvc.perform(get("/api/assignments/{id}", assignmentId)
                        .header("Authorization", bearer(jwtService, studentUid, "hdr_stu", "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.courseName").value("上下文课程"))
                .andExpect(jsonPath("$.data.assignmentId").value(assignmentId))
                .andExpect(jsonPath("$.data.title").value("上下文作业"))
                .andExpect(jsonPath("$.data.problemCount").value(1))
                .andExpect(jsonPath("$.data.dueAt").exists())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void student_not_enrolled_gets_40400() throws Exception {
        mockMvc.perform(get("/api/assignments/{id}", assignmentId)
                        .header("Authorization", bearer(jwtService, outsiderUid, "hdr_out", "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void student_cannot_read_unpublished_assignment() throws Exception {
        Assignment a = assignmentMapper.selectById(assignmentId);
        a.setIsPublished(false);
        assignmentMapper.updateById(a);
        mockMvc.perform(get("/api/assignments/{id}", assignmentId)
                        .header("Authorization", bearer(jwtService, studentUid, "hdr_stu", "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }
}
