package com.mashangping.register;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final int VALID_MINUTES = 15;
    private static final int RESEND_INTERVAL_SECONDS = 60;
    private static final int DAILY_LIMIT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationMapper emailVerificationMapper;
    private final SchoolDomainChecker domainChecker;
    private final MailSender mailSender;

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

        mailSender.send(email, "码上评注册验证码",
                "您的注册验证码是：" + code + "，" + VALID_MINUTES + " 分钟内有效。若非本人操作请忽略。");
    }

    /** 校验通过则消费该码；否则抛 40011 */
    public void verify(String email, String code) {
        EmailVerification record = emailVerificationMapper.selectOne(
                new LambdaQueryWrapper<EmailVerification>()
                        .eq(EmailVerification::getEmail, email)
                        .eq(EmailVerification::getUsed, false)
                        .gt(EmailVerification::getExpiresAt, LocalDateTime.now())
                        .orderByDesc(EmailVerification::getId)
                        .last("LIMIT 1"));
        if (record == null || !record.getCode().equals(code)) {
            throw new BizException(ErrorCode.EMAIL_CODE_INVALID);
        }
        record.setUsed(true);
        emailVerificationMapper.updateById(record);
    }
}
