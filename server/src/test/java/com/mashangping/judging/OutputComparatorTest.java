package com.mashangping.judging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputComparatorTest {

    @Test
    void exact_match_passes() {
        assertThat(OutputComparator.compare("hello\nworld", "hello\nworld")).isTrue();
    }

    @Test
    void trailing_whitespace_per_line_ignored() {
        // 修正：原简报断言 compare("a \tb\n", "a\t\tb ") 为真——" \t"与"\t\t"是行内差异，
        // 按“每行剥行尾空白+丢弃文末空行”规则属真差异，此处改为行尾空白对照
        assertThat(OutputComparator.compare("a b\t\n", "a b \n")).isTrue();
        assertThat(OutputComparator.compare("a \tb", "a\t\tb")).isFalse();
    }

    @Test
    void trailing_blank_lines_ignored() {
        assertThat(OutputComparator.compare("x\ny", "x\ny\n\n\n")).isTrue();
        assertThat(OutputComparator.compare("x\ny\n", "x\ny")).isTrue();
    }

    @Test
    void internal_difference_fails() {
        assertThat(OutputComparator.compare("1 2\n3", "2 1\n3")).isFalse();
        assertThat(OutputComparator.compare("abc", "abd")).isFalse();
    }

    @Test
    void leading_blank_line_of_actual_is_a_difference() {
        // 只容忍文末多余空行；文首空行是真差异
        // 修正：原简报在 trailing_blank_lines_ignored 断言 compare("x\ny\n", "\nx\ny\n") 为真，
        // 与本规则自相矛盾，按规格“文首空行是真差异”改为断 false 并移入本用例
        assertThat(OutputComparator.compare("x\n", "\nx\n")).isFalse();
        assertThat(OutputComparator.compare("x\ny\n", "\nx\ny\n")).isFalse();
    }
}
