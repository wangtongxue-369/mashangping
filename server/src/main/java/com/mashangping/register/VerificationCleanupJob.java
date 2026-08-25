package com.mashangping.register;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.emailverify.EmailVerification;
import com.mashangping.emailverify.EmailVerificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 每日 04:30 清理 7 天前的验证码记录（规格 §9） */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationCleanupJob {

    private final EmailVerificationMapper emailVerificationMapper;

    @Scheduled(cron = "0 30 4 * * ?")
    public void purgeExpired() {
        int removed = emailVerificationMapper.delete(
                new LambdaQueryWrapper<EmailVerification>()
                        .lt(EmailVerification::getCreatedAt, LocalDateTime.now().minusDays(7)));
        if (removed > 0) {
            log.info("已清理过期验证码记录 {} 条", removed);
        }
    }
}
