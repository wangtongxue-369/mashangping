package com.mashangping.plagiarism;

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

/** 教师端代码查重端点（仅 TEACHER；归属校验在 service，越权 40400）。 */
@RestController
@RequestMapping("/api/assignments/{assignmentId}/plagiarism")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class PlagiarismController {

    private final PlagiarismService plagiarismService;

    /** 默认：报告含相似度 ≥0.5 的对，≥0.8 标 HIGH。 */
    private static final double DEFAULT_INCLUDE_MIN = 0.5;
    private static final double DEFAULT_HIGH = 0.8;

    @GetMapping("/problems/{problemId}")
    public ApiResponse<PlagiarismViews.Report> report(
            @AuthenticationPrincipal TokenPayload me,
            @PathVariable long assignmentId,
            @PathVariable long problemId,
            @RequestParam(defaultValue = "0.5") double includeMin,
            @RequestParam(defaultValue = "0.8") double high) {
        double include = Math.max(0.0, Math.min(includeMin, 1.0));
        double highThreshold = Math.max(include, Math.min(high, 1.0));
        return ApiResponse.ok(plagiarismService.report(me.uid(), assignmentId, problemId,
                include, highThreshold));
    }

    @GetMapping("/problems/{problemId}/compare")
    public ApiResponse<PlagiarismViews.CompareView> compare(
            @AuthenticationPrincipal TokenPayload me,
            @PathVariable long assignmentId,
            @PathVariable long problemId,
            @RequestParam long submissionA,
            @RequestParam long submissionB) {
        return ApiResponse.ok(plagiarismService.compare(me.uid(), assignmentId, problemId,
                submissionA, submissionB));
    }
}
