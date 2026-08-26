package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 公开题库只读查询；非公开题一律 40400 隐藏存在性 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;

    public Page<PracticeViews.Summary> list(int page, int size, String keyword) {
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getIsPublic, true)
                .like(keyword != null && !keyword.isBlank(), Problem::getTitle, keyword)
                .orderByDesc(Problem::getId);
        Page<Problem> result = problemMapper.selectPage(new Page<>(page, size), wrapper);
        // convert 返回 IPage，需按既有 CourseService 模式显式强转
        return (Page<PracticeViews.Summary>) result.convert(PracticeViews.Summary::from);
    }

    public PracticeViews.Detail detail(long problemId) {
        Problem p = problemMapper.selectById(problemId);
        // 非公开/不存在统一 40400：对学生隐藏一切私有题的存在性
        if (p == null || !Boolean.TRUE.equals(p.getIsPublic())) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        List<PracticeViews.Sample> samples = testCaseMapper
                .selectList(new LambdaQueryWrapper<TestCase>()
                        .eq(TestCase::getProblemId, problemId)
                        .eq(TestCase::getIsSample, true)
                        .orderByAsc(TestCase::getId))
                .stream()
                .map(tc -> new PracticeViews.Sample(tc.getInput(), tc.getExpectedOutput()))
                .toList();
        return new PracticeViews.Detail(p.getId(), p.getTitle(), p.getDescription(),
                Languages.parse(p.getAllowedLanguages()), p.getTimeLimitMs(), p.getMemoryLimitMb(),
                samples);
    }
}
