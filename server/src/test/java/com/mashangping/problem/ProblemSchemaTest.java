package com.mashangping.problem;

import com.mashangping.IntegrationTestBase;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemSchemaTest extends IntegrationTestBase {

    @Autowired private ProblemMapper problemMapper;
    @Autowired private TestCaseMapper testCaseMapper;
    @Autowired private CourseProblemMapper courseProblemMapper;
    @Autowired private UserMapper userMapper;

    @Test
    void problem_and_testcase_roundtrip_with_defaults() {
        Problem p = new Problem();
        p.setTeacherId(990001L);
        p.setTitle("两数之和");
        p.setDescription("# 求 two sum\n\n```c\nint main(){}\n```");
        p.setIsPublic(false);
        // 显式 set 创建时间，避免依赖容器 UTC 默认时钟（执行注记）
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);
        problemMapper.insert(p);

        Problem loaded = problemMapper.selectById(p.getId());
        assertThat(loaded.getTitle()).isEqualTo("两数之和");
        assertThat(loaded.getAllowedLanguages()).isNull(); // NULL=全支持
        assertThat(loaded.getTimeLimitMs()).isEqualTo(1000);
        assertThat(loaded.getMemoryLimitMb()).isEqualTo(256);
        assertThat(loaded.getUpdatedAt()).isNotNull();

        TestCase tc = new TestCase();
        tc.setProblemId(p.getId());
        tc.setInput("2 3\n");
        tc.setExpectedOutput("5\n");
        tc.setIsSample(true);
        testCaseMapper.insert(tc);
        assertThat(testCaseMapper.selectById(tc.getId()).getInput()).isEqualTo("2 3\n");
    }

    @Test
    void course_problem_unique_constraint_rejects_duplicate_pair() {
        CourseProblem cp = new CourseProblem();
        cp.setCourseId(990002L);
        cp.setProblemId(990003L);
        cp.setSortOrder(1);
        courseProblemMapper.insert(cp);

        CourseProblem dup = new CourseProblem();
        dup.setCourseId(990002L);
        dup.setProblemId(990003L);
        dup.setSortOrder(2);
        assertThatThrownBy(() -> courseProblemMapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void user_email_unique_allows_multiple_nulls_but_rejects_dup() {
        // 多行 NULL 共存（MySQL 唯一索引语义）
        for (String username : new String[]{"schema_email_null_a", "schema_email_null_b"}) {
            User u = new User();
            u.setUsername(username);
            u.setPasswordHash("x");
            u.setRealName("t");
            u.setRole("TEACHER");
            u.setEnabled(true);
            userMapper.insert(u);
        }
        User withEmail = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, "schema_email_null_a"));
        withEmail.setEmail("schema@stu.example.edu.cn");
        userMapper.updateById(withEmail);

        User another = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, "schema_email_null_b"));
        another.setEmail("schema@stu.example.edu.cn");
        assertThatThrownBy(() -> userMapper.updateById(another))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
