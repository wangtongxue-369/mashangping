package com.mashangping.judging.dto;

import jakarta.validation.constraints.NotBlank;

/** 双目标键互斥：恰好其一；互斥校验放 service（跨字段注解不值得引入） */
public record SubmissionCreateRequest(Long assignmentProblemId, Long problemId,
                                      @NotBlank String language,
                                      @NotBlank String code) {
}
