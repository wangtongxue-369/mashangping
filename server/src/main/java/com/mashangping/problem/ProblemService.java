package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.common.LikeUtils;
import com.mashangping.problem.dto.ProblemUpsertRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProblemService {

    private static final int MAX_DESCRIPTION_BYTES = 131072; // 128KB

    private final ProblemMapper problemMapper;
    private final TestCaseMapper testCaseMapper;
    private final CourseProblemMapper courseProblemMapper;

    public Problem create(long teacherUid, ProblemUpsertRequest request) {
        checkDescriptionBytes(request);
        Problem p = new Problem();
        p.setTeacherId(teacherUid);
        p.setTitle(request.title());
        p.setDescription(request.description());
        p.setAllowedLanguages(Languages.normalize(request.allowedLanguages()));
        p.setTimeLimitMs(request.timeLimitMs() != null ? request.timeLimitMs() : 1000);
        p.setMemoryLimitMb(request.memoryLimitMb() != null ? request.memoryLimitMb() : 256);
        p.setIsPublic(Boolean.TRUE.equals(request.isPublic()));
        problemMapper.insert(p);
        return p;
    }

    public Page<ProblemSummaryView> listMine(long teacherUid, int page, int size, String keyword) {
        // keyword 可为 null：先判空归一，避免 .like 实参求值时 null.trim() 空指针
        boolean hasKw = keyword != null && !keyword.isBlank();
        String kw = hasKw ? LikeUtils.escapeForLike(keyword.trim()) : null;
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getTeacherId, teacherUid)
                .like(hasKw, Problem::getTitle, kw)
                .orderByDesc(Problem::getId);
        Page<Problem> result = problemMapper.selectPage(new Page<>(page, size), wrapper);
        // convert 返回 IPage，需按既有 CourseService 模式显式强转
        return (Page<ProblemSummaryView>) result.convert(ProblemSummaryView::from);
    }

    /** 归属链校验的唯一入口：不存在或非属主一律 40400「题目不存在」 */
    public Problem getOwned(long teacherUid, long problemId) {
        Problem p = problemMapper.selectById(problemId);
        if (p == null || p.getTeacherId() == null || p.getTeacherId() != teacherUid) {
            throw new BizException(ErrorCode.NOT_FOUND, "题目不存在");
        }
        return p;
    }

    public Problem detail(long teacherUid, long problemId) {
        return getOwned(teacherUid, problemId);
    }

    public void update(long teacherUid, long problemId, ProblemUpsertRequest request) {
        Problem p = getOwned(teacherUid, problemId);
        checkDescriptionBytes(request);
        // 部分更新语义（规格「传什么改什么」）：可选字段缺省=保持原值；
        // 语言传空数组=恢复全支持，缺省=不变
        p.setTitle(request.title());
        p.setDescription(request.description());
        if (request.allowedLanguages() != null) {
            p.setAllowedLanguages(Languages.normalize(request.allowedLanguages()));
        }
        if (request.timeLimitMs() != null) {
            p.setTimeLimitMs(request.timeLimitMs());
        }
        if (request.memoryLimitMb() != null) {
            p.setMemoryLimitMb(request.memoryLimitMb());
        }
        if (request.isPublic() != null) {
            p.setIsPublic(request.isPublic());
        }
        problemMapper.updateById(p);
    }

    /** 物理删除：级联删测试点；被任一课程引用则 40015 */
    @Transactional
    public void delete(long teacherUid, long problemId) {
        getOwned(teacherUid, problemId);
        Long refs = courseProblemMapper.selectCount(
                new LambdaQueryWrapper<CourseProblem>().eq(CourseProblem::getProblemId, problemId));
        if (refs != null && refs > 0) {
            throw new BizException(ErrorCode.PROBLEM_IN_USE);
        }
        testCaseMapper.delete(new LambdaQueryWrapper<TestCase>().eq(TestCase::getProblemId, problemId));
        problemMapper.deleteById(problemId);
    }

    /** 题面字节上限两条路径共用；title/description 由 @NotBlank 保证非空 */
    private void checkDescriptionBytes(ProblemUpsertRequest request) {
        if (request.description() != null
                && request.description().getBytes(StandardCharsets.UTF_8).length > MAX_DESCRIPTION_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "题面超出 128KB 上限");
        }
    }
}
