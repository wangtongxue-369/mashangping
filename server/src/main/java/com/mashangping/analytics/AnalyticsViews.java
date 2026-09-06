package com.mashangping.analytics;

/** 教师端聚合出参集合。 */
public final class AnalyticsViews {

    /** 课程概览。 */
    public record Overview(long courseId, long studentCount, long assignmentCount,
                           long publishedAssignmentCount, long courseProblemCount,
                           Double latestAvg, long highPlagiarismPairs) {
    }

    /** 各作业成绩趋势点（平均/最高/最低基于在册学生，缺提交记 0）。 */
    public record ScorePoint(long assignmentId, String title, String startAt, String dueAt,
                             double avg, double max, double min, long submittedCount) {
    }

    /** 作业分数段分布桶。 */
    public record ScoreDistribution(long assignmentId, String assignmentTitle,
                                    java.util.List<Bucket> buckets) {
    }

    /** 分数段桶：label 如 0-59/90-100。 */
    public record Bucket(String label, long count) {
    }

    /** 每个作业题 AC 率。 */
    public record AcRateRow(long assignmentId, String assignmentTitle, long problemId,
                            String problemTitle, long acStudents, long submittedStudents, double rate) {
    }

    /** 提交状态计数。 */
    public record StatusCount(String status, long count) {
    }

    /** 按天提交量。 */
    public record TimelinePoint(String date, long count) {
    }

    /** 查重风险（某作业题 HIGH/MID 疑似对数）。 */
    public record PlagiarismRiskRow(long assignmentId, String assignmentTitle, long problemId,
                                    String problemTitle, long highPairs, long midPairs) {
    }

    private AnalyticsViews() {
    }
}
