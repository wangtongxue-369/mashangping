package com.mashangping.course;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("course")
public class Course {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String term;
    private String description;
    private Long teacherId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
