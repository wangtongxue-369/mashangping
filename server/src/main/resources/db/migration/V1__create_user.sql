CREATE TABLE `user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `username`      VARCHAR(50)  NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL,
    `real_name`     VARCHAR(50)  NOT NULL,
    `student_no`    VARCHAR(30)  NULL COMMENT '学号，教师/管理员可为空',
    `role`          VARCHAR(20)  NOT NULL COMMENT 'ADMIN/TEACHER/STUDENT',
    `enabled`       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
