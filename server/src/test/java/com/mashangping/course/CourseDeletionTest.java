package com.mashangping.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseDeletionTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private EnrollmentMapper enrollmentMapper;
    @Autowired private ProblemMapper problemMapper;
    @Autowired private CourseProblemMapper courseProblemMapper;
    @Autowired private AssignmentMapper assignmentMapper;
    @Autowired private AssignmentProblemMapper assignmentProblemMapper;

    private long uidA;
    private long uidB;
    private long courseIdA;
    private long courseIdB;
    private long pidA;
    private long assignmentIdA;

    @BeforeEach
    void seed() {
        uidA = ensureUser("cdel_t_a", "TEACHER", null, "甲老师");
        uidB = ensureUser("cdel_t_b", "TEACHER", null, "乙老师");
        courseIdA = insertCourse("待删课", uidA);
        courseIdB = insertCourse("保留课", uidB);

        // 课程 A 全链路数据：名单 + 选题 + 作业 + 作业题
        Enrollment e = new Enrollment();
        e.setCourseId(courseIdA);
        e.setStudentNo("20268881");
        e.setStudentName("删课生");
        e.setStatus(Enrollment.STATUS_ACTIVE);
        enrollmentMapper.insert(e);

        Problem p = new Problem();
        p.setTeacherId(uidA);
        p.setTitle("被引题");
        p.setDescription("d");
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        pidA = p.getId();

        CourseProblem cp = new CourseProblem();
        cp.setCourseId(courseIdA);
        cp.setProblemId(pidA);
        cp.setSortOrder(1);
        courseProblemMapper.insert(cp);

        Assignment a = new Assignment();
        a.setCourseId(courseIdA);
        a.setTitle("被删作业");
        a.setStartAt(LocalDateTime.now().plusDays(1));
        a.setDueAt(LocalDateTime.now().plusDays(2));
        a.setLateDays(0);
        a.setIsPublished(false);
        assignmentMapper.insert(a);
        assignmentIdA = a.getId();

        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentIdA);
        ap.setProblemId(pidA);
        ap.setScore(10);
        ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);
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

    private String teacherA() { return bearer(jwtService, uidA, "cdel_t_a", "TEACHER"); }
    private String teacherB() { return bearer(jwtService, uidB, "cdel_t_b", "TEACHER"); }

    private long insertCourse(String name, long teacherUid) {
        Course c = new Course();
        c.setName(name);
        c.setTerm("2025-2026-1");
        c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        return c.getId();
    }

    @Test
    void delete_course_cascades_all_five_tables() throws Exception {
        mockMvc.perform(delete("/api/courses/" + courseIdA)
                        .header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertThat(courseMapper.selectById(courseIdA)).isNull();
        Long enroll = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseIdA));
        assertThat(enroll).isZero();
        Long cps = courseProblemMapper.selectCount(new LambdaQueryWrapper<CourseProblem>()
                .eq(CourseProblem::getCourseId, courseIdA));
        assertThat(cps).isZero();
        Long asgn = assignmentMapper.selectCount(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getCourseId, courseIdA));
        assertThat(asgn).isZero();
        Long aps = assignmentProblemMapper.selectCount(new LambdaQueryWrapper<AssignmentProblem>()
                .eq(AssignmentProblem::getAssignmentId, assignmentIdA));
        assertThat(aps).isZero();
        // 题目本体是教师资产，不随课删除
        assertThat(problemMapper.selectById(pidA)).isNotNull();
        // 其他课程不受影响
        assertThat(courseMapper.selectById(courseIdB)).isNotNull();
    }

    @Test
    void non_owner_delete_rejected_40400_and_nothing_deleted() throws Exception {
        mockMvc.perform(delete("/api/courses/" + courseIdA)
                        .header("Authorization", teacherB()))
                .andExpect(jsonPath("$.code").value(40400));
        assertThat(courseMapper.selectById(courseIdA)).isNotNull();
    }

    @Test
    void delete_course_without_assignments_works() throws Exception {
        mockMvc.perform(delete("/api/courses/" + courseIdB)
                        .header("Authorization", teacherB()))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(courseMapper.selectById(courseIdB)).isNull();
    }
}
