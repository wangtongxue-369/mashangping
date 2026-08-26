package com.mashangping.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikeUtilsTest {

    @Test
    void escapes_wildcards_preserving_literal_chars() {
        assertThat(LikeUtils.escapeForLike("50%折扣_题\\难")).isEqualTo("50\\%折扣\\_题\\\\难");
        assertThat(LikeUtils.escapeForLike("普通关键字")).isEqualTo("普通关键字");
        assertThat(LikeUtils.escapeForLike("")).isEqualTo("");
    }
}
