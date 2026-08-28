package com.mashangping.gradebook.dto;

import java.util.List;

public record GradebookView(
        long assignmentId, String assignmentTitle,
        List<StudentRow> students,
        List<ProblemRow> problems,
        List<Cell> cells,
        List<Total> totals) {
    public record StudentRow(long studentId, String studentNo, String realName) {}
    public record ProblemRow(long assignmentProblemId, long problemId, String title,
                             int score, int sortOrder) {}
    public record Cell(long studentId, long assignmentProblemId, int bestScore) {}
    public record Total(long studentId, int total) {}
}