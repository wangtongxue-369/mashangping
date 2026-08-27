package com.mashangping.assignment;

import java.time.LocalDateTime;
import java.util.List;

/** 作业域出参集合：教师/学生分型，学生侧结构不含隐藏字段 */
public final class AssignmentViews {

    /** 教师列表行 */
    public record TeacherListItem(long id, String title, LocalDateTime startAt, LocalDateTime dueAt,
                                  int lateDays, boolean isPublished, long problemCount, int totalScore,
                                  String status) {
    }

    /** 教师详情：全字段 + 题目清单 */
    public record TeacherDetail(long id, long courseId, String title, String description,
                                LocalDateTime startAt, LocalDateTime dueAt, int lateDays,
                                boolean isPublished, String status,
                                List<ProblemItem> problems) {
        public record ProblemItem(long problemId, String title, int score, int sortOrder) {
        }
    }

    /** 学生作业列表行 */
    public record StudentListItem(long id, String title, LocalDateTime startAt, LocalDateTime dueAt,
                                  int lateDays, long problemCount, String status) {
    }

    /** 学生题目列表行（含语言集合/时空限制，供前端进入编码页） */
    public record StudentProblemItem(long problemId, String title, int score, int sortOrder,
                                     List<String> languages, int timeLimitMs, int memoryLimitMb) {
    }

    /** 学生题目阅读视图：样例点完整输入输出；隐藏点零泄漏 */
    public record StudentSample(String input, String output) {
    }

    public record StudentProblemDetail(long problemId, String title, String description,
                                       List<String> languages, int timeLimitMs, int memoryLimitMb,
                                       List<StudentSample> samples) {
    }

    private AssignmentViews() {
    }
}
