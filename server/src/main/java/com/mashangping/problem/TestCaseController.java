package com.mashangping.problem;

import com.mashangping.common.ApiResponse;
import com.mashangping.problem.dto.TestCaseUpsertRequest;
import com.mashangping.security.TokenPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/problems/{problemId}/test-cases")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public record CreatedTestCase(long id) {}

    @PostMapping
    public ApiResponse<CreatedTestCase> create(@AuthenticationPrincipal TokenPayload me,
                                               @PathVariable long problemId,
                                               @Valid @RequestBody TestCaseUpsertRequest request) {
        TestCase tc = testCaseService.create(me.uid(), problemId, request);
        return ApiResponse.ok(new CreatedTestCase(tc.getId()));
    }

    @PutMapping("/{testCaseId}")
    public ApiResponse<Void> update(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long problemId,
                                    @PathVariable long testCaseId,
                                    @Valid @RequestBody TestCaseUpsertRequest request) {
        testCaseService.update(me.uid(), problemId, testCaseId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{testCaseId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long problemId,
                                    @PathVariable long testCaseId) {
        testCaseService.delete(me.uid(), problemId, testCaseId);
        return ApiResponse.ok();
    }
}
