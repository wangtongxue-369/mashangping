package com.mashangping.assignment;

import com.mashangping.course.EnrollmentMapper;
import com.mashangping.problem.Problem;
import com.mashangping.problem.ProblemMapper;
import com.mashangping.problem.TestCaseMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 学生题目视图补返 assignmentProblemId 的结构/装配断言（纯单元，无 Docker）。
 * 任务 0：支撑学生端提交作业题时携带作业锚（双锚互斥中的 assignmentProblemId）。
 */
class AssignmentViewsFieldTest {

    @Test
    void studentProblemItem_record_contains_assignmentProblemId() {
        List<String> fields = recordFields(AssignmentViews.StudentProblemItem.class);
        assertThat(fields).contains("assignmentProblemId");
    }

    @Test
    void studentProblemDetail_record_contains_assignmentProblemId() {
        List<String> fields = recordFields(AssignmentViews.StudentProblemDetail.class);
        assertThat(fields).contains("assignmentProblemId");
    }

    @Test
    void problems_assemble_assignmentProblemId_from_ap_id() {
        long assignmentId = 7L;
        long problemId = 10L;
        long apId = 42L;

        Assignment a = new Assignment();
        a.setId(assignmentId);
        a.setCourseId(3L);
        a.setIsPublished(true);
        a.setStartAt(LocalDateTime.now().minusHours(1));

        Problem p = new Problem();
        p.setId(problemId);
        p.setTitle("题一");
        p.setAllowedLanguages(null);
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);

        AssignmentProblem ap = new AssignmentProblem();
        ap.setId(apId);
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(problemId);
        ap.setScore(10);
        ap.setSortOrder(1);

        EnrollmentMapper enrollmentMapper = mock(EnrollmentMapper.class);
        AssignmentMapper assignmentMapper = mock(AssignmentMapper.class);
        AssignmentProblemMapper assignmentProblemMapper = mock(AssignmentProblemMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);

        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        when(assignmentMapper.selectById(assignmentId)).thenReturn(a);
        when(assignmentProblemMapper.selectList(any())).thenReturn(List.of(ap));
        when(problemMapper.selectBatchIds(any())).thenReturn(List.of(p));

        StudentAssignmentService service = new StudentAssignmentService(
                enrollmentMapper, assignmentMapper, assignmentProblemMapper, null, problemMapper, null);

        List<AssignmentViews.StudentProblemItem> items = service.problems(1L, 3L, assignmentId);
        assertThat(items).hasSize(1);
        assertThat(items.get(0).assignmentProblemId()).isEqualTo(apId);
        assertThat(items.get(0).problemId()).isEqualTo(problemId);
    }

    @Test
    void problemDetail_assemble_assignmentProblemId_from_ap_id() {
        long assignmentId = 7L;
        long problemId = 10L;
        long apId = 99L;

        Assignment a = new Assignment();
        a.setId(assignmentId);
        a.setCourseId(3L);
        a.setIsPublished(true);
        a.setStartAt(LocalDateTime.now().minusHours(1));

        Problem p = new Problem();
        p.setId(problemId);
        p.setTitle("题一");
        p.setDescription("描述");
        p.setAllowedLanguages(null);
        p.setTimeLimitMs(1000);
        p.setMemoryLimitMb(256);

        AssignmentProblem ap = new AssignmentProblem();
        ap.setId(apId);
        ap.setAssignmentId(assignmentId);
        ap.setProblemId(problemId);
        ap.setScore(10);

        EnrollmentMapper enrollmentMapper = mock(EnrollmentMapper.class);
        AssignmentMapper assignmentMapper = mock(AssignmentMapper.class);
        AssignmentProblemMapper assignmentProblemMapper = mock(AssignmentProblemMapper.class);
        ProblemMapper problemMapper = mock(ProblemMapper.class);
        TestCaseMapper testCaseMapper = mock(TestCaseMapper.class);

        when(enrollmentMapper.selectCount(any())).thenReturn(1L);
        when(assignmentMapper.selectById(assignmentId)).thenReturn(a);
        when(assignmentProblemMapper.selectOne(any())).thenReturn(ap);
        when(problemMapper.selectById(problemId)).thenReturn(p);
        when(testCaseMapper.selectList(any())).thenReturn(List.of());

        StudentAssignmentService service = new StudentAssignmentService(
                enrollmentMapper, assignmentMapper, assignmentProblemMapper, null, problemMapper, testCaseMapper);

        AssignmentViews.StudentProblemDetail detail =
                service.problemDetail(1L, 3L, assignmentId, problemId);
        assertThat(detail.assignmentProblemId()).isEqualTo(apId);
        assertThat(detail.problemId()).isEqualTo(problemId);
        assertThat(detail.score()).isEqualTo(10);
    }

    private static List<String> recordFields(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
