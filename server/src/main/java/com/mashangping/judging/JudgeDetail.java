package com.mashangping.judging;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 逐测试点明细；status 仅 AC/WA/TLE/MLE/RE */
@Data
@TableName("judge_detail")
public class JudgeDetail {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long submissionId;
    private Long testCaseId;
    private Integer pointIndex;
    private String status;
    private Integer timeUsedMs;
    private Integer memoryUsedMb;
    private String message;
}
