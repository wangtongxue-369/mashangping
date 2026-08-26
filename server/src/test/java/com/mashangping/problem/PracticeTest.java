package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.security.JwtService;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PracticeTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private ProblemMapper problemMapper;
    @Autowired private TestCaseMapper testCaseMapper;

    private long uidStu;
    private long uidTeacher;

    @BeforeEach
    void seed() {
        // 学号避开 ExcelImportTest 已占用的 20268101（uk_user_student_no 全库唯一，跨类共享容器）
        uidStu = ensureUser("pr_s_a", "STUDENT", "20268301", "练同学");
        uidTeacher = ensureUser("pr_t_b", "TEACHER", null, "题老师");
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

    private String student() { return bearer(jwtService, uidStu, "pr_s_a", "STUDENT"); }
    private String teacher() { return bearer(jwtService, uidTeacher, "pr_t_b", "TEACHER"); }

    private long createProblem(boolean isPublic, String title) {
        Problem p = new Problem();
        p.setTeacherId(uidTeacher);
        p.setTitle(title);
        p.setDescription("# " + title + " 的题面");
        p.setIsPublic(isPublic);
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        return p.getId();
    }

    private void createCase(long pid, String input, String output, boolean sample) {
        TestCase tc = new TestCase();
        tc.setProblemId(pid);
        tc.setInput(input);
        tc.setExpectedOutput(output);
        tc.setIsSample(sample);
        testCaseMapper.insert(tc);
    }

    @Test
    void list_returns_only_public_problems_without_description() throws Exception {
        long pub = createProblem(true, "公开题甲");
        createProblem(false, "私有题乙");

        // 单例容器跨用例共享数据：用唯一标题关键词圈定本用例数据，避免全局 total 受其他公开题污染
        mockMvc.perform(get("/api/practice/problems?keyword=公开题甲").header("Authorization", student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) pub))
                .andExpect(jsonPath("$.data.records[0].description").doesNotExist());

        mockMvc.perform(get("/api/practice/problems?keyword=私有").header("Authorization", student()))
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void detail_shows_samples_completely_and_never_hidden_cases() {
        long pid = createProblem(true, "样例展示题");
        createCase(pid, "1 2\n", "3\n", true);
        createCase(pid, "秘密输入\n", "秘密输出\n", false);
        createCase(pid, "4 5\n", "9\n", true);

        String resp;
        try {
            resp = mockMvc.perform(get("/api/practice/problems/" + pid).header("Authorization", student()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.samples.length()").value(2)) // 仅样例点
                    .andExpect(jsonPath("$.data.samples[0].input").value("1 2\n"))
                    .andExpect(jsonPath("$.data.samples[1].output").value("9\n"))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // 隐藏点内容零泄漏：整个响应中不得出现隐藏点的任何文本
        assertThat(resp).doesNotContain("秘密输入").doesNotContain("秘密输出");
    }

    @Test
    void private_or_missing_problem_hidden_as_40400_for_students() throws Exception {
        long priv = createProblem(false, "学生不可见");
        mockMvc.perform(get("/api/practice/problems/" + priv).header("Authorization", student()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
        mockMvc.perform(get("/api/practice/problems/999999999").header("Authorization", student()))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void teacher_can_also_browse_practice_library() throws Exception {
        createProblem(true, "教师也浏览");
        // 关键词圈定（同 list 用例）：验证教师身份可浏览公开题库，不依赖全局 total
        mockMvc.perform(get("/api/practice/problems?keyword=教师也浏览").header("Authorization", teacher()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));
    }
}
