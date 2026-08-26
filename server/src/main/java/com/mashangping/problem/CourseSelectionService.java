package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.course.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseSelectionService {

    private final CourseService courseService;
    private final ProblemService problemService;
    private final CourseProblemMapper courseProblemMapper;
    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;

    /** 选入：双重归属——先课程门再题门（只能选自己名下的题），重复选 40402 */
    public void select(long uid, long courseId, long problemId) {
        courseService.getOwned(uid, courseId);
        problemService.getOwned(uid, problemId);
        Long dup = courseProblemMapper.selectCount(new LambdaQueryWrapper<CourseProblem>()
                .eq(CourseProblem::getCourseId, courseId)
                .eq(CourseProblem::getProblemId, problemId));
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.COURSE_PROBLEM_DUPLICATE);
        }
        CourseProblem cp = new CourseProblem();
        cp.setCourseId(courseId);
        cp.setProblemId(problemId);
        cp.setSortOrder(nextSortOrder(courseId));
        try {
            courseProblemMapper.insert(cp);
        } catch (DuplicateKeyException e) {
            // 并发兜底：两请求同时选同一题越过 pre-check，唯一约束冲突归一 40402
            throw new BizException(ErrorCode.COURSE_PROBLEM_DUPLICATE);
        }
    }

    public Page<CourseProblemView> list(long uid, long courseId, int page, int size) {
        courseService.getOwned(uid, courseId);
        List<CourseProblem> relations = courseProblemMapper.selectList(
                new LambdaQueryWrapper<CourseProblem>()
                        .eq(CourseProblem::getCourseId, courseId)
                        .orderByAsc(CourseProblem::getSortOrder));
        if (relations.isEmpty()) {
            // 空课程提前返回：否则下方 IN 条件短路省略后 selectList 会把整张 test_case 表载入内存
            return new Page<>(page, size, 0);
        }
        List<Long> ids = relations.stream().map(CourseProblem::getProblemId).toList();
        Map<Long, Problem> problems = ids.isEmpty() ? Map.of()
                : problemMapper.selectBatchIds(ids).stream()
                        .collect(Collectors.toMap(Problem::getId, Function.identity()));
        List<Map<String, Object>> countRows = testCaseMapper.selectMaps(
                new QueryWrapper<TestCase>()
                        .select("problem_id", "COUNT(*) AS cnt")
                        .in(!ids.isEmpty(), "problem_id", ids)
                        .groupBy("problem_id"));
        Map<Long, Long> caseCounts = countRows.stream().collect(Collectors.toMap(
                row -> ((Number) row.get("problem_id")).longValue(),
                row -> ((Number) row.get("cnt")).longValue()));

        List<CourseProblemView> rows = relations.stream()
                .map(cp -> {
                    Problem p = problems.get(cp.getProblemId());
                    if (p == null) {
                        return null; // 关联行存在但题目已消失（异常数据）：跳过不炸
                    }
                    return new CourseProblemView(cp.getProblemId(), p.getTitle(),
                            Languages.parse(p.getAllowedLanguages()),
                            p.getTimeLimitMs(), p.getMemoryLimitMb(),
                            Boolean.TRUE.equals(p.getIsPublic()),
                            caseCounts.getOrDefault(cp.getProblemId(), 0L),
                            cp.getSortOrder());
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(CourseProblemView::sortOrder))
                .toList();

        Page<CourseProblemView> result = new Page<>(page, size, rows.size());
        int from = Math.min((page - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());
        result.setRecords(rows.subList(from, to));
        return result;
    }

    /** 移出：只删关联不动题 */
    public void remove(long uid, long courseId, long problemId) {
        courseService.getOwned(uid, courseId);
        courseProblemMapper.delete(new LambdaQueryWrapper<CourseProblem>()
                .eq(CourseProblem::getCourseId, courseId)
                .eq(CourseProblem::getProblemId, problemId));
    }

    private int nextSortOrder(long courseId) {
        return courseProblemMapper.selectList(new LambdaQueryWrapper<CourseProblem>()
                        .eq(CourseProblem::getCourseId, courseId))
                .stream()
                .mapToInt(CourseProblem::getSortOrder)
                .max()
                .orElse(0) + 1;
    }
}
