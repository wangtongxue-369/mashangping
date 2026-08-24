package com.mashangping.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Long admins = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, User.ROLE_ADMIN));
        if (admins != null && admins > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setRealName("系统管理员");
        admin.setRole(User.ROLE_ADMIN);
        admin.setEnabled(true);
        try {
            userMapper.insert(admin);
            log.warn("已创建初始管理员 admin/admin123，请立即登录修改密码！");
        } catch (DuplicateKeyException e) {
            // 并行上下文/多实例冷启动竞态兜底：admin 已存在则跳过
            log.info("初始管理员已存在，跳过种子创建");
        }
    }
}
