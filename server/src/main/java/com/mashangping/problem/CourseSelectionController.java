package com.mashangping.problem;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.ApiResponse;
import com.mashangping.security.TokenPayload;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses/{courseId}/problems")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class CourseSelectionController {

    private final CourseSelectionService courseSelectionService;

    public record SelectProblemRequest(@NotNull Long problemId) {}

    @PostMapping
    public ApiResponse<Void> select(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long courseId,
                                    @RequestBody SelectProblemRequest request) {
        courseSelectionService.select(me.uid(), courseId, request.problemId());
        return ApiResponse.ok();
    }

    @GetMapping
    public ApiResponse<Page<CourseProblemView>> list(@AuthenticationPrincipal TokenPayload me,
                                                     @PathVariable long courseId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(courseSelectionService.list(me.uid(), courseId, page, size));
    }

    @DeleteMapping("/{problemId}")
    public ApiResponse<Void> remove(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long courseId,
                                    @PathVariable long problemId) {
        courseSelectionService.remove(me.uid(), courseId, problemId);
        return ApiResponse.ok();
    }
}
