package com.mashangping.plagiarism;

import com.mashangping.judging.Submission;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** 取样排序：AC 优先 → 分高优先 → 新提交优先（纯单测）。 */
class PlagiarismSamplingTest {

    private static Submission s(long id, String status, Integer score, String at) {
        Submission x = new Submission();
        x.setId(id);
        x.setStatus(status);
        x.setScore(score);
        x.setSubmittedAt(at == null ? null : LocalDateTime.parse(at));
        return x;
    }

    @Test
    void acPreferredOverWaEvenIfLater() {
        Submission wa = s(1, "WA", 0, "2026-09-06T10:00:00");
        Submission ac = s(2, "AC", 100, "2026-09-06T09:00:00");
        assertThat(PlagiarismService.isBetter(ac, wa)).isTrue();
        assertThat(PlagiarismService.isBetter(wa, ac)).isFalse();
    }

    @Test
    void higherScorePreferredWithinSameAc() {
        Submission ac10 = s(1, "AC", 10, "2026-09-06T10:00:00");
        Submission ac100 = s(2, "AC", 100, "2026-09-06T09:00:00");
        assertThat(PlagiarismService.isBetter(ac100, ac10)).isTrue();
    }

    @Test
    void latestSubmissionWinsOnEqualScore() {
        Submission early = s(1, "AC", 100, "2026-09-06T09:00:00");
        Submission late = s(2, "AC", 100, "2026-09-06T11:00:00");
        assertThat(PlagiarismService.isBetter(late, early)).isTrue();
    }

    @Test
    void ceNullScoreLoosesToWaZero() {
        Submission ce = s(1, "CE", null, "2026-09-06T12:00:00");
        Submission wa = s(2, "WA", 0, "2026-09-06T09:00:00");
        assertThat(PlagiarismService.isBetter(wa, ce)).isTrue();
        assertThat(PlagiarismService.isBetter(ce, wa)).isFalse();
    }
}
