package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.problem.dto.TestCaseUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class TestCaseService {

    static final int MAX_CASES_PER_PROBLEM = 50;
    static final int MAX_FIELD_BYTES = 65536; // 64KB

    private final ProblemService problemService;
    private final TestCaseMapper testCaseMapper;

    public TestCase create(long uid, long problemId, TestCaseUpsertRequest request) {
        problemService.getOwned(uid, problemId);
        enforceCountLimit(problemId);
        enforceByteLimit(request);

        TestCase tc = new TestCase();
        tc.setProblemId(problemId);
        applyUpsert(tc, request);
        testCaseMapper.insert(tc);
        return tc;
    }

    public void update(long uid, long problemId, long testCaseId, TestCaseUpsertRequest request) {
        problemService.getOwned(uid, problemId);
        TestCase tc = requireCaseOfProblem(problemId, testCaseId);
        // 数量上限不查：更新不增加数量，满员仍可编辑（字节上限两路共用）
        enforceByteLimit(request);
        applyUpsert(tc, request);
        testCaseMapper.updateById(tc);
    }

    public void delete(long uid, long problemId, long testCaseId) {
        problemService.getOwned(uid, problemId);
        TestCase tc = requireCaseOfProblem(problemId, testCaseId);
        testCaseMapper.deleteById(tc.getId());
    }

    private TestCase requireCaseOfProblem(long problemId, long testCaseId) {
        TestCase tc = testCaseMapper.selectById(testCaseId);
        // 用例不属于该题（含不存在）一律 40400，隐藏其他题目的用例存在性
        if (tc == null || tc.getProblemId() == null || tc.getProblemId() != problemId) {
            throw new BizException(ErrorCode.NOT_FOUND, "测试点不存在");
        }
        return tc;
    }

    /** 仅 create 调用：更新不增加数量，不得以数量上限阻塞既有测试点编辑 */
    private void enforceCountLimit(long problemId) {
        Long count = testCaseMapper.selectCount(
                new LambdaQueryWrapper<TestCase>().eq(TestCase::getProblemId, problemId));
        if (count != null && count >= MAX_CASES_PER_PROBLEM) {
            throw new BizException(ErrorCode.PARAM_INVALID, "每题最多 " + MAX_CASES_PER_PROBLEM + " 个测试点");
        }
    }

    private void enforceByteLimit(TestCaseUpsertRequest request) {
        checkFieldBytes("输入", request.input());
        checkFieldBytes("期望输出", request.expectedOutput());
    }

    private void checkFieldBytes(String label, String value) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > MAX_FIELD_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID, label + "超过 64KB 上限");
        }
    }

    private void applyUpsert(TestCase tc, TestCaseUpsertRequest request) {
        tc.setInput(request.input());
        tc.setExpectedOutput(request.expectedOutput());
        tc.setIsSample(Boolean.TRUE.equals(request.isSample()));
    }
}
