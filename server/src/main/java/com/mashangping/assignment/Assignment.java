package com.mashangping.assignment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assignment")
public class Assignment {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 属主课程：归属链经 CourseService.getOwned 校验 */
    private Long courseId;
    private String title;
    /** Markdown 作业说明，可选，≤128KB */
    private String description;
    private LocalDateTime startAt;
    private LocalDateTime dueAt;
    /** 迟交宽限天数 0~7；宽限截止 = dueAt + lateDays 天，不冗余存列 */
    private Integer lateDays;
    /** 1=对学生可见；创建默认 0（草稿） */
    private Boolean isPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
