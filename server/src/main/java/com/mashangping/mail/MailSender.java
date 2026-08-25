package com.mashangping.mail;

/**
 * 发信抽象：隔离 SMTP 细节，便于测试替身与日后切换学校官方通道。
 */
@FunctionalInterface
public interface MailSender {

    void send(String to, String subject, String textBody);
}
