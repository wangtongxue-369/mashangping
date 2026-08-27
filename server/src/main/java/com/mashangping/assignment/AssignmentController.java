package com.mashangping.assignment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.assignment.dto.AssignmentScoreRequest;
import com.mashangping.assignment.dto.AssignmentProblemsRequest;
import com.mashangping.assignment.dto.AssignmentUpsertRequest;
import com.mashangping.common.ApiResponse;
import com.mashangping.common.PageUtils;
import com.mashangping.security.TokenPayload;
import com.mashangping.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final StudentAssignmentService studentAssignmentService;

    public record CreatedAssignment(long id, String title) {}

    @PostMapping("/courses/{courseId}/assignments")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<CreatedAssignment> create(@AuthenticationPrincipal TokenPayload me,
                                                 @PathVariable long courseId,
                                                 @Valid @RequestBody AssignmentUpsertRequest request) {
        Assignment a = assignmentService.create(me.uid(), courseId, request);
        return ApiResponse.ok(new CreatedAssignment(a.getId(), a.getTitle()));
    }

    /** 同路径角色分流：教师看含草稿全量；学生只看已发布 */
    @GetMapping("/courses/{courseId}/assignments")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    public ApiResponse<Page<?>> list(@AuthenticationPrincipal TokenPayload me,
                                     @PathVariable long courseId,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        if (User.ROLE_TEACHER.equals(me.role())) {
            return ApiResponse.ok(assignmentService.listMine(me.uid(), courseId,
                    PageUtils.page(page), PageUtils.size(size)));
        }
        return ApiResponse.ok(studentAssignmentService.list(me.uid(), courseId,
                PageUtils.page(page), PageUtils.size(size)));
    }

    @GetMapping("/assignments/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<AssignmentViews.TeacherDetail> detail(@AuthenticationPrincipal TokenPayload me,
                                                             @PathVariable long id) {
        return ApiResponse.ok(assignmentService.detail(me.uid(), id));
    }

    @PutMapping("/assignments/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> update(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long id,
                                    @Valid @RequestBody AssignmentUpsertRequest request) {
        assignmentService.update(me.uid(), id, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/assignments/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> delete(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long id) {
        assignmentService.delete(me.uid(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/assignments/{id}/problems")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> addProblems(@AuthenticationPrincipal TokenPayload me,
                                         @PathVariable long id,
                                         @Valid @RequestBody AssignmentProblemsRequest request) {
        assignmentService.addProblems(me.uid(), id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/assignments/{id}/problems/{problemId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> updateScore(@AuthenticationPrincipal TokenPayload me,
                                         @PathVariable long id,
                                         @PathVariable long problemId,
                                         @Valid @RequestBody AssignmentScoreRequest request) {
        assignmentService.updateScore(me.uid(), id, problemId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/assignments/{id}/problems/{problemId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> removeProblem(@AuthenticationPrincipal TokenPayload me,
                                           @PathVariable long id,
                                           @PathVariable long problemId) {
        assignmentService.removeProblem(me.uid(), id, problemId);
        return ApiResponse.ok();
    }

    /** 学生：作业题目列表（start_at 门在 service） */
    @GetMapping("/courses/{courseId}/assignments/{assignmentId}/problems")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    public ApiResponse<List<AssignmentViews.StudentProblemItem>> problems(
            @AuthenticationPrincipal TokenPayload me,
            @PathVariable long courseId,
            @PathVariable long assignmentId) {
        return ApiResponse.ok(studentAssignmentService.problems(me.uid(), courseId, assignmentId));
    }

    /** 学生：题目阅读视图（隐藏点零泄漏） */
    @GetMapping("/courses/{courseId}/assignments/{assignmentId}/problems/{problemId}")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
    public ApiResponse<AssignmentViews.StudentProblemDetail> problemDetail(
            @AuthenticationPrincipal TokenPayload me,
            @PathVariable long courseId,
            @PathVariable long assignmentId,
            @PathVariable long problemId) {
        return ApiResponse.ok(studentAssignmentService.problemDetail(me.uid(), courseId, assignmentId, problemId));
    }
}
