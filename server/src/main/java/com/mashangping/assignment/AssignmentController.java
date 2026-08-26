package com.mashangping.assignment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.assignment.dto.AssignmentUpsertRequest;
import com.mashangping.common.ApiResponse;
import com.mashangping.common.PageUtils;
import com.mashangping.security.TokenPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class AssignmentController {

    private final AssignmentService assignmentService;

    public record CreatedAssignment(long id, String title) {}

    @PostMapping("/courses/{courseId}/assignments")
    public ApiResponse<CreatedAssignment> create(@AuthenticationPrincipal TokenPayload me,
                                                 @PathVariable long courseId,
                                                 @Valid @RequestBody AssignmentUpsertRequest request) {
        Assignment a = assignmentService.create(me.uid(), courseId, request);
        return ApiResponse.ok(new CreatedAssignment(a.getId(), a.getTitle()));
    }

    /** 教师列表（含草稿）。学生侧同路径角色分流在 Task 5 落地 */
    @GetMapping("/courses/{courseId}/assignments")
    public ApiResponse<Page<AssignmentViews.TeacherListItem>> list(
            @AuthenticationPrincipal TokenPayload me,
            @PathVariable long courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(assignmentService.listMine(me.uid(), courseId,
                PageUtils.page(page), PageUtils.size(size)));
    }

    @GetMapping("/assignments/{id}")
    public ApiResponse<AssignmentViews.TeacherDetail> detail(@AuthenticationPrincipal TokenPayload me,
                                                             @PathVariable long id) {
        return ApiResponse.ok(assignmentService.detail(me.uid(), id));
    }

    @PutMapping("/assignments/{id}")
    public ApiResponse<Void> update(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long id,
                                    @Valid @RequestBody AssignmentUpsertRequest request) {
        assignmentService.update(me.uid(), id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/assignments/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long id) {
        assignmentService.delete(me.uid(), id);
        return ApiResponse.ok();
    }
}
