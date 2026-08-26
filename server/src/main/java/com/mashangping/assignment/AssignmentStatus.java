package com.mashangping.assignment;

import java.time.LocalDateTime;

/** 作业状态机：实时推算，不落库。lateDays=0 时无宽限窗口，到点即 CLOSED */
public enum AssignmentStatus {
    NOT_STARTED, IN_PROGRESS, LATE_WINDOW, CLOSED;

    public static AssignmentStatus of(LocalDateTime now, LocalDateTime startAt,
                                      LocalDateTime dueAt, int lateDays) {
        if (now.isBefore(startAt)) {
            return NOT_STARTED;
        }
        if (now.isBefore(dueAt)) {
            return IN_PROGRESS;
        }
        LocalDateTime lateDeadline = dueAt.plusDays(lateDays);
        if (lateDays > 0 && (now.isBefore(lateDeadline) || now.isEqual(lateDeadline))) {
            return LATE_WINDOW;
        }
        return CLOSED;
    }
}
