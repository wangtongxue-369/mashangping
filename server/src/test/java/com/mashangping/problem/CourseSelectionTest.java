package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
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

class CourseSelectionTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private ProblemMapper problemMapper;
    @Autowired private TestCaseMapper testCaseMapper;

    private long uidA;
    private long uidB;
    private long courseIdA;

    @BeforeEach
    void seed() {
        uidA = ensureUser("cs_t_a", "TEACHER", null, "甲老师");
        uidB = ensureUser("cs_t_b", "TEACHER", null, "乙老师");
        Course c = new Course();
        c.setName("选题课");
        c.setTerm("2025-2026-1");
        c.setTeacherId(uidA);
        courseMapper.insert(c);
        courseIdA = c.getId();
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

    private String teacherA() { return bearer(jwtService, uidA, "cs_t_a", "TEACHER"); }
    private String teacherB() { return bearer(jwtService, uidB, "cs_t_b", "TEACHER"); }

    private long createProblem(long ownerUid, String title, boolean isPublic) {
        Problem p = new Problem();
        p.setTeacherId(ownerUid);
        p.setTitle(title);
        p.setDescription("d");
        p.setIsPublic(isPublic);
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);
        return p.getId();
    }

    // 简报模板四处 @Test 方法均漏写 throws Exception（mockMvc.perform 抛受检异常，同 ProblemCrudTest 惯例补齐）
    @Test
    void select_increments_sort_order_and_lists_with_case_count() throws Exception {
        long p1 = createProblem(uidA, "题一", false);
        long p2 = createProblem(uidA, "题二", false);
        for (long i = 1; i <= 3; i++) {
            TestCase tc = new TestCase();
            tc.setProblemId(p2);
            tc.setInput("i" + i);
            tc.setExpectedOutput("o" + i);
            tc.setIsSample(false);
            testCaseMapper.insert(tc);
        }

        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + p1 + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + p2 + "}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].problemId").value((int) p1))
                .andExpect(jsonPath("$.data.records[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.records[1].problemId").value((int) p2))
                .andExpect(jsonPath("$.data.records[1].sortOrder").value(2))
                .andExpect(jsonPath("$.data.records[1].testCaseCount").value(3));
    }

    @Test
    void duplicate_selection_rejected_40402_and_reforeign_problem_40400() throws Exception {
        long own = createProblem(uidA, "己题", false);
        long foreign = createProblem(uidB, "他题", false);

        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + own + "}"))
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + own + "}"))
                .andExpect(jsonPath("$.code").value(40402));

        // 他人名下的题（即使存在）按归属链隐藏：40400
        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + foreign + "}"))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void remove_then_reselect_and_delete_protection_lifted() throws Exception {
        long pid = createProblem(uidA, "可移除题", false);
        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + pid + "}"))
                .andExpect(jsonPath("$.code").value(0));

        // 引用期内删除题目 → 40015（联动 T2 保护）
        mockMvc.perform(delete("/api/problems/" + pid).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(40015));

        mockMvc.perform(delete("/api/courses/" + courseIdA + "/problems/" + pid)
                        .header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(0));

        Long links = courseProblemCount(pid);
        assertThat(links).isZero();

        // 移除后可重新选入；sort_order 按 design「当前 max+1」取值，关联清空后从 1 重计
        // （简报模板原断言 2 与规格自相矛盾：唯一关联行已被物理删除，max 为空必得 1；
        //   跨删除续递增需额外持久化计数器，属 schema 级改动，超出本期范围——按上位规格对齐）
        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherA())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + pid + "}"))
                .andExpect(jsonPath("$.code").value(0));
        CourseProblem relinked = courseProblemMapper.selectOne(
                new LambdaQueryWrapper<CourseProblem>().eq(CourseProblem::getProblemId, pid));
        assertThat(relinked.getSortOrder()).isEqualTo(1);

        // 再次移出解除引用，题目方可物理删除（design 测试策略：选入 → DELETE 40015 → 移出 → 可删；
        // 简报模板漏了重选后的二次移出，带着引用删题必被 T2 保护拦为 40015）
        mockMvc.perform(delete("/api/courses/" + courseIdA + "/problems/" + pid)
                        .header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/problems/" + pid).header("Authorization", teacherA()))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void other_teacher_cannot_touch_my_course_selection() throws Exception {
        long pid = createProblem(uidB, "乙的题", false);
        mockMvc.perform(post("/api/courses/" + courseIdA + "/problems")
                        .header("Authorization", teacherB())   // 非课程属主
                        .contentType(MediaType.APPLICATION_JSON).content("{\"problemId\":" + pid + "}"))
                .andExpect(jsonPath("$.code").value(40400));

        mockMvc.perform(delete("/api/courses/" + courseIdA + "/problems/123")
                        .header("Authorization", teacherB()))
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Autowired private CourseProblemMapper courseProblemMapper;

    private Long courseProblemCount(long pid) {
        return courseProblemMapper.selectCount(
                new QueryWrapper<CourseProblem>().lambda().eq(CourseProblem::getProblemId, pid));
    }
}
