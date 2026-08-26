-- 计划3 题库域：题目挂教师属主，课程引用式选题；终审遗留项 user.email 同车
ALTER TABLE `user`
    ADD COLUMN `email` VARCHAR(100) NULL,
    ADD UNIQUE KEY `uk_user_email` (`email`);

CREATE TABLE `problem` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `teacher_id`        BIGINT       NOT NULL COMMENT '属主教师',
    `title`             VARCHAR(200) NOT NULL,
    `description`       MEDIUMTEXT   NOT NULL COMMENT 'Markdown 原文，由前端安全渲染',
    `allowed_languages` VARCHAR(100) NULL COMMENT '逗号分隔语言键；NULL=全支持',
    `time_limit_ms`     INT          NOT NULL DEFAULT 1000,
    `memory_limit_mb`   INT          NOT NULL DEFAULT 256,
    `is_public`         TINYINT      NOT NULL DEFAULT 0 COMMENT '1=进入平台题库供自由练习',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY `idx_problem_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `test_case` (
    `id`              BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `problem_id`      BIGINT   NOT NULL,
    `input`           TEXT     NOT NULL COMMENT 'UTF-8 ≤64KB',
    `expected_output` TEXT     NOT NULL COMMENT 'UTF-8 ≤64KB',
    `is_sample`       TINYINT  NOT NULL DEFAULT 0 COMMENT '样例点=学生可见完整输入输出',
    `created_at`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_testcase_problem` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `course_problem` (
    `id`         BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `course_id`  BIGINT   NOT NULL,
    `problem_id` BIGINT   NOT NULL,
    `sort_order` INT      NOT NULL DEFAULT 0 COMMENT '本期按选入顺序赋值',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_course_problem` (`course_id`, `problem_id`),
    KEY `idx_course_problem_pid` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
