package com.mashangping.judging;

/** 可重试的基础设施异常：daemon 失联/镜像缺失/容器起不来等 */
public class InfraBrokenException extends RuntimeException {

    public InfraBrokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
