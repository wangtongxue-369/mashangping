package com.mashangping.course;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.ApiResponse;
import com.mashangping.course.dto.AddStudentRequest;
import com.mashangping.security.TokenPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CourseMemberController {

    private final EnrollmentService enrollmentService;
    private final ExcelImportService excelImportService;

    @PostMapping("/api/courses/{courseId}/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<EnrollmentView> add(@AuthenticationPrincipal TokenPayload me,
                                           @PathVariable long courseId,
                                           @Valid @RequestBody AddStudentRequest request) {
        return ApiResponse.ok(enrollmentService.addStudent(
                me.uid(), courseId, request.studentNo(), request.studentName()));
    }

    @GetMapping("/api/courses/{courseId}/students")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Page<EnrollmentView>> list(@AuthenticationPrincipal TokenPayload me,
                                                  @PathVariable long courseId,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(enrollmentService.list(
                me.uid(), courseId, status, keyword, page, size));
    }

    @DeleteMapping("/api/courses/{courseId}/students/{enrollmentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<Void> remove(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long courseId,
                                    @PathVariable long enrollmentId) {
        enrollmentService.remove(me.uid(), courseId, enrollmentId);
        return ApiResponse.ok();
    }

    @GetMapping("/api/courses/my")
    @PreAuthorize("hasRole('STUDENT')")
    public ApiResponse<List<EnrollmentService.MyCourseView>> my(@AuthenticationPrincipal TokenPayload me) {
        return ApiResponse.ok(enrollmentService.myCourses(me.uid()));
    }

    @PostMapping(value = "/api/courses/{courseId}/students/import",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TEACHER')")
    public ApiResponse<ExcelImportService.ImportResult> importStudents(
            @AuthenticationPrincipal TokenPayload me,
            @PathVariable long courseId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(excelImportService.importStudents(me.uid(), courseId, file));
    }
}
