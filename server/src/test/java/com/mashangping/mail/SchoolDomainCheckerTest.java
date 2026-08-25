package com.mashangping.mail;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolDomainCheckerTest {

    private SchoolDomainChecker checker() {
        return new SchoolDomainChecker(List.of("stu.example.edu.cn", "staff.example.edu.cn"));
    }

    @Test
    void allows_configured_suffix_case_insensitively() {
        assertThat(checker().allowed("alice@STU.Example.Edu.CN")).isTrue();
        assertThat(checker().allowed("bob@stu.example.edu.cn")).isTrue();
    }

    @Test
    void rejects_other_domains_and_malformed() {
        assertThat(checker().allowed("eve@gmail.com")).isFalse();
        assertThat(checker().allowed("trick@fakestu.example.edu.cn")).isFalse(); // 后缀前拼凑
        assertThat(checker().allowed("no-at-sign")).isFalse();
        assertThat(checker().allowed(null)).isFalse();
    }
}
