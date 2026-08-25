package com.mashangping.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "msp.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingMailSender implements MailSender {

    @Override
    public void send(String to, String subject, String textBody) {
        log.info("[开发邮件] to={} subject={} body={}", to, subject, textBody);
    }
}
