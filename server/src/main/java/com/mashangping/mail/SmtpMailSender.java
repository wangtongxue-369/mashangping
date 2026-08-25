package com.mashangping.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "msp.mail.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SmtpMailSender implements MailSender {

    private final JavaMailSender javaMailSender;

    @Value("${msp.mail.from}")
    private String from;

    @Override
    public void send(String to, String subject, String textBody) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(textBody);
        javaMailSender.send(message);
        log.info("已发送邮件 to={} subject={}", to, subject);
    }
}
