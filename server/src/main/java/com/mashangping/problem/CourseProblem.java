package com.mashangping.problem;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course_problem")
public class CourseProblem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long problemId;
    /** 本期按选入顺序递增赋值，手动重排留待前端阶段 */
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
