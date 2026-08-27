package com.mashangping.judging;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学生提交：作业路径带 assignment 锚点计分，练习路径双锚为 NULL 不计分 */
@Data
@TableName("submission")
public class Submission {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_AC = "AC";
    public static final String STATUS_WA = "WA";
    public static final String STATUS_TLE = "TLE";
    public static final String STATUS_MLE = "MLE";
    public static final String STATUS_RE = "RE";
    public static final String STATUS_CE = "CE";
    public static final String STATUS_SYSTEM_ERROR = "SYSTEM_ERROR";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long problemId;
    private Long userId;
    private Long assignmentId;
    private Long assignmentProblemId;
    private String language;
    private String code;
    private String status;
    /** 练习恒 NULL；作业路径 AC=题分 否则 0 */
    private Integer score;
    private Integer passedCount;
    private Integer totalCount;
    private Integer timeUsedMs;
    private Integer memoryUsedMb;
    private Boolean isLate;
    private LocalDateTime submittedAt;
}
