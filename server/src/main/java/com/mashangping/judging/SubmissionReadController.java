package com.mashangping.judging;

import com.mashangping.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionReadController {

    private final StudentReadService readService;

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<StudentSubmissionViews.Summary>> my(
            @AuthenticationPrincipal com.mashangping.security.TokenPayload me,
            @RequestParam(required = false) Long assignmentProblemId,
            @RequestParam(required = false) Long problemId) {
        return ApiResponse.ok(readService.listMine(me.uid(), assignmentProblemId, problemId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<StudentSubmissionViews.Detail> get(
            @AuthenticationPrincipal com.mashangping.security.TokenPayload me,
            @PathVariable long id) {
        return ApiResponse.ok(readService.detail(me.uid(), id));
    }
}
