package com.mashangping.problem.dto;

import jakarta.validation.constraints.NotBlank;

/** 测试点入参；64KB 字节上限在服务层按 UTF-8 校验（jakarta 无字节长度约束） */
public record TestCaseUpsertRequest(
        @NotBlank String input,
        @NotBlank String expectedOutput,
        Boolean isSample) {
}
