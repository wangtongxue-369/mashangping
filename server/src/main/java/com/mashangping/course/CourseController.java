package com.mashangping.course;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.ApiResponse;
import com.mashangping.course.dto.CourseUpsertRequest;
import com.mashangping.security.TokenPayload;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class CourseController {

    private final CourseService courseService;

    public record CreatedCourse(long id, String name, String term, String description) {}

    @PostMapping
    public ApiResponse<CreatedCourse> create(@AuthenticationPrincipal TokenPayload me,
                                             @Valid @RequestBody CourseUpsertRequest request) {
        Course c = courseService.create(me.uid(), request);
        return ApiResponse.ok(new CreatedCourse(c.getId(), c.getName(), c.getTerm(), c.getDescription()));
    }

    @GetMapping
    public ApiResponse<Page<CourseView>> list(@AuthenticationPrincipal TokenPayload me,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size,
                                              @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(courseService.listMine(me.uid(), page, size, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<CourseView> detail(@AuthenticationPrincipal TokenPayload me,
                                          @PathVariable long id) {
        return ApiResponse.ok(CourseView.from(courseService.getOwned(me.uid(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@AuthenticationPrincipal TokenPayload me,
                                    @PathVariable long id,
                                    @Valid @RequestBody CourseUpsertRequest request) {
        courseService.update(me.uid(), id, request);
        return ApiResponse.ok();
    }
}
