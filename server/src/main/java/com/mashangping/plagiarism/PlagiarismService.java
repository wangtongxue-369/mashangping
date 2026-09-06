package com.mashangping.plagiarism;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.judging.Submission;
import com.mashangping.judging.SubmissionMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 同题同班代码查重：取每生最高分提交（AC 优先，同分取新）→ 归一化指纹（提交不可变，进程内缓存）
 * → 两两 Jaccard 相似度 → 报告。归属校验：作业属当前教师之课，越权一律 40400。
 */
@Service
@RequiredArgsConstructor
public class PlagiarismService {

    /** 参与比对的提交须为判题终态（PENDING/RUNNING 不算）。 */
    private static final Set<String> NON_TERMINAL = Set.of(Submission.STATUS_PENDING, Submission.STATUS_RUNNING);

    /** 指纹缓存上限（学生数×重查有限，够用即可） */
    private static final int FINGERPRINT_CACHE_MAX = 4096;

    private final AssignmentMapper assignmentMapper;
    private final CourseMapper courseMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final ProblemMapper problemMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;
    private final CodeParser codeParser;
    private final SimilarityEngine engine = new SimilarityEngine();

    private final Map<Long, CodeFingerprint> fingerprintCache =
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CodeFingerprint> eldest) {
                    return size() > FINGERPRINT_CACHE_MAX;
                }
            };

    /** 生成某作业某题的查重报告（仅教师本人课程下的作业）。 */
    public PlagiarismViews.Report report(long teacherUid, long assignmentId, long problemId,
                                         double includeMin, double highThreshold) {
        AssignmentProblem ap = ownedProblem(teacherUid, assignmentId, problemId);
        List<Submission> all = submissionMapper.selectList(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getAssignmentProblemId, ap.getId()));
        // 仅终态参与；按学生分组取最高分
        Map<Long, Submission> chosen = new LinkedHashMap<>();
        for (Submission s : all) {
            if (s.getUserId() == null || NON_TERMINAL.contains(s.getStatus())) {
                continue;
            }
            Submission prev = chosen.get(s.getUserId());
            if (prev == null || isBetter(s, prev)) {
                chosen.put(s.getUserId(), s);
            }
        }
        if (chosen.isEmpty()) {
            String title = problemTitle(problemId);
            return new PlagiarismViews.Report(assignmentId, problemId, title, highThreshold, 0, List.of());
        }
        Map<Long, User> users = usersOf(chosen.keySet());
        List<StudentEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, Submission> e : chosen.entrySet()) {
            User u = users.get(e.getKey());
            Submission s = e.getValue();
            if (u == null) {
                continue;
            }
            CodeFingerprint fp = fingerprintOf(s);
            if (!fp.valid()) {
                continue; // 解析不出有效结构（空/纯异常），不参与互比
            }
            entries.add(new StudentEntry(toStudentInfo(u, s), fp));
        }
        List<PlagiarismViews.PairItem> items = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                StudentEntry ea = entries.get(i);
                StudentEntry eb = entries.get(j);
                double sim = engine.similarity(ea.fp, eb.fp);
                if (sim < includeMin) {
                    continue;
                }
                String flag = sim >= highThreshold ? "HIGH" : "MID";
                PlagiarismViews.StudentInfo first = ea.info.studentId() <= eb.info.studentId() ? ea.info : eb.info;
                PlagiarismViews.StudentInfo second = first == ea.info ? eb.info : ea.info;
                items.add(new PlagiarismViews.PairItem(first, second, sim, flag));
            }
        }
        items.sort(Comparator.comparingDouble(PlagiarismViews.PairItem::similarity).reversed());
        return new PlagiarismViews.Report(assignmentId, problemId, problemTitle(problemId),
                highThreshold, entries.size(), items);
    }

    /** 两名学生最高分提交的源码对比（供前端左右分栏）。 */
    public PlagiarismViews.CompareView compare(long teacherUid, long assignmentId, long problemId,
                                               long submissionA, long submissionB) {
        AssignmentProblem ap = ownedProblem(teacherUid, assignmentId, problemId);
        Submission a = submissionMapper.selectById(submissionA);
        Submission b = submissionMapper.selectById(submissionB);
        if (a == null || b == null || !belongsTo(a, assignmentId, ap) || !belongsTo(b, assignmentId, ap)) {
            throw new BizException(ErrorCode.NOT_FOUND, "提交不存在");
        }
        Map<Long, User> users = usersOf(Set.of(a.getUserId(), b.getUserId()));
        User ua = users.get(a.getUserId());
        User ub = users.get(b.getUserId());
        if (ua == null || ub == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return new PlagiarismViews.CompareView(toStudentInfo(ua, a), toStudentInfo(ub, b),
                a.getCode(), b.getCode());
    }

    /** 归属 + 定位作业题：非属主教师或题不在作业中一律 40400（隐藏存在性）。 */
    private AssignmentProblem ownedProblem(long teacherUid, long assignmentId, long problemId) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        Course c = courseMapper.selectById(a.getCourseId());
        if (c == null || !Objects.equals(c.getTeacherId(), teacherUid)) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        AssignmentProblem ap = assignmentProblemMapper.selectOne(new LambdaQueryWrapper<AssignmentProblem>()
                .eq(AssignmentProblem::getAssignmentId, assignmentId)
                .eq(AssignmentProblem::getProblemId, problemId));
        if (ap == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不在本作业中");
        }
        return ap;
    }

    private boolean belongsTo(Submission s, long assignmentId, AssignmentProblem ap) {
        return Objects.equals(s.getAssignmentId(), assignmentId)
                && Objects.equals(s.getAssignmentProblemId(), ap.getId())
                && Objects.equals(s.getProblemId(), ap.getProblemId());
    }

    /** 取样排序键：AC 优先 → 分高优先（未判分按 -1）→ 提交新优先。包可见供单测。 */
    static boolean isBetter(Submission candidate, Submission current) {
        if (ac(candidate) != ac(current)) {
            return ac(candidate);
        }
        int sc = candidate.getScore() == null ? -1 : candidate.getScore();
        int cc = current.getScore() == null ? -1 : current.getScore();
        if (sc != cc) {
            return sc > cc;
        }
        LocalDateTime at = candidate.getSubmittedAt();
        LocalDateTime ct = current.getSubmittedAt();
        return at != null && (ct == null || at.isAfter(ct));
    }

    private static boolean ac(Submission s) {
        return Submission.STATUS_AC.equals(s.getStatus());
    }

    private CodeFingerprint fingerprintOf(Submission s) {
        synchronized (fingerprintCache) {
            CodeFingerprint fp = fingerprintCache.get(s.getId());
            if (fp == null) {
                fp = CodeFingerprint.of(codeParser.parse(s.getLanguage(), s.getCode()));
                fingerprintCache.put(s.getId(), fp);
            }
            return fp;
        }
    }

    private Map<Long, User> usersOf(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private PlagiarismViews.StudentInfo toStudentInfo(User u, Submission s) {
        return new PlagiarismViews.StudentInfo(u.getId(),
                u.getStudentNo() != null ? u.getStudentNo() : u.getUsername(),
                u.getRealName() != null ? u.getRealName() : u.getUsername(),
                s.getLanguage(), s.getScore(), ac(s), s.getId());
    }

    private String problemTitle(long problemId) {
        Problem p = problemMapper.selectById(problemId);
        return p != null ? p.getTitle() : String.valueOf(problemId);
    }

    private record StudentEntry(PlagiarismViews.StudentInfo info, CodeFingerprint fp) {
    }

}
