package com.mashangping.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** 学校邮箱域名白名单：邮箱须以 "@<suffix>" 结尾（大小写不敏感）。 */
@Component
public class SchoolDomainChecker {

    private final List<String> suffixes;

    public SchoolDomainChecker(@Value("${msp.school.email-suffixes}") List<String> suffixes) {
        this.suffixes = suffixes.stream()
                .map(s -> "@" + s.toLowerCase(Locale.ROOT))
                .toList();
    }

    public boolean allowed(String email) {
        if (email == null) {
            return false;
        }
        String normalized = email.toLowerCase(Locale.ROOT);
        return suffixes.stream().anyMatch(normalized::endsWith);
    }
}
