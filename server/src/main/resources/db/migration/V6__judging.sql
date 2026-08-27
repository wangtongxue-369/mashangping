-- 计划5 判题域：submission 提交 / judge_task 判题任务(DB持久化队列) / judge_detail 逐点明细
CREATE TABLE `submission` (
    `id`                    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `problem_id`            BIGINT      NOT NULL,
    `user_id`               BIGINT      NOT NULL,
    `assignment_id`         BIGINT      NULL COMMENT '练习提交为 NULL',
    `assignment_problem_id` BIGINT      NULL COMMENT '练习提交为 NULL',
    `language`              VARCHAR(10) NOT NULL COMMENT 'C/CPP/JAVA/PYTHON',
    `code`                  MEDIUMTEXT  NOT NULL COMMENT '源码原文 ≤64KB',
    `status`                VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            COMMENT 'PENDING/RUNNING/AC/WA/TLE/MLE/RE/CE/SYSTEM_ERROR',
    `score`                 INT         NULL COMMENT '练习恒NULL; 作业路径 AC=题分 否则0',
    `passed_count`          INT         NOT NULL DEFAULT 0,
    `total_count`           INT         NOT NULL DEFAULT 0,
    `time_used_ms`          INT         NULL COMMENT '各测试点最大用时',
    `memory_used_mb`        INT         NULL COMMENT '各测试点内存采样峰值最大值',
    `is_late`               TINYINT     NOT NULL DEFAULT 0,
    `submitted_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY `idx_submission_user` (`user_id`),
    KEY `idx_submission_problem` (`problem_id`),
    KEY `idx_submission_ap` (`assignment_problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `judge_task` (
    `id`            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `submission_id` BIGINT      NOT NULL,
    `status`        VARCHAR(10) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
    `retry_count`   INT         NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_judge_task_submission` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `judge_detail` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `submission_id`  BIGINT        NOT NULL,
    `test_case_id`   BIGINT        NOT NULL,
    `point_index`    INT           NOT NULL COMMENT '1-based，按 test_case.id 升序编号',
    `status`         VARCHAR(4)    NOT NULL COMMENT 'AC/WA/TLE/MLE/RE',
    `time_used_ms`   INT           NULL,
    `memory_used_mb` INT           NULL,
    `message`        VARCHAR(2000) NULL COMMENT 'RE stderr尾段；WA/正常点为NULL；读侧按可见性过滤',
    KEY `idx_detail_submission` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
