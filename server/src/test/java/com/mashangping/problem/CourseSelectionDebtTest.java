package com.mashangping.problem;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.IntegrationTestBase;
import com.mashangping.assignment.Assignment;
import com.mashangping.assignment.AssignmentMapper;
import com.mashangping.assignment.AssignmentProblem;
import com.mashangping.assignment.AssignmentProblemMapper;
import com.mashangping.course.Course;
import com.mashangping.course.CourseMapper;
import com.mashangping.judging.Submission;
import com.mashangping.judging.SubmissionMapper;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseSelectionDebtTest extends IntegrationTestBase {

    @Autowired UserMapper userMapper;
    @Autowired CourseMapper courseMapper;
    @Autowired ProblemMapper problemMapper; @Autowired TestCaseMapper testCaseMapper;
    @Autowired AssignmentMapper assignmentMapper;
    @Autowired AssignmentProblemMapper assignmentProblemMapper;
    @Autowired SubmissionMapper submissionMapper;
    @Autowired CourseProblemMapper courseProblemMapper;
    @Autowired CourseSelectionService selectionService;

    long teacherUid; Course course; Problem problem;

    @BeforeEach
    void seed() {
        User t = new User();
        t.setUsername("teaD" + System.nanoTime());
        t.setPasswordHash(passwordEncoder.encode("pw123456"));
        t.setRealName("债测师"); t.setRole(User.ROLE_TEACHER); t.setEnabled(true);
        userMapper.insert(t);
        teacherUid = t.getId();

        course = new Course();
        course.setName("课D" + System.nanoTime()); course.setTerm("2025-2026-1");
        course.setTeacherId(teacherUid); courseMapper.insert(course);

        problem = new Problem();
        problem.setTeacherId(teacherUid); problem.setTitle("悬账题");
        problem.setDescription("d"); problem.setIsPublic(false);
        problem.setTimeLimitMs(1000); problem.setMemoryLimitMb(256);
        problemMapper.insert(problem);

        testCaseMapper.insert(sampleOf());
        selectionService.select(teacherUid, course.getId(), problem.getId());
    }

    private TestCase sampleOf() {
        TestCase tc = new TestCase();
        tc.setProblemId(problem.getId()); tc.setInput("1\n");
        tc.setExpectedOutput("2\n"); tc.setIsSample(true);
        return tc;
    }

    private long seedAssignmentRef(boolean published) {
        Assignment a = new Assignment();
        a.setCourseId(course.getId()); a.setTitle("作D"); a.setDescription("d");
        a.setStartAt(LocalDateTime.now().minusDays(1));
        a.setDueAt(LocalDateTime.now().plusDays(3));
        a.setLateDays(0); a.setIsPublished(published);
        assignmentMapper.insert(a);
        AssignmentProblem ap = new AssignmentProblem();
        ap.setAssignmentId(a.getId()); ap.setProblemId(problem.getId());
        ap.setScore(5); ap.setSortOrder(1);
        assignmentProblemMapper.insert(ap);
        return ap.getId();
    }

    /** 提交行需要一个真实 user_id：每次插入独立学生账号（先落库再引用，JWT 铁律同源习惯） */
    private void seedSubmission(Long apAnchor) {
        User stu = new User();
        stu.setUsername("stuD" + System.nanoTime());
        stu.setPasswordHash(passwordEncoder.encode("pw123456"));
        stu.setRealName("债测生"); stu.setRole(User.ROLE_STUDENT); stu.setEnabled(true);
        userMapper.insert(stu);

        Submission s = new Submission();
        s.setProblemId(problem.getId()); s.setUserId(stu.getId());
        s.setAssignmentId(apAnchor != null ? seedCourseAnchorId() : null);
        s.setAssignmentProblemId(apAnchor);
        s.setLanguage("C"); s.setCode("int main(){return 0;}");
        s.setStatus(Submission.STATUS_AC); s.setScore(null);
        s.setPassedCount(1); s.setTotalCount(1);
        s.setIsLate(false); s.setSubmittedAt(LocalDateTime.now());
        submissionMapper.insert(s);
    }

    private Long seedCourseAnchorId() {
        // 与任一 seedAssignmentRef 调用配对使用；此处由调用侧传入锚前已存的 assignment id
        return assignmentMapper.selectList(new LambdaQueryWrapper<Assignment>()
                .eq(Assignment::getCourseId, course.getId())
                .orderByDesc(Assignment::getId)).get(0).getId();
    }

    @Test
    void removal_blocked_40020_when_submission_exists_under_assignment_ref() {
        long apId = seedAssignmentRef(true);
        seedSubmission(apId);
        assertThatThrownBy(() -> selectionService.remove(teacherUid,
                course.getId(), problem.getId()))
                .isInstanceOf(com.mashangping.common.BizException.class)
                .extracting(e -> ((com.mashangping.common.BizException) e)
                        .getErrorCode().getCode())
                .isEqualTo(40020);
        // 行仍在：移出失败不得误删
        assertThat(courseProblemCount()).isEqualTo(1);
    }

    @Test
    void removal_still_40016_when_reference_without_submission() {
        seedAssignmentRef(true);           // 引用存在但无提交
        assertThatThrownBy(() -> selectionService.remove(teacherUid,
                course.getId(), problem.getId()))
                .isInstanceOf(com.mashangping.common.BizException.class)
                .extracting(e -> ((com.mashangping.common.BizException) e)
                        .getErrorCode().getCode())
                .isEqualTo(40016);
        assertThat(courseProblemCount()).isEqualTo(1);
    }

    @Test
    void practice_only_submission_does_not_block_removal() {
        seedSubmission(null);              // 双锚 NULL 的练习提交不应触发 40020
        selectionService.remove(teacherUid, course.getId(), problem.getId());
        assertThat(courseProblemCount()).isZero();
    }

    @Test
    void selectByIdForUpdate_smoke_returns_row_id() {
        Long locked = courseMapper.selectByIdForUpdate(course.getId());
        assertThat(locked).isEqualTo(course.getId());   // autocommit 单语句锁即刻释放的冒烟断言
    }

    private long courseProblemCount() {
        return courseProblemMapper.selectCount(new LambdaQueryWrapper<CourseProblem>()
                .eq(CourseProblem::getCourseId, course.getId()));
    }
}
