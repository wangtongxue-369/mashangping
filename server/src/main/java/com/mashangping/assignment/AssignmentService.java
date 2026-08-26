package com.mashangping.assignment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.assignment.dto.AssignmentUpsertRequest;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.course.CourseService;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssignmentService {

    private static final int MAX_DESCRIPTION_BYTES = 131072; // 128KB

    private final CourseService courseService;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentProblemMapper assignmentProblemMapper;
    private final ProblemMapper problemMapper;

    public Assignment create(long teacherUid, long courseId, AssignmentUpsertRequest request) {
        courseService.getOwned(teacherUid, courseId);
        requireStartBeforeDue(request.startAt(), request.dueAt());
        checkDescriptionBytes(request);
        Assignment a = new Assignment();
        a.setCourseId(courseId);
        a.setTitle(request.title());
        a.setDescription(request.description());
        a.setStartAt(request.startAt());
        a.setDueAt(request.dueAt());
        a.setLateDays(request.lateDays() != null ? request.lateDays() : 0);
        a.setIsPublished(Boolean.TRUE.equals(request.isPublished()));
        assignmentMapper.insert(a);
        return a;
    }

    public Page<AssignmentViews.TeacherListItem> listMine(long teacherUid, long courseId, int page, int size) {
        courseService.getOwned(teacherUid, courseId);
        Page<Assignment> result = assignmentMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Assignment>()
                        .eq(Assignment::getCourseId, courseId)
                        .orderByAsc(Assignment::getDueAt));
        List<Assignment> rows = result.getRecords();
        List<Long> ids = rows.stream().map(Assignment::getId).toList();
        Map<Long, List<AssignmentProblem>> byAssignment = ids.isEmpty() ? Map.of()
                : assignmentProblemMapper.selectList(new LambdaQueryWrapper<AssignmentProblem>()
                        .in(AssignmentProblem::getAssignmentId, ids))
                        .stream().collect(Collectors.groupingBy(AssignmentProblem::getAssignmentId));
        LocalDateTime now = LocalDateTime.now();
        Page<AssignmentViews.TeacherListItem> views =
                new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        views.setRecords(rows.stream().map(a -> {
            List<AssignmentProblem> aps = byAssignment.getOrDefault(a.getId(), List.of());
            int totalScore = aps.stream().mapToInt(AssignmentProblem::getScore).sum();
            return new AssignmentViews.TeacherListItem(a.getId(), a.getTitle(),
                    a.getStartAt(), a.getDueAt(), a.getLateDays(),
                    Boolean.TRUE.equals(a.getIsPublished()), aps.size(), totalScore,
                    AssignmentStatus.of(now, a.getStartAt(), a.getDueAt(), a.getLateDays()).name());
        }).collect(Collectors.toList()));
        return views;
    }

    /** 归属链校验唯一入口：作业不存在或课程非属主一律 40400「作业不存在」 */
    public Assignment getOwned(long teacherUid, long assignmentId) {
        Assignment a = assignmentMapper.selectById(assignmentId);
        if (a == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        }
        courseService.getOwned(teacherUid, a.getCourseId());
        return a;
    }

    public AssignmentViews.TeacherDetail detail(long teacherUid, long assignmentId) {
        Assignment a = getOwned(teacherUid, assignmentId);
        List<AssignmentViews.TeacherDetail.ProblemItem> items = assignmentProblemMapper
                .selectList(new LambdaQueryWrapper<AssignmentProblem>()
                        .eq(AssignmentProblem::getAssignmentId, assignmentId)
                        .orderByAsc(AssignmentProblem::getSortOrder))
                .stream()
                .map(ap -> {
                    Problem p = problemMapper.selectById(ap.getProblemId());
                    return new AssignmentViews.TeacherDetail.ProblemItem(
                            ap.getProblemId(), p != null ? p.getTitle() : "",
                            ap.getScore(), ap.getSortOrder());
                })
                .toList();
        return new AssignmentViews.TeacherDetail(a.getId(), a.getCourseId(), a.getTitle(),
                a.getDescription(), a.getStartAt(), a.getDueAt(), a.getLateDays(),
                Boolean.TRUE.equals(a.getIsPublished()),
                AssignmentStatus.of(LocalDateTime.now(), a.getStartAt(), a.getDueAt(),
                        a.getLateDays()).name(),
                items);
    }

    public void update(long teacherUid, long assignmentId, AssignmentUpsertRequest request) {
        Assignment a = getOwned(teacherUid, assignmentId);
        requireStartBeforeDue(request.startAt(), request.dueAt());
        checkDescriptionBytes(request);
        a.setTitle(request.title());
        a.setDescription(request.description());
        a.setStartAt(request.startAt());
        a.setDueAt(request.dueAt());
        if (request.lateDays() != null) {
            a.setLateDays(request.lateDays());
        }
        if (request.isPublished() != null) {
            a.setIsPublished(request.isPublished());
        }
        assignmentMapper.updateById(a);
    }

    /** 物理删除：级联删 assignment_problem 行。有提交后收紧条款见规格 §8（届时补 COUNT 检查） */
    @Transactional
    public void delete(long teacherUid, long assignmentId) {
        getOwned(teacherUid, assignmentId);
        assignmentProblemMapper.delete(new LambdaQueryWrapper<AssignmentProblem>()
                .eq(AssignmentProblem::getAssignmentId, assignmentId));
        assignmentMapper.deleteById(assignmentId);
    }

    private void requireStartBeforeDue(LocalDateTime startAt, LocalDateTime dueAt) {
        if (startAt == null || dueAt == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "开始与结束时间均必填");
        }
        if (!dueAt.isAfter(startAt)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "结束时间必须晚于开始时间");
        }
    }

    private void checkDescriptionBytes(AssignmentUpsertRequest request) {
        if (request.description() != null
                && request.description().getBytes(StandardCharsets.UTF_8).length > MAX_DESCRIPTION_BYTES) {
            throw new BizException(ErrorCode.PARAM_INVALID, "作业说明超出 128KB 上限");
        }
    }
}
