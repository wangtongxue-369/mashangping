package com.mashangping.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PageUtilsTest {

    @Test
    void page_clamps_below_one_to_one() {
        assertThat(PageUtils.page(0)).isEqualTo(1);
        assertThat(PageUtils.page(-5)).isEqualTo(1);
        assertThat(PageUtils.page(1)).isEqualTo(1);
        assertThat(PageUtils.page(2)).isEqualTo(2);
    }

    @Test
    void size_clamps_to_range() {
        assertThat(PageUtils.size(0)).isEqualTo(1);
        assertThat(PageUtils.size(-3)).isEqualTo(1);
        assertThat(PageUtils.size(1)).isEqualTo(1);
        assertThat(PageUtils.size(20)).isEqualTo(20);
        assertThat(PageUtils.size(51)).isEqualTo(50);
        assertThat(PageUtils.size(9999)).isEqualTo(50);
    }
}
