package com.mashangping;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.security.JwtService;
import com.mashangping.security.TokenPayload;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * multipart 超限映射 40014 的真实容器测试。
 * MockMvc 的 Mock 请求不经 Servlet 容器的 multipart 解析，实测不会触发
 * MaxUploadSizeExceededException（超限文件会原样进到业务层），故此处起
 * 真实 Tomcat（RANDOM_PORT）走完整 HTTP 栈，验证
 * spring.servlet.multipart.max-file-size 的强制拦截与异常到 40014 的映射。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class MultipartOversizeIntegrationTest {

    /** 复用 IntegrationTestBase 的手动单例容器，避免同一 JVM 起双容器 */
    static final MySQLContainer<?> MYSQL = IntegrationTestBase.MYSQL;

    static {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            MYSQL.start();
        }
    }

    @BeforeAll
    static void requireDocker() {
        Assumptions.assumeTrue(MYSQL.isRunning(), "本机无 Docker，跳过数据库集成测试");
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;

    @Test
    void oversize_multipart_maps_to_40014_over_real_http() throws Exception {
        User teacher = ensureUser("mp_teacher", "TEACHER", null, "超限老师");
        Course c = new Course();
        c.setName("超限导入课"); c.setTerm("2025-2026-1"); c.setTeacherId(teacher.getId());
        courseMapper.insert(c);

        byte[] xlsx = inflatedXlsxOver1MB();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(xlsx) {
            @Override
            public String getFilename() { return "students.xlsx"; }
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(jwtService.generate(
                new TokenPayload(teacher.getId(), teacher.getUsername(), "TEACHER")));

        ResponseEntity<String> resp = restTemplate.postForEntity(
                "/api/courses/" + c.getId() + "/students/import",
                new HttpEntity<>(body, headers), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        // 判别性断言：文件本身合法、表头正确且仅 61 行（远低于服务层 500 行上限），
        // 若大小限制未生效，导入本应成功返回 code=0；
        // 因此这里的 40014 只可能来自传输层 multipart 超限拦截 → 异常映射，
        // 与 ExcelImportTest 的 501 行用例（服务层行数上限路径）互不覆盖
        assertThat(resp.getBody()).contains("\"code\":40014");
    }

    /** 合法两列 xlsx：表头正确、共 61 行，用高熵随机长姓名把字节撑过 1MB（随机串压缩率低） */
    private byte[] inflatedXlsxOver1MB() {
        SecureRandom random = new SecureRandom();
        String symbols = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("学号", "姓名"));
        for (int i = 0; i < 60; i++) {
            StringBuilder sb = new StringBuilder(30_000);
            for (int j = 0; j < 30_000; j++) {
                sb.append(symbols.charAt(random.nextInt(symbols.length())));
            }
            rows.add(List.of("20269" + String.format("%03d", i), sb.toString()));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out).sheet("Sheet1").doWrite(rows);
        byte[] bytes = out.toByteArray();
        assertThat(bytes.length).isGreaterThan(1024 * 1024);
        return bytes;
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

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
}
