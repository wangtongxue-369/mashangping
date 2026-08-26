package com.mashangping.problem;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("problem")
public class Problem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teacherId;
    private String title;
    /** Markdown 原文，渲染职责在前端安全渲染器 */
    private String description;
    /** 逗号分隔语言键（C/CPP/JAVA/PYTHON 子集）；NULL=全支持 */
    private String allowedLanguages;
    private Integer timeLimitMs;
    private Integer memoryLimitMb;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
