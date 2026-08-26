package com.mashangping.course;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExcelImportTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private EnrollmentMapper enrollmentMapper;

    private long courseId;
    private String teacherBearer;
    private File tempXlsx;

    @BeforeEach
    void seed() throws Exception {
        User teacher = ensureUser("imp_teacher", "TEACHER", null, "导老师");
        User existStu = ensureUser("imp_exist_stu", "STUDENT", "20268101", "已注册生");
        Course c = new Course();
        c.setName("导入测试课"); c.setTerm("2025-2026-1"); c.setTeacherId(teacher.getId());
        courseMapper.insert(c);
        courseId = c.getId();
        teacherBearer = bearer(jwtService, teacher.getId(), teacher.getUsername(), "TEACHER");
        tempXlsx = File.createTempFile("import", ".xlsx");
    }

    @AfterEach
    void cleanup() {
        if (tempXlsx != null && tempXlsx.exists()) {
            assertThat(tempXlsx.delete()).isTrue();
        }
    }

    private User ensureUser(String username, String role, String studentNo, String realName) {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (probe != null) {
            return probe;
        }
        User u = new User();
        u.setUsername(username); u.setPasswordHash(passwordEncoder.encode("secret66"));
        u.setRealName(realName); u.setRole(role); u.setStudentNo(studentNo); u.setEnabled(true);
        userMapper.insert(u);
        return u;
    }

    /** 用 EasyExcel 运行时生成两列 xlsx：首行表头 + 数据行 */
    private MockMultipartFile buildFile(List<List<String>> header, List<List<String>> rows)
            throws Exception {
        try (var out = Files.newOutputStream(tempXlsx.toPath())) {
            EasyExcel.write(out).sheet("Sheet1").doWrite(headerAndRows(header, rows));
        }
        byte[] bytes = Files.readAllBytes(tempXlsx.toPath());
        return new MockMultipartFile("file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private List<List<String>> headerAndRows(List<List<String>> header, List<List<String>> rows) {
        List<List<String>> all = new ArrayList<>(header);
        all.addAll(rows);
        return all;
    }

    private static final List<List<String>> HEADER =
            List.of(List.of("学号", "姓名"));

    @Test
    void mixed_import_partial_success() throws Exception {
        List<List<String>> rows = List.of(
                List.of("20268101", "已注册生"),          // → ACTIVATED（账号已存在）
                List.of("20268102", "待激活甲"),           // → PENDING
                List.of("20268101", "文件内重复"),          // → SKIPPED
                List.of("bad no!", "格式错"),               // → FAILED
                List.of("", "空学号"),                      // → FAILED
                List.of("20268103", "")                    // → FAILED（姓名空）
        );
        MockMultipartFile file = buildFile(HEADER, rows);

        mockMvc.perform(multipart("/api/courses/" + courseId + "/students/import")
                                .file(file).header("Authorization", teacherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalRows").value(6))
                .andExpect(jsonPath("$.data.activated").value(1))
                .andExpect(jsonPath("$.data.pending").value(1))
                .andExpect(jsonPath("$.data.skipped").value(1))
                .andExpect(jsonPath("$.data.failures.length()").value(3));

        assertThat(enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId)
                .eq(Enrollment::getStatus, Enrollment.STATUS_ACTIVE))).isEqualTo(1);
        assertThat(enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId)
                .eq(Enrollment::getStatus, Enrollment.STATUS_PENDING))).isEqualTo(1);
    }

    @Test
    void wrong_header_rejected_40014() throws Exception {
        MockMultipartFile file = buildFile(List.of(List.of("编号", "名字")),
                List.of(List.of("20268201", "某人")));

        mockMvc.perform(multipart("/api/courses/" + courseId + "/students/import")
                                .file(file).header("Authorization", teacherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40014));
    }

    @Test
    void oversize_file_rejected_40014() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            rows.add(List.of("20269" + String.format("%04d", i), "生" + i));
        }
        MockMultipartFile file = buildFile(HEADER, rows);

        mockMvc.perform(multipart("/api/courses/" + courseId + "/students/import")
                                .file(file).header("Authorization", teacherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40014));
    }

    @Test
    void non_xlsx_extension_rejected_40014() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "students.csv",
                "text/csv", "学号,姓名\n20268301,张三".getBytes());

        mockMvc.perform(multipart("/api/courses/" + courseId + "/students/import")
                                .file(file).header("Authorization", teacherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40014));
    }

    @Test
    void foreign_teacher_cannot_import_40400() throws Exception {
        User strangerUser = ensureUser("imp_other", "TEACHER", null, "外课老师");
        String stranger = bearer(jwtService, strangerUser.getId(), "imp_other", "TEACHER");
        MockMultipartFile file = buildFile(HEADER, List.of(List.of("20268401", "某")));
        mockMvc.perform(multipart("/api/courses/" + courseId + "/students/import")
                                .file(file).header("Authorization", stranger))
                .andExpect(jsonPath("$.code").value(40400));
    }
}
