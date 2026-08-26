package com.mashangping.problem;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.ApiResponse;
import com.mashangping.problem.dto.ProblemUpsertRequest;
import com.mashangping.security.TokenPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class ProblemController {

    private final ProblemService problemService;

    public record CreatedProblem(long id, String title) {}

    @PostMapping
    public ApiResponse<CreatedProblem> create(@AuthenticationPrincipal TokenPayload me,
                                              @Valid @RequestBody ProblemUpsertRequest request) {
        Problem p = problemService.create(me.uid(), request);
        return ApiResponse.ok(new CreatedProblem(p.getId(), p.getTitle()));
    }

    @GetMapping
    public ApiResponse<Page<ProblemSummaryView>> list(@AuthenticationPrincipal TokenPayload me,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(problemService.listMine(me.uid(), page, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProblemDetailView> detail(@AuthenticationPrincipal TokenPayload me,
                                                 @PathVariable long id) {
        return ApiResponse.ok(ProblemDetailView.from(problemService.detail(me.uid(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long id,
                                    @Valid @RequestBody ProblemUpsertRequest request) {
        problemService.update(me.uid(), id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long id) {
        problemService.delete(me.uid(), id);
        return ApiResponse.ok();
    }
}
