package com.mashangping.emailverify;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_verification")
public class EmailVerification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String code;
    private String purpose;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private Boolean used;
}
