package com.mashangping.gradebook;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.ApiResponse;
import com.mashangping.common.PageUtils;
import com.mashangping.gradebook.dto.GradebookView;
import com.mashangping.gradebook.dto.TeacherSubmissionRow;
import com.mashangping.security.TokenPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/assignments/{assignmentId}")
@PreAuthorize("hasRole('TEACHER')")
@RequiredArgsConstructor
public class GradebookController {

    private final GradebookService gradebookService;

    @GetMapping("/gradebook")
    public ApiResponse<GradebookView> gradebook(@AuthenticationPrincipal TokenPayload me,
                                                @PathVariable long assignmentId) {
        return ApiResponse.ok(gradebookService.gradebook(me.uid(), assignmentId));
    }

    @GetMapping("/submissions")
    public ApiResponse<Page<TeacherSubmissionRow>> submissions(
            @AuthenticationPrincipal TokenPayload me, @PathVariable long assignmentId,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long assignmentProblemId,
            @RequestParam(required = false) Long studentId) {
        return ApiResponse.ok(gradebookService.submissions(me.uid(), assignmentId,
                PageUtils.page(page), PageUtils.size(size), assignmentProblemId, studentId));
    }

    @GetMapping("/gradebook/csv")
    public ResponseEntity<byte[]> csv(@AuthenticationPrincipal TokenPayload me,
                                      @PathVariable long assignmentId) {
        byte[] bytes = gradebookService.csv(me.uid(), assignmentId);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=gradebook-" + assignmentId + ".csv")
                .body(bytes);
    }

    @GetMapping("/submissions/{submissionId}")
    public ApiResponse<TeacherSubmissionRow.Detail> submissionDetail(
            @AuthenticationPrincipal TokenPayload me, @PathVariable long assignmentId,
            @PathVariable long submissionId) {
        return ApiResponse.ok(gradebookService.submissionDetail(me.uid(), assignmentId, submissionId));
    }
}