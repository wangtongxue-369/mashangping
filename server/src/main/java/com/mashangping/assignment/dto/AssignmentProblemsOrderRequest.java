package com.mashangping.assignment.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 作业题序重排：按目标顺序给出全部 problemId（需与本作业已选题集合一致且不重复）。 */
public record AssignmentProblemsOrderRequest(
        @NotEmpty(message = "题目顺序不能为空") List<Long> problemIds) {
}
