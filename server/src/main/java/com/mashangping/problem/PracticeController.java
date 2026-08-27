package com.mashangping.problem;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.ApiResponse;
import com.mashangping.common.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/practice/problems")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT','TEACHER')")
public class PracticeController {

    private final PracticeService practiceService;

    @GetMapping
    public ApiResponse<Page<PracticeViews.Summary>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(practiceService.list(
                PageUtils.page(page), PageUtils.size(size), keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<PracticeViews.Detail> detail(@PathVariable long id) {
        return ApiResponse.ok(practiceService.detail(id));
    }
}
