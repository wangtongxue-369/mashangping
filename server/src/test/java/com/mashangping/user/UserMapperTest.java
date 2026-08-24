package com.mashangping.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest extends IntegrationTestBase {

    @Autowired
    private UserMapper userMapper;

    private User newUser(String username, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash("hash-" + username);
        u.setRealName("测试" + username);
        u.setRole(role);
        u.setEnabled(true);
        return u;
    }

    @Test
    void insert_and_find_by_username() {
        User u = newUser("mapper_tom", "STUDENT");
        u.setStudentNo("2026001");
        userMapper.insert(u);
        assertThat(u.getId()).isNotNull();

        User found = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "mapper_tom"));
        assertThat(found).isNotNull();
        assertThat(found.getStudentNo()).isEqualTo("2026001");
        assertThat(found.getPasswordHash()).isEqualTo("hash-mapper_tom");
    }

    @Test
    void duplicate_username_rejected_by_unique_key() {
        userMapper.insert(newUser("mapper_dup", "TEACHER"));
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DuplicateKeyException.class,
                () -> userMapper.insert(newUser("mapper_dup", "TEACHER")));
    }

    @Test
    void page_query_with_role_filter() {
        for (int i = 0; i < 5; i++) {
            userMapper.insert(newUser("page_s" + i, "STUDENT"));
        }
        userMapper.insert(newUser("page_t0", "TEACHER"));

        Page<User> page = userMapper.selectPage(
                new Page<>(1, 3),
                new LambdaQueryWrapper<User>().eq(User::getRole, "STUDENT"));
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(5);
        assertThat(page.getRecords()).hasSize(3);
    }

    @Test
    void update_enabled_flag() {
        User u = newUser("mapper_off", "STUDENT");
        userMapper.insert(u);
        u.setEnabled(false);
        userMapper.updateById(u);

        User found = userMapper.selectById(u.getId());
        assertThat(found.getEnabled()).isFalse();
    }
}
