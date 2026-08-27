package com.mashangping.ws;

import com.mashangping.IntegrationTestBase;
import com.mashangping.security.JwtService;
import com.mashangping.security.TokenPayload;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class WsAuthInterceptorTest extends IntegrationTestBase {

    @Autowired UserMapper userMapper; @Autowired JwtService jwtService;
    @Autowired WsAuthChannelInterceptor interceptor;

    long uid;

    private MessageChannel mockChannel() {
        // spring-messaging 6.x 中唯一抽象方法是 send(Message,long)，单参 lambda 无法编译
        return (msg, timeout) -> true;
    }

    @BeforeEach
    void seed() {
        User u = new User();
        u.setUsername("stuW" + System.nanoTime());
        u.setPasswordHash(passwordEncoder.encode("pw123456"));
        u.setRealName("ws测"); u.setRole(User.ROLE_STUDENT); u.setEnabled(true);
        userMapper.insert(u);
        uid = u.getId();
    }

    private Message<byte[]> connectWith(String token) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (token != null) {
            acc.addNativeHeader("Authorization", token);
        }
        acc.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
    }

    /** 合法 JWT 的 CONNECT：preSend 放行且 user 注入为 WsUserPrincipal(uid=本人) */
    @Test
    void valid_jwt_connect_passes_and_binds_principal() {
        String jwt = jwtService.generate(new TokenPayload(uid, "stuW", "STUDENT"));
        Message<?> msg = interceptor.preSend(connectWith("Bearer " + jwt), mockChannel());
        assertThat(msg).isNotNull();
        SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.wrap(msg);
        assertThat(acc.getUser()).isInstanceOf(WsUserPrincipal.class);
        assertThat(((WsUserPrincipal) acc.getUser()).getName())
                .isEqualTo(String.valueOf(uid));
    }

    @Test
    void missing_or_bad_token_connect_rejected() {
        // 缺 token：直连异常路径（STOMP 层将以 ERROR 帧回应客户端）
        assertThrows(Exception.class,
                () -> interceptor.preSend(connectWith(null), mockChannel()));
        assertThrows(Exception.class,
                () -> interceptor.preSend(connectWith("Bearer garbage"), mockChannel()));
    }

    @Test
    void non_connect_frames_pass_through() {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setDestination("/user/queue/judge-progress");
        acc.setLeaveMutable(true);
        Message<byte[]> m = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        assertThat(interceptor.preSend(m, mockChannel())).isSameAs(m);
    }

    /**
     * 握手不再被 HTTP 认证层拦死（浏览器原生 WS 无法自设 Authorization 头）：
     * 非 401 即契约成立——未升级的普通 GET 由端点侧以 400 等方式拒绝，与认证无关。
     */
    @Test
    void ws_handshake_not_blocked_by_http_auth_layer() throws Exception {
        mockMvc.perform(get("/ws/judge"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotEqualTo(401));
    }
}
