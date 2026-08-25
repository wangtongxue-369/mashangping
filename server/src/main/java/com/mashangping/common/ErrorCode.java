package com.mashangping.common;

public enum ErrorCode {
    PARAM_INVALID(40000, "参数不合法"),
    DUPLICATE_USERNAME(40001, "用户名已存在"),
    BAD_CREDENTIALS(40002, "用户名或密码错误"),
    USER_DISABLED(40003, "账号已停用"),
    UNAUTHORIZED(40100, "未登录或登录已失效"),
    FORBIDDEN(40300, "无权访问"),
    NOT_FOUND(40400, "资源不存在"),
    EMAIL_DOMAIN_NOT_ALLOWED(40010, "邮箱域不在学校白名单"),
    EMAIL_CODE_INVALID(40011, "验证码无效或已过期"),
    EMAIL_CODE_RATE_LIMITED(40012, "验证码发送过于频繁，请稍后再试"),
    STUDENT_NO_CONFLICT(40013, "该学号已被占用"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
