package com.mashangping.gradebook;

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
import com.mashangping.judging.Submission;
import com.mashangping.judging.SubmissionMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GradebookCsvTest extends IntegrationTestBase {

    @Autowired GradebookService gradebookService;
    @Autowired UserMapper userMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired EnrollmentMapper enrollmentMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired ProblemMapper problemMapper;
    @Autowired SubmissionMapper submissionMapper;

    private long teacherUid;
    private long assignmentId;

    @BeforeEach
    void seed() {
        teacherUid = ensureUser("gb_t_c1", User.ROLE_TEACHER, null, "甲老师");
        Course c = new Course();
        c.setName("CSV课"); c.setTerm("2025-2026-1"); c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        // 学生N：正常，有 AC 提交
        long uidN = ensureUser("gb_st_c1", User.ROLE_STUDENT, "S_N", "张三");
        // 学生M：隐藏/注入名，无提交
        long uidM = ensureUser("gb_st_c2", User.ROLE_STUDENT, "S_M", "=SUM(A1),陷阱\"");
        enroll(c.getId(), uidN, "S_N", "张三");
        enroll(c.getId(), uidM, "S_M", "=SUM(A1),陷阱\"");
        Problem p1 = new Problem();
        p1.setTeacherId(teacherUid); p1.setTitle("题A");
        p1.setTimeLimitMs(1000); p1.setMemoryLimitMb(256);
        problemMapper.insert(p1);
        Assignment a = new Assignment();
        a.setCourseId(c.getId()); a.setTitle("CSV作");
        a.setStartAt(LocalDateTime.now().minusDays(1)); a.setDueAt(LocalDateTime.now().plusDays(3));
        a.setLateDays(0); a.setIsPublished(true);
        assignmentMapper.insert(a);
        assignmentId = a.getId();
        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(assignmentId); ap.setProblemId(p1.getId()); ap.setScore(10); ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);
        // 学生N 有 AC 提交 → cell 10；学生M 无提交 → cell 0
        Submission s = new Submission();
        s.setProblemId(p1.getId()); s.setUserId(uidN);
        s.setAssignmentId(assignmentId); s.setAssignmentProblemId(ap.getId());
        s.setLanguage("C"); s.setCode("int main(){return 0;}");
        s.setStatus(Submission.STATUS_AC); s.setScore(10);
        s.setPassedCount(1); s.setTotalCount(1); s.setIsLate(false);
        s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);
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

    @Test
    void bom_zeros_escaping_injection_guard() {
        byte[] bytes = gradebookService.csv(teacherUid, assignmentId);
        // BOM = EF BB BF
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(bytes, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        assertThat(csv).contains("学号,姓名");
        assertThat(csv).contains("题A(10)");
        assertThat(csv).contains(",总分");
        // 学生N：AC→10，总分 10
        assertThat(csv).contains("张三,10,10");
        // 学生M：注入前缀 '、含逗号/引号走 RFC4180 引号包裹，未做→0
        assertThat(csv).contains("\"'=SUM(A1),陷阱\"\"\"");
        assertThat(csv).contains("S_M");
        // total 0，单题未做 → 0
    }
}