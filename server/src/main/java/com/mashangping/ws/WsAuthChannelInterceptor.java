package com.mashangping.ws;

import com.mashangping.security.JwtService;
import com.mashangping.security.TokenPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CONNECT 帧鉴权：Authorization 头携带 Bearer JWT；
 * 验签通过则把 WsUserPrincipal 绑进 accessor（后续帧/投递定向都靠它），
 * 非 CONNECT 帧一律原样放行。失败抛出即由 STOMP 协议层回 ERROR 帧。
 */
@Component
@RequiredArgsConstructor
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }
        List<String> auth = accessor.getNativeHeader("Authorization");
        if (auth == null || auth.isEmpty()) {
            throw new IllegalArgumentException("missing bearer token");
        }
        String first = auth.get(0); // List.getFirst 是 JDK21 API，本工程 Java 17
        if (!first.startsWith("Bearer ")) {
            throw new IllegalArgumentException("missing bearer token");
        }
        TokenPayload payload = jwtService.parse(first.substring(7).trim());
        accessor.setUser(new WsUserPrincipal(payload.uid(), payload.username()));
        return message;
    }
}
