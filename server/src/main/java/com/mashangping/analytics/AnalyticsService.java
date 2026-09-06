package com.mashangping.analytics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
import com.mashangping.judging.Submission;
import com.mashangping.judging.SubmissionMapper;
import com.mashangping.plagiarism.PlagiarismService;
import com.mashangping.plagiarism.PlagiarismViews;
import com.mashangping.problem.CourseProblem;
import com.mashangping.problem.CourseProblemMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 课程/作业读聚合（纯内存计算，教师归属校验）。 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final CourseMapper courseMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final CourseProblemMapper courseProblemMapper;
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final PlagiarismService plagiarismService;

    // ---------- 概览 ----------
    public AnalyticsViews.Overview overview(long teacherUid, long courseId) {
        Course c = ownedCourse(teacherUid, courseId);
        List<Long> students = activeStudentIds(courseId);
        List<Assignment> assignments = assignmentsOf(courseId);
        List<Assignment> published = assignments.stream().filter(a -> Boolean.TRUE.equals(a.getIsPublished())).toList();

        Double latestAvg = null;
        Assignment latest = published.stream()
                .max(java.util.Comparator.comparing(Assignment::getDueAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .orElse(null);
        if (latest != null && !students.isEmpty()) {
            List<AssignmentProblem> aps = assignmentProblemsOf(latest.getId());
            if (!aps.isEmpty()) {
                List<Submission> subs = submissionsOf(aps);
                latestAvg = (double) Math.round(average(totalScores(students, aps, subs)) * 10) / 10;
            }
        }
        long high = 0;
        for (Assignment a : published) {
            for (AssignmentProblem ap : assignmentProblemsOf(a.getId())) {
                PlagiarismViews.Report r = plagiarismService.report(teacherUid, a.getId(),
                        ap.getProblemId(), 0.5, 0.8);
                high += r.items().stream().filter(i -> "HIGH".equals(i.flag())).count();
            }
        }
        long courseProblems = courseProblemMapper.selectCount(new LambdaQueryWrapper<CourseProblem>()
                .eq(CourseProblem::getCourseId, courseId));
        return new AnalyticsViews.Overview(courseId, students.size(), assignments.size(),
                published.size(), courseProblems, latestAvg, high);
    }

    // ---------- 各作业成绩趋势 ----------
    public List<AnalyticsViews.ScorePoint> scoreTrend(long teacherUid, long courseId) {
        ownedCourse(teacherUid, courseId);
        List<Long> students = activeStudentIds(courseId);
        List<Assignment> assignments = assignmentsOf(courseId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsPublished())).toList();
        List<AnalyticsViews.ScorePoint> points = new ArrayList<>();
        for (Assignment a : assignments) {
            List<AssignmentProblem> aps = assignmentProblemsOf(a.getId());
            if (aps.isEmpty()) {
                continue;
            }
            List<Submission> subs = submissionsOf(aps);
            Map<Long, Integer> totals = totalScores(students, aps, subs);
            if (students.isEmpty()) {
                continue;
            }
            points.add(new AnalyticsViews.ScorePoint(a.getId(), a.getTitle(),
                    String.valueOf(a.getStartAt()), String.valueOf(a.getDueAt()),
                    average(totals), totals.values().stream().mapToInt(Integer::intValue).max().orElse(0),
                    totals.values().stream().mapToInt(Integer::intValue).min().orElse(0),
                    submittedCount(aps, subs)));
        }
        return points;
    }

    // ---------- 作业分数段分布 ----------
    public AnalyticsViews.ScoreDistribution scoreDistribution(long teacherUid, long assignmentId) {
        Assignment a = ownedAssignment(teacherUid, assignmentId);
        List<Long> students = activeStudentIds(a.getCourseId());
        List<AssignmentProblem> aps = assignmentProblemsOf(assignmentId);
        Map<Long, Integer> totals = totalScores(students, aps, submissionsOf(aps));
        return new AnalyticsViews.ScoreDistribution(assignmentId, a.getTitle(),
                bucketCounts(students.isEmpty() ? Map.of() : totals));
    }

    // ---------- 每题 AC 率 ----------
    public List<AnalyticsViews.AcRateRow> acRate(long teacherUid, long courseId) {
        ownedCourse(teacherUid, courseId);
        List<Assignment> published = assignmentsOf(courseId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsPublished())).toList();
        List<AnalyticsViews.AcRateRow> rows = new ArrayList<>();
        for (Assignment a : published) {
            List<AssignmentProblem> aps = assignmentProblemsOf(a.getId());
            if (aps.isEmpty()) {
                continue;
            }
            List<Submission> subs = submissionsOf(aps);
            Map<Long, String> titles = problemTitles(aps);
            for (AssignmentProblem ap : aps) {
                List<Submission> apSubs = subs.stream()
                        .filter(s -> Objects.equals(s.getAssignmentProblemId(), ap.getId())).toList();
                long submitted = apSubs.stream().map(Submission::getUserId).distinct().count();
                long ac = apSubs.stream().filter(s -> Submission.STATUS_AC.equals(s.getStatus()))
                        .map(Submission::getUserId).distinct().count();
                double rate = submitted == 0 ? 0.0
                        : Math.round((double) ac / submitted * 1000) / 1000.0;
                rows.add(new AnalyticsViews.AcRateRow(a.getId(), a.getTitle(), ap.getProblemId(),
                        titles.getOrDefault(ap.getProblemId(), "题目"), ac, submitted, rate));
            }
        }
        return rows;
    }

    // ---------- 提交状态分布 ----------
    public List<AnalyticsViews.StatusCount> statusDistribution(long teacherUid, long courseId,
                                                               Long assignmentId) {
        if (assignmentId != null) {
            ownedAssignment(teacherUid, assignmentId);
        } else {
            ownedCourse(teacherUid, courseId);
        }
        List<Assignment> scope = assignmentsOf(courseId);
        List<Submission> subs = new ArrayList<>();
        for (Assignment a : scope) {
            if (assignmentId != null && !a.getId().equals(assignmentId)) {
                continue;
            }
            subs.addAll(submissionsOf(assignmentProblemsOf(a.getId())));
        }
        Map<String, Long> counts = new TreeMap<>(java.util.Collections.reverseOrder());
        for (Submission s : subs) {
            counts.merge(s.getStatus(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(e -> new AnalyticsViews.StatusCount(e.getKey(), e.getValue())).toList();
    }

    // ---------- 按天提交时间线 ----------
    public List<AnalyticsViews.TimelinePoint> submissionTimeline(long teacherUid, long courseId) {
        ownedCourse(teacherUid, courseId);
        List<Assignment> scope = assignmentsOf(courseId);
        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (Assignment a : scope) {
            for (Submission s : submissionsOf(assignmentProblemsOf(a.getId()))) {
                LocalDateTime at = s.getSubmittedAt();
                if (at != null) {
                    byDay.merge(at.toLocalDate(), 1L, Long::sum);
                }
            }
        }
        return byDay.entrySet().stream()
                .map(e -> new AnalyticsViews.TimelinePoint(String.valueOf(e.getKey()), e.getValue())).toList();
    }

    // ---------- 查重风险 ----------
    public List<AnalyticsViews.PlagiarismRiskRow> plagiarismRisk(long teacherUid, long courseId) {
        ownedCourse(teacherUid, courseId);
        List<Assignment> published = assignmentsOf(courseId).stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsPublished())).toList();
        List<AnalyticsViews.PlagiarismRiskRow> rows = new ArrayList<>();
        for (Assignment a : published) {
            for (AssignmentProblem ap : assignmentProblemsOf(a.getId())) {
                PlagiarismViews.Report r = plagiarismService.report(teacherUid, a.getId(),
                        ap.getProblemId(), 0.5, 0.8);
                long high = r.items().stream().filter(i -> "HIGH".equals(i.flag())).count();
                long mid = r.items().size() - high;
                if (high == 0 && mid == 0) {
                    continue;
                }
                Problem p = problemMapper.selectById(ap.getProblemId());
                rows.add(new AnalyticsViews.PlagiarismRiskRow(a.getId(), a.getTitle(),
                        ap.getProblemId(), p != null ? p.getTitle() : "题目", high, mid));
            }
        }
        return rows;
    }

    // ---------- 纯聚合（可单测） ----------
    /** 每名在册学生该作业总分（bestScore=AC 最高分，无 AC 记 0）。 */
    static Map<Long, Integer> totalScores(List<Long> studentIds, List<AssignmentProblem> aps,
                                          List<Submission> subs) {
        Map<Long, Integer> totals = new HashMap<>();
        studentIds.forEach(id -> totals.put(id, 0));
        Map<Long, List<Submission>> byAp = subs.stream()
                .filter(s -> !isPending(s))
                .collect(Collectors.groupingBy(Submission::getAssignmentProblemId));
        for (AssignmentProblem ap : aps) {
            List<Submission> apSubs = byAp.getOrDefault(ap.getId(), List.of());
            Map<Long, Integer> bestAc = new HashMap<>();
            for (Submission s : apSubs) {
                int v = Submission.STATUS_AC.equals(s.getStatus()) && s.getScore() != null
                        ? s.getScore() : 0;
                bestAc.merge(s.getUserId(), v, Math::max);
            }
            for (Map.Entry<Long, Integer> e : bestAc.entrySet()) {
                totals.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return totals;
    }

    static double average(Map<Long, Integer> totals) {
        if (totals.isEmpty()) {
            return 0.0;
        }
        double sum = totals.values().stream().mapToInt(Integer::intValue).sum();
        return Math.round(sum / totals.size() * 10) / 10.0;
    }

    static long submittedCount(List<AssignmentProblem> aps, List<Submission> subs) {
        Set<Long> apIds = aps.stream().map(AssignmentProblem::getId).collect(Collectors.toSet());
        return subs.stream().filter(s -> !isPending(s) && apIds.contains(s.getAssignmentProblemId()))
                .map(Submission::getUserId).distinct().count();
    }

    static List<AnalyticsViews.Bucket> bucketCounts(Map<Long, Integer> totals) {
        long[] c = new long[5];
        for (int v : totals.values()) {
            if (v < 60) {
                c[0]++;
            } else if (v < 70) {
                c[1]++;
            } else if (v < 80) {
                c[2]++;
            } else if (v < 90) {
                c[3]++;
            } else {
                c[4]++;
            }
        }
        List<String> labels = List.of("0-59", "60-69", "70-79", "80-89", "90-100");
        List<AnalyticsViews.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            buckets.add(new AnalyticsViews.Bucket(labels.get(i), c[i]));
        }
        return buckets;
    }

    static boolean isPending(Submission s) {
        return Submission.STATUS_PENDING.equals(s.getStatus())
                || Submission.STATUS_RUNNING.equals(s.getStatus());
    }

    // ---------- 私有装配 ----------
    private Course ownedCourse(long teacherUid, long courseId) {
        Course c = courseMapper.selectById(courseId);
        if (c == null || !Objects.equals(c.getTeacherId(), teacherUid)) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return c;
    }

    private Assignment ownedAssignment(long teacherUid, long assignmentId) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        ownedCourse(teacherUid, a.getCourseId());
        return a;
    }

    private List<Long> activeStudentIds(long courseId) {
        return enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getCourseId, courseId)
                        .eq(Enrollment::getStatus, Enrollment.STATUS_ACTIVE))
                .stream().map(Enrollment::getStudentId).filter(Objects::nonNull).toList();
    }

    private List<Assignment> assignmentsOf(long courseId) {
        return assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getCourseId, courseId)
                .orderByAsc(Assignment::getId));
    }

    private List<AssignmentProblem> assignmentProblemsOf(long assignmentId) {
        return assignmentProblemMapper.selectList(new LambdaQueryWrapper<AssignmentProblem>()
                .eq(AssignmentProblem::getAssignmentId, assignmentId)
                .orderByAsc(AssignmentProblem::getSortOrder));
    }

    private List<Submission> submissionsOf(List<AssignmentProblem> aps) {
        if (aps.isEmpty()) {
            return List.of();
        }
        List<Long> ids = aps.stream().map(AssignmentProblem::getId).toList();
        return submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .in(Submission::getAssignmentProblemId, ids));
    }

    private Map<Long, String> problemTitles(List<AssignmentProblem> aps) {
        List<Long> pids = aps.stream().map(AssignmentProblem::getProblemId).distinct().toList();
        if (pids.isEmpty()) {
            return Map.of();
        }
        return problemMapper.selectBatchIds(pids).stream()
                .collect(Collectors.toMap(Problem::getId, Problem::getTitle));
    }
}
