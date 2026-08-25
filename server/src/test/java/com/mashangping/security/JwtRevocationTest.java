package com.mashangping.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtRevocationTest extends IntegrationTestBase {

    @Autowired private JwtService jwtService;
    @Autowired private UserMapper userMapper;
    @Autowired private UserAuthCache userAuthCache;

    private long victimUid;
    private String victimToken;

    @BeforeEach
    void seedVictim() {
        User probe = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "revoke_victim"));
        if (probe == null) {
            User u = new User();
            u.setUsername("revoke_victim");
            u.setPasswordHash(passwordEncoder.encode("secret66"));
            u.setRealName("被停用人");
            u.setRole(User.ROLE_STUDENT);
            u.setEnabled(true);
            userMapper.insert(u);
            probe = u;
        } else {
            // 测试基座无事务回滚：复用探测账号时必须恢复启用态，
            // 否则"停用"用例会污染后续用例，形成对方法排序的隐性依赖
            probe.setEnabled(true);
            userMapper.updateById(probe);
            userAuthCache.invalidate(probe.getId());
        }
        victimUid = probe.getId();
        victimToken = jwtService.generate(
                new TokenPayload(probe.getId(), probe.getUsername(), probe.getRole()));
    }

    @Test
    void valid_token_passes_before_disable() throws Exception {
        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + victimToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void token_of_unknown_uid_is_unauthorized() throws Exception {
        // 实时吊销改造后：库里查不到的 uid 一律拒绝（即使签名有效）
        String ghost = jwtService.generate(new TokenPayload(9999999L, "ghost", "STUDENT"));
        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + ghost))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void disabled_user_token_immediately_unauthorized() throws Exception {
        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + victimToken))
                .andExpect(status().isOk());

        // 模拟管理端正式停用路径：改库 + 失效缓存（与 UserService.updateStatus 行为一致）
        User victim = userMapper.selectById(victimUid);
        victim.setEnabled(false);
        userMapper.updateById(victim);
        userAuthCache.invalidate(victimUid);

        mockMvc.perform(get("/api/ping").header("Authorization", "Bearer " + victimToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
