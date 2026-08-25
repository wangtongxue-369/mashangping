package com.mashangping.course;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("enrollment")
public class Enrollment {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courseId;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private String status;
    private LocalDateTime enrolledAt;
}
