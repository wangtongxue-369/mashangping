package com.mashangping.problem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 建题/改题共用入参；allowedLanguages 缺省=全支持，time/memory 缺省 1000/256 */
public record ProblemUpsertRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String description,
        List<String> allowedLanguages,
        @Positive Integer timeLimitMs,
        @Positive Integer memoryLimitMb,
        Boolean isPublic) {
}
