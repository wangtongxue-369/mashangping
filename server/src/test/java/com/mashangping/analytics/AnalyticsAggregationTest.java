package com.mashangping.analytics;

import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.judging.Submission;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 聚合纯函数语义：bestScore=AC最高、缺提交=0、AC率/提交数、分数段桶、状态/时间聚合由 service 层覆盖。 */
class AnalyticsAggregationTest {

    private static Submission sub(long id, long userId, long apId, String status, Integer score, String at) {
        Submission s = new Submission();
        s.setId(id);
        s.setUserId(userId);
        s.setAssignmentProblemId(apId);
        s.setStatus(status);
        s.setScore(score);
        s.setSubmittedAt(at == null ? null : LocalDateTime.parse(at));
        return s;
    }

    private static AssignmentProblem ap(long id) {
        AssignmentProblem a = new AssignmentProblem();
        a.setId(id);
        return a;
    }

    @Test
    void totalScores_takesBestAc_and_zeroWhenNoAc() {
        List<AssignmentProblem> aps = List.of(ap(1L), ap(2L));
        List<Submission> subs = List.of(
                sub(1, 10, 1L, "WA", 0, "2026-09-01T09:00:00"),
                sub(2, 10, 1L, "AC", 100, "2026-09-01T09:10:00"), // 题1 AC
                sub(3, 10, 2L, "WA", 0, "2026-09-01T09:20:00"), // 题2 无AC
                sub(4, 20, 1L, "AC", 100, "2026-09-01T09:00:00"),
                sub(5, 30, 1L, "PENDING", null, "2026-09-01T09:30:00") // 未判不计
        );
        Map<Long, Integer> totals = AnalyticsService.totalScores(List.of(10L, 20L, 30L), aps, subs);
        assertThat(totals.get(10L)).isEqualTo(100); // 题1 AC100 + 题2 无AC=0
        assertThat(totals.get(20L)).isEqualTo(100);
        assertThat(totals.get(30L)).isZero();
        assertThat(AnalyticsService.average(totals)).isEqualTo(66.7);
        assertThat(AnalyticsService.submittedCount(aps, subs)).isEqualTo(2); // PENDING 学生不计
    }

    @Test
    void totalScores_skipsRunningAndPending() {
        List<Submission> subs = List.of(
                sub(1, 10, 1L, "RUNNING", null, "2026-09-01T09:00:00"),
                sub(2, 10, 1L, "AC", 100, "2026-09-01T09:10:00")
        );
        Map<Long, Integer> totals = AnalyticsService.totalScores(List.of(10L), List.of(ap(1L)), subs);
        assertThat(totals.get(10L)).isEqualTo(100);
    }

    @Test
    void buckets_groupBoundaries() {
        Map<Long, Integer> totals = Map.of(
                1L, 0, 2L, 59, 3L, 60, 4L, 69, 5L, 70, 6L, 79, 7L, 80, 8L, 89, 9L, 90, 10L, 100);
        List<AnalyticsViews.Bucket> buckets = AnalyticsService.bucketCounts(totals);
        assertThat(countOf(buckets, "0-59")).isEqualTo(2);
        assertThat(countOf(buckets, "60-69")).isEqualTo(2);
        assertThat(countOf(buckets, "70-79")).isEqualTo(2);
        assertThat(countOf(buckets, "80-89")).isEqualTo(2);
        assertThat(countOf(buckets, "90-100")).isEqualTo(2);
    }

    private static long countOf(List<AnalyticsViews.Bucket> buckets, String label) {
        return buckets.stream().filter(b -> b.label().equals(label)).mapToLong(AnalyticsViews.Bucket::count).sum();
    }
}
