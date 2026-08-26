package com.mashangping.register;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.emailverify.EmailVerification;
import com.mashangping.emailverify.EmailVerificationMapper;
import com.mashangping.mail.MailSender;
import com.mashangping.mail.SchoolDomainChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final int VALID_MINUTES = 15;
    private static final int RESEND_INTERVAL_SECONDS = 60;
    private static final int DAILY_LIMIT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** 单邮箱验证码失败尝试上限：累计达阈值后一律拒绝，直至重新签发或验证成功 */
    private static final int MAX_VERIFY_FAILURES = 5;
    /** 失败计数表容量上限：仿 UserAuthCache 的超限全清自愈策略 */
    private static final int MAX_TRACKED_EMAILS = 10_000;

    private final EmailVerificationMapper emailVerificationMapper;
    private final SchoolDomainChecker domainChecker;
    private final MailSender mailSender;

    /**
     * 注册验证码爆破节流：内存失败计数（单机单体架构，无多实例失效问题）。
     * 键取小写化 email——DB 层 utf8mb4_unicode_ci 已使大小写归一，内存键自行 lower-case 对齐，防换大小写绕过。
     * 有界性说明：仅靠 issue() 重置并不天然有界——攻击者可对任意从未签发过的邮箱狂打错码，
     * 使表随请求无限增长；故仿 security/UserAuthCache 在超限时全量清空自愈
     * （代价是极端滥用下个别邮箱的锁提前解除，属可接受的取舍）。
     */
    private final Map<String, Integer> verifyFailures = new ConcurrentHashMap<>();

    public void issue(String email) {
        if (!domainChecker.allowed(email)) {
            throw new BizException(ErrorCode.EMAIL_DOMAIN_NOT_ALLOWED);
        }
        LocalDateTime now = LocalDateTime.now();
        Long lastMinute = emailVerificationMapper.selectCount(new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getEmail, email)
                .gt(EmailVerification::getCreatedAt, now.minusSeconds(RESEND_INTERVAL_SECONDS)));
        if (lastMinute != null && lastMinute > 0) {
            throw new BizException(ErrorCode.EMAIL_CODE_RATE_LIMITED);
        }
        Long today = emailVerificationMapper.selectCount(new LambdaQueryWrapper<EmailVerification>()
                .eq(EmailVerification::getEmail, email)
                .gt(EmailVerification::getCreatedAt, now.toLocalDate().atStartOfDay()));
        if (today != null && today >= DAILY_LIMIT) {
            throw new BizException(ErrorCode.EMAIL_CODE_RATE_LIMITED);
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        EmailVerification record = new EmailVerification();
        record.setEmail(email);
        record.setCode(code);
        record.setPurpose("REGISTER");
        record.setCreatedAt(now);
        record.setExpiresAt(now.plusMinutes(VALID_MINUTES));
        record.setUsed(false);
        emailVerificationMapper.insert(record);

        // 新码签发成功即重置该邮箱的失败计数：用户申请新码意味着重新开始
        verifyFailures.remove(throttleKey(email));

        mailSender.send(email, "码上评注册验证码",
                "您的注册验证码是：" + code + "，" + VALID_MINUTES + " 分钟内有效。若非本人操作请忽略。");
    }

    /** 校验通过则以条件 UPDATE 原子消费该码（防并发双兑）；任何失败计入节流并统一抛 40011 */
    public void verify(String email, String code) {
        String key = throttleKey(email);
        // 锁定判断前置：达阈值后无论携带对错一律拒绝，不给攻击者区分信号；
        // 锁定后的尝试同样累计，防探测解锁时机
        Integer failures = verifyFailures.get(key);
        if (failures != null && failures >= MAX_VERIFY_FAILURES) {
            recordVerifyFailure(key);
            throw new BizException(ErrorCode.EMAIL_CODE_INVALID);
        }

        EmailVerification record = emailVerificationMapper.selectOne(
                new LambdaQueryWrapper<EmailVerification>()
                        .eq(EmailVerification::getEmail, email)
                        .eq(EmailVerification::getUsed, false)
                        .gt(EmailVerification::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(EmailVerification::getId)
                        .last("LIMIT 1"));
        if (record == null || !record.getCode().equals(code)) {
            recordVerifyFailure(key);
            throw new BizException(ErrorCode.EMAIL_CODE_INVALID);
        }
        // 条件 UPDATE 原子消费：affected==0 说明同码已被并发请求抢先使用
        int consumed = emailVerificationMapper.update(null, new LambdaUpdateWrapper<EmailVerification>()
                .eq(EmailVerification::getId, record.getId())
                .eq(EmailVerification::getUsed, false)
                .set(EmailVerification::getUsed, true));
        if (consumed != 1) {
            recordVerifyFailure(key);
            throw new BizException(ErrorCode.EMAIL_CODE_INVALID);
        }
        verifyFailures.remove(key); // 验证成功清零
    }

    private String throttleKey(String email) {
        return email == null ? "" : email.toLowerCase(Locale.ROOT);
    }

    private void recordVerifyFailure(String key) {
        if (verifyFailures.size() >= MAX_TRACKED_EMAILS) {
            verifyFailures.clear(); // 与 UserAuthCache 同思路：正常规模到不了上限，超限全清是简单正确的自愈
        }
        verifyFailures.merge(key, 1, Integer::sum);
    }
}
