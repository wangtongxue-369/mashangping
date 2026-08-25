package com.mashangping.course;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final CourseService courseService;
    private final EnrollmentMapper enrollmentMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;

    /** 单行处理结果：供单人添加与Excel批量共用（Task 7） */
    record ProcessOutcome(Kind kind, Enrollment row, String failReason) {
        enum Kind { ACTIVATED, PENDING, SKIPPED_DUPLICATE, FAILED }
    }

    public EnrollmentView addStudent(long teacherUid, long courseId, String studentNo,
                                     String studentName) {
        courseService.getOwned(teacherUid, courseId);
        ProcessOutcome outcome = processSingle(courseId, studentNo, studentName);
        switch (outcome.kind()) {
            case FAILED -> throw new BizException(ErrorCode.PARAM_INVALID, outcome.failReason());
            case SKIPPED_DUPLICATE -> throw new BizException(ErrorCode.ENROLLMENT_DUPLICATE);
            default -> { /* ACTIVATED / PENDING 正常返回 */ }
        }
        return toView(outcome.row());
    }

    /**
     * 单行核心逻辑：格式校验 → 查重 → 查账号定状态。
     * 参数应为调用方 trim 过的原值；格式不符归 FAILED（批量场景不抛出）。
     */
    ProcessOutcome processSingle(long courseId, String rawNo, String rawName) {
        String no = rawNo == null ? "" : rawNo.trim();
        String name = rawName == null ? "" : rawName.trim();
        if (!no.matches("\\w{3,30}")) {
            return new ProcessOutcome(ProcessOutcome.Kind.FAILED, null, "学号须为3~30位字母/数字/下划线");
        }
        if (name.isEmpty() || name.length() > 50) {
            return new ProcessOutcome(ProcessOutcome.Kind.FAILED, null, "姓名不能为空且不超过50字");
        }
        Long dup = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId)
                .eq(Enrollment::getStudentNo, no));
        if (dup != null && dup > 0) {
            return new ProcessOutcome(ProcessOutcome.Kind.SKIPPED_DUPLICATE, null, null);
        }
        User account = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, no));
        Enrollment e = new Enrollment();
        e.setCourseId(courseId);
        e.setStudentNo(no);
        e.setStudentName(name);
        if (account != null) {
            if (!User.ROLE_STUDENT.equals(account.getRole())) {
                return new ProcessOutcome(ProcessOutcome.Kind.FAILED, null, "该学号对应非学生账号");
            }
            e.setStudentId(account.getId());
            e.setStatus(Enrollment.STATUS_ACTIVE);
        } else {
            e.setStatus(Enrollment.STATUS_PENDING);
        }
        enrollmentMapper.insert(e);
        return new ProcessOutcome(
                account != null ? ProcessOutcome.Kind.ACTIVATED : ProcessOutcome.Kind.PENDING,
                e, null);
    }

    public Page<EnrollmentView> list(long teacherUid, long courseId, String status,
                                     String keyword, int page, int size) {
        courseService.getOwned(teacherUid, courseId);
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getCourseId, courseId)
                .eq(status != null && !status.isBlank(), Enrollment::getStatus, status)
                .orderByAsc(Enrollment::getId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            // keyword 命中账号实名的学生集合（ACTIVE 行的第三匹配路径）
            List<Long> matchedUserIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                            .eq(User::getRole, User.ROLE_STUDENT)
                            .like(User::getRealName, kw))
                    .stream().map(User::getId).toList();
            wrapper.and(w -> {
                w.like(Enrollment::getStudentNo, kw)
                 .or().like(Enrollment::getStudentName, kw);
                if (!matchedUserIds.isEmpty()) {
                    w.or().in(Enrollment::getStudentId, matchedUserIds);
                }
            });
        }
        Page<Enrollment> result = enrollmentMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, User> accounts = loadAccounts(result.getRecords());
        Page<EnrollmentView> views = new Page<>(result.getCurrent(), result.getSize(),
                result.getTotal());
        views.setRecords(result.getRecords().stream()
                .map(e -> toView(e, accounts)).collect(Collectors.toList()));
        return views;
    }

    public void remove(long teacherUid, long courseId, long enrollmentId) {
        courseService.getOwned(teacherUid, courseId);
        Enrollment e = enrollmentMapper.selectById(enrollmentId);
        if (e == null || !Objects.equals(e.getCourseId(), courseId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        enrollmentMapper.deleteById(enrollmentId);
    }

    public List<MyCourseView> myCourses(long studentUid) {
        List<Enrollment> rows = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentUid)
                .eq(Enrollment::getStatus, Enrollment.STATUS_ACTIVE));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Course> courses = courseMapper.selectBatchIds(
                        rows.stream().map(Enrollment::getCourseId).toList())
                .stream().collect(Collectors.toMap(Course::getId, Function.identity()));
        return rows.stream()
                .filter(r -> courses.containsKey(r.getCourseId()))
                .map(r -> {
                    Course c = courses.get(r.getCourseId());
                    return new MyCourseView(c.getId(), c.getName(), c.getTerm());
                })
                .toList();
    }

    private Map<Long, User> loadAccounts(List<Enrollment> rows) {
        List<Long> uids = rows.stream().map(Enrollment::getStudentId)
                .filter(Objects::nonNull).distinct().toList();
        if (uids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(uids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private EnrollmentView toView(Enrollment e, Map<Long, User> accounts) {
        User account = e.getStudentId() == null ? null : accounts.get(e.getStudentId());
        String display = account != null ? account.getRealName() : e.getStudentName();
        return new EnrollmentView(e.getId(), e.getStudentNo(), display, e.getStatus());
    }

    private EnrollmentView toView(Enrollment e) {
        return toView(e, e.getStudentId() == null ? Map.of()
                : loadAccounts(List.of(e)));
    }

    public record MyCourseView(long courseId, String name, String term) {}
}
