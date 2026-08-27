-- 计划4 作业域：assignment 作业 + assignment_problem 作业选题（引用 course_problem 范围内题目）
CREATE TABLE `assignment` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `course_id`    BIGINT       NOT NULL COMMENT '属主课程',
    `title`        VARCHAR(200) NOT NULL,
    `description`  MEDIUMTEXT   NULL COMMENT 'Markdown 作业说明，可选，≤128KB',
    `start_at`     DATETIME     NOT NULL,
    `due_at`       DATETIME     NOT NULL,
    `late_days`    INT          NOT NULL DEFAULT 0 COMMENT '迟交宽限天数 0~7',
    `is_published` TINYINT      NOT NULL DEFAULT 0 COMMENT '1=对学生可见',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_assignment_course` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `assignment_problem` (
    `id`            BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `assignment_id` BIGINT   NOT NULL,
    `problem_id`    BIGINT   NOT NULL,
    `score`         INT      NOT NULL COMMENT '该题分值 1~10000',
    `sort_order`    INT      NOT NULL DEFAULT 0 COMMENT '按选入顺序赋值',
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_assignment_problem` (`assignment_id`, `problem_id`),
    KEY `idx_assignment_problem_pid` (`problem_id`),
    CONSTRAINT `fk_ap_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `assignment`(`id`),
    CONSTRAINT `fk_ap_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 计划3绑定义务同车：course_problem 补外键兜底（应用层保护先行，FK 只防绕过应用的脏数据）
ALTER TABLE `course_problem`
    ADD CONSTRAINT `fk_cp_course`  FOREIGN KEY (`course_id`)  REFERENCES `course`(`id`),
    ADD CONSTRAINT `fk_cp_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem`(`id`);
