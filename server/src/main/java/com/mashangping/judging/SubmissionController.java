package com.mashangping.judging;

import com.mashangping.common.ApiResponse;
import com.mashangping.judging.dto.SubmissionCreateRequest;
import com.mashangping.judging.dto.SubmissionCreatedView;
import com.mashangping.security.TokenPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<SubmissionCreatedView> submit(@AuthenticationPrincipal TokenPayload me,
                                                     @Valid @RequestBody SubmissionCreateRequest request) {
        return ApiResponse.ok(submissionService.submit(me.uid(), request));
    }
}
