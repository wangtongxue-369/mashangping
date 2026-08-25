-- 学生学号唯一：身份锚点（教师/管理员 student_no 为 NULL，唯一索引允许多 NULL）
ALTER TABLE `user`
    ADD UNIQUE KEY `uk_user_student_no` (`student_no`);

CREATE TABLE `email_verification` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `email`      VARCHAR(100) NOT NULL,
    `code`       CHAR(6)      NOT NULL COMMENT '6位纯数字',
    `purpose`    VARCHAR(20)  NOT NULL DEFAULT 'REGISTER',
    `expires_at` DATETIME     NOT NULL COMMENT '15分钟有效期',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `used`       TINYINT      NOT NULL DEFAULT 0,
    KEY `idx_emailverify_email` (`email`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `course` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(100) NOT NULL COMMENT '课程名',
    `term`        VARCHAR(50)  NOT NULL COMMENT '学期，如 2025-2026-1',
    `description` VARCHAR(500) NULL,
    `teacher_id`  BIGINT       NOT NULL COMMENT '授课教师，单师制',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_course_teacher` (`teacher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `enrollment` (
    `id`           BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `course_id`    BIGINT      NOT NULL,
    `student_id`   BIGINT      NULL COMMENT '已激活关联账号；待激活为NULL',
    `student_no`   VARCHAR(30) NOT NULL COMMENT '身份锚点',
    `student_name` VARCHAR(50) NOT NULL COMMENT '拉人时姓名快照，供教师核对',
    `status`       VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACTIVE',
    `enrolled_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_enrollment_course_no` (`course_id`, `student_no`),
    KEY `idx_enrollment_student_id` (`student_id`),
    KEY `idx_enrollment_pending_no` (`student_no`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
