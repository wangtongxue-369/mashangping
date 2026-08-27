package com.mashangping.assignment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assignment_problem")
public class AssignmentProblem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assignmentId;
    private Long problemId;
    /** 该题分值 1~10000 */
    private Integer score;
    /** 按选入顺序赋值（当前 max+1 接续） */
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
