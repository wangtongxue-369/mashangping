package com.mashangping.analytics;

import com.mashangping.common.ApiResponse;
import com.mashangping.security.TokenPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 教师端课程/作业聚合端点（仅 TEACHER；归属校验在 service，越权 40400）。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/courses/{courseId}/analytics/overview")
    public ApiResponse<AnalyticsViews.Overview> overview(@AuthenticationPrincipal TokenPayload me,
                                                         @PathVariable long courseId) {
        return ApiResponse.ok(analyticsService.overview(me.uid(), courseId));
    }

    @GetMapping("/courses/{courseId}/analytics/score-trend")
    public ApiResponse<List<AnalyticsViews.ScorePoint>> scoreTrend(@AuthenticationPrincipal TokenPayload me,
                                                                   @PathVariable long courseId) {
        return ApiResponse.ok(analyticsService.scoreTrend(me.uid(), courseId));
    }

    @GetMapping("/assignments/{assignmentId}/analytics/score-distribution")
    public ApiResponse<AnalyticsViews.ScoreDistribution> scoreDistribution(
            @AuthenticationPrincipal TokenPayload me, @PathVariable long assignmentId) {
        return ApiResponse.ok(analyticsService.scoreDistribution(me.uid(), assignmentId));
    }

    @GetMapping("/courses/{courseId}/analytics/ac-rate")
    public ApiResponse<List<AnalyticsViews.AcRateRow>> acRate(@AuthenticationPrincipal TokenPayload me,
                                                              @PathVariable long courseId) {
        return ApiResponse.ok(analyticsService.acRate(me.uid(), courseId));
    }

    @GetMapping("/courses/{courseId}/analytics/status-distribution")
    public ApiResponse<List<AnalyticsViews.StatusCount>> statusDistribution(
            @AuthenticationPrincipal TokenPayload me, @PathVariable long courseId,
            @RequestParam(required = false) Long assignmentId) {
        return ApiResponse.ok(analyticsService.statusDistribution(me.uid(), courseId, assignmentId));
    }

    @GetMapping("/courses/{courseId}/analytics/submission-timeline")
    public ApiResponse<List<AnalyticsViews.TimelinePoint>> timeline(@AuthenticationPrincipal TokenPayload me,
                                                                    @PathVariable long courseId) {
        return ApiResponse.ok(analyticsService.submissionTimeline(me.uid(), courseId));
    }

    @GetMapping("/courses/{courseId}/analytics/plagiarism-risk")
    public ApiResponse<List<AnalyticsViews.PlagiarismRiskRow>> plagiarismRisk(
            @AuthenticationPrincipal TokenPayload me, @PathVariable long courseId) {
        return ApiResponse.ok(analyticsService.plagiarismRisk(me.uid(), courseId));
    }
}
