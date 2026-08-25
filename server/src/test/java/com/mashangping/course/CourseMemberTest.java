package com.mashangping.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseMemberTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private EnrollmentMapper enrollmentMapper;

    private long teacherUid;
    private long courseId;
    private String teacherBearer;
    private String studentBearer;

    @BeforeEach
    void seed() {
        User teacher = ensureUser("mem_teacher", "TEACHER", null, "陈老师");
        User student = ensureUser("mem_student", "STUDENT", "20267001", "周同学");
        teacherUid = teacher.getId();
        // 测试库无回滚：清掉此前用例为共享学生留下的选课行，保证 /my 断言只看本用例
        enrollmentMapper.delete(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, student.getId()));

        Course c = new Course();
        c.setName("成员管理测试课");
        c.setTerm("2025-2026-1");
        c.setTeacherId(teacherUid);
        courseMapper.insert(c);
        courseId = c.getId();

        teacherBearer = bearer(jwtService, teacher.getId(), teacher.getUsername(), "TEACHER");
        studentBearer = bearer(jwtService, student.getId(), student.getUsername(), "STUDENT");
    }

    private User ensureUser(String username, String role, String studentNo, String realName) {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (probe != null) {
            return probe;
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode("secret66"));
        u.setRealName(realName);
        u.setRole(role);
        u.setStudentNo(studentNo);
        u.setEnabled(true);
        userMapper.insert(u);
        return u;
    }

    private String addBody(String no, String name) {
        return "{\"studentNo\":\"" + no + "\",\"studentName\":\"" + name + "\"}";
    }

    @Test
    void adding_registered_student_activates_immediately() throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267001", "任意快照名")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.displayName").value("周同学")); // 展示以账号实名

        Enrollment row = enrollmentMapper.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId));
        assertThat(row.getStatus()).isEqualTo(Enrollment.STATUS_ACTIVE);
        assertThat(row.getStudentId()).isNotNull();
    }

    @Test
    void adding_unknown_student_stores_pending_snapshot() throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267999", "预留同学")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.displayName").value("预留同学"));

        Enrollment row = enrollmentMapper.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId));
        assertThat(row.getStudentId()).isNull();
    }

    @Test
    void duplicate_addition_rejected_40401() throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267001", "周同学")))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267001", "周同学")))
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    void non_student_account_number_rejected() throws Exception {
        ensureUser("mem_teacher2", "TEACHER", "99980001", "隔壁老师");
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("99980001", "隔壁老师")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("该学号对应非学生账号"));
    }

    @Test
    void list_filters_by_status_and_keyword_matching_realname() throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267001", "占位名")))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267999", "王待激活")))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/courses/" + courseId + "/students?status=PENDING")
                        .header("Authorization", teacherBearer))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].displayName").value("王待激活"));

        // keyword 命中账号实名（ACTIVE 行的 realName 路径）
        mockMvc.perform(get("/api/courses/" + courseId + "/students?keyword=周同学")
                        .header("Authorization", teacherBearer))
                .andExpect(jsonPath("$.data.total").value(1));

        // keyword 命中学号
        mockMvc.perform(get("/api/courses/" + courseId + "/students?keyword=20267999")
                        .header("Authorization", teacherBearer))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void remove_works_for_both_states_and_wrong_course_is_40400() throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267001", "周同学")))
                .andExpect(jsonPath("$.code").value(0));
        Enrollment row = enrollmentMapper.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId));

        mockMvc.perform(delete("/api/courses/" + courseId + "/students/" + row.getId())
                        .header("Authorization", teacherBearer))
                .andExpect(jsonPath("$.code").value(0));
        assertThat(enrollmentMapper.selectById(row.getId())).isNull();

        // 成员挂到别的课再删：courseId 不匹配 → 40400
        Course other = new Course();
        other.setName("别的课"); other.setTerm("2025-2026-1"); other.setTeacherId(teacherUid);
        courseMapper.insert(other);
        Enrollment foreign = new Enrollment();
        foreign.setCourseId(other.getId()); foreign.setStudentNo("20267123");
        foreign.setStudentName("外课成员"); foreign.setStatus(Enrollment.STATUS_PENDING);
        enrollmentMapper.insert(foreign);
        mockMvc.perform(delete("/api/courses/" + courseId + "/students/" + foreign.getId())
                        .header("Authorization", teacherBearer))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void student_sees_only_own_active_courses_in_my_list() throws Exception {
        mockMvc.perform(post("/api/courses/" + courseId + "/students")
                        .header("Authorization", teacherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addBody("20267001", "周同学")))
                .andExpect(jsonPath("$.code").value(0));
        // 再造一门未加入的课
        Course other = new Course();
        other.setName("没进的课"); other.setTerm("2025-2026-1"); other.setTeacherId(teacherUid);
        courseMapper.insert(other);

        mockMvc.perform(get("/api/courses/my").header("Authorization", studentBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].courseId").value(courseId))
                .andExpect(jsonPath("$.data[0].name").value("成员管理测试课"));

        // 待激活状态不应出现在我的课程里
        Enrollment pendingOnly = enrollmentMapper.selectOne(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId));
        assertThat(pendingOnly.getStatus()).isEqualTo(Enrollment.STATUS_ACTIVE);
    }

    @Test
    void foreign_teacher_cannot_touch_member_endpoints_40400() throws Exception {
        User otherTeacher = ensureUser("mem_other_t", "TEACHER", null, "外课老师");
        String otherBearer = bearer(jwtService, otherTeacher.getId(), "mem_other_t", "TEACHER");
        mockMvc.perform(get("/api/courses/" + courseId + "/students")
                        .header("Authorization", otherBearer))
                .andExpect(jsonPath("$.code").value(40400));
    }
}
