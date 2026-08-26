package com.mashangping.assignment;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentStatusTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 26, 8, 0);
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 8, 26, 18, 0);

    @Test
    void before_start_is_not_started() {
        assertThat(AssignmentStatus.of(START.minusHours(1), START, DUE, 0))
                .isEqualTo(AssignmentStatus.NOT_STARTED);
    }

    @Test
    void between_start_and_due_is_in_progress() {
        assertThat(AssignmentStatus.of(START.plusHours(1), START, DUE, 0))
                .isEqualTo(AssignmentStatus.IN_PROGRESS);
    }

    @Test
    void at_due_boundary_with_zero_late_is_closed() {
        // lateDays=0：无宽限窗口，到点即关
        assertThat(AssignmentStatus.of(DUE, START, DUE, 0)).isEqualTo(AssignmentStatus.CLOSED);
    }

    @Test
    void inside_late_window_is_late_window() {
        assertThat(AssignmentStatus.of(DUE.plusDays(2), START, DUE, 3))
                .isEqualTo(AssignmentStatus.LATE_WINDOW);
    }

    @Test
    void beyond_late_deadline_is_closed() {
        assertThat(AssignmentStatus.of(DUE.plusDays(4), START, DUE, 3))
                .isEqualTo(AssignmentStatus.CLOSED);
    }
}
