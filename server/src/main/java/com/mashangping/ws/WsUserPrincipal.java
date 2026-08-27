package com.mashangping.ws;

/** WS 会话身份：name 即 uid 字符串（convertAndSendToUser 按 name 定向） */
public record WsUserPrincipal(long uid, String username) implements java.security.Principal {
    @Override
    public String getName() {
        return String.valueOf(uid);
    }
}
