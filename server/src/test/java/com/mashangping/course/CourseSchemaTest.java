package com.mashangping.course;

import com.mashangping.IntegrationTestBase;
import com.mashangping.emailverify.EmailVerification;
import com.mashangping.emailverify.EmailVerificationMapper;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CourseSchemaTest extends IntegrationTestBase {

    @Autowired private CourseMapper courseMapper;
    @Autowired private EnrollmentMapper enrollmentMapper;
    @Autowired private EmailVerificationMapper emailVerificationMapper;
    @Autowired private UserMapper userMapper;

    private Course newCourse(Long teacherId, String name) {
        Course c = new Course();
        c.setName(name);
        c.setTerm("2025-2026-1");
        c.setTeacherId(teacherId);
        return c;
    }

    @Test
    void course_insert_and_readback() {
        Course c = newCourse(9001L, "Java程序设计");
        c.setDescription("面向对象入门");
        courseMapper.insert(c);
        assertThat(c.getId()).isNotNull();
        Course found = courseMapper.selectById(c.getId());
        assertThat(found.getTerm()).isEqualTo("2025-2026-1");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void enrollment_unique_per_course_but_reusable_across_courses() {
        Course c1 = newCourse(9002L, "课一");
        Course c2 = newCourse(9002L, "课二");
        courseMapper.insert(c1);
        courseMapper.insert(c2);

        Enrollment e = new Enrollment();
        e.setCourseId(c1.getId());
        e.setStudentNo("2026001");
        e.setStudentName("张三");
        e.setStatus(Enrollment.STATUS_PENDING);
        enrollmentMapper.insert(e);
        assertThat(e.getId()).isNotNull();

        // 同课同学号：唯一约束拒绝
        Enrollment dup = new Enrollment();
        dup.setCourseId(c1.getId());
        dup.setStudentNo("2026001");
        dup.setStudentName("张三");
        dup.setStatus(Enrollment.STATUS_PENDING);
        assertThrows(DuplicateKeyException.class, () -> enrollmentMapper.insert(dup));

        // 另一门课同学号：允许
        Enrollment other = new Enrollment();
        other.setCourseId(c2.getId());
        other.setStudentNo("2026001");
        other.setStudentName("张三");
        other.setStatus(Enrollment.STATUS_PENDING);
        enrollmentMapper.insert(other);
        assertThat(other.getId()).isNotNull();
    }

    @Test
    void user_student_no_unique_among_students_null_allowed_for_teachers() {
        User s1 = new User();
        s1.setUsername("uniq_s1"); s1.setPasswordHash("h"); s1.setRealName("甲");
        s1.setStudentNo("7770001"); s1.setRole(User.ROLE_STUDENT); s1.setEnabled(true);
        userMapper.insert(s1);

        User s2 = new User();
        s2.setUsername("uniq_s2"); s2.setPasswordHash("h"); s2.setRealName("乙");
        s2.setStudentNo("7770001"); s2.setRole(User.ROLE_STUDENT); s2.setEnabled(true);
        assertThrows(DuplicateKeyException.class, () -> userMapper.insert(s2));

        User t1 = new User();
        t1.setUsername("uniq_t1"); t1.setPasswordHash("h"); t1.setRealName("师");
        t1.setRole(User.ROLE_TEACHER); t1.setEnabled(true); // studentNo 为 NULL
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> userMapper.insert(t1));
    }

    @Test
    void emailverification_crud() {
        EmailVerification v = new EmailVerification();
        v.setEmail("a@stu.example.edu.cn");
        v.setCode("123456");
        v.setPurpose("REGISTER");
        v.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        v.setUsed(false);
        emailVerificationMapper.insert(v);
        assertThat(v.getId()).isNotNull();

        v.setUsed(true);
        emailVerificationMapper.updateById(v);
        assertThat(emailVerificationMapper.selectById(v.getId()).getUsed()).isTrue();
    }
}
