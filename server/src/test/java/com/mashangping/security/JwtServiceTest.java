package com.mashangping.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef"; // 32字节
    private final JwtService jwtService = new JwtService(SECRET, Duration.ofHours(24));

    @Test
    void generate_then_parse_roundtrip() {
        String token = jwtService.generate(new TokenPayload(42L, "alice", "TEACHER"));
        TokenPayload parsed = jwtService.parse(token);
        assertThat(parsed.uid()).isEqualTo(42L);
        assertThat(parsed.username()).isEqualTo("alice");
        assertThat(parsed.role()).isEqualTo("TEACHER");
    }

    @Test
    void expired_token_rejected() {
        JwtService shortLived = new JwtService(SECRET, Duration.ofSeconds(-5));
        String token = shortLived.generate(new TokenPayload(1L, "bob", "STUDENT"));
        assertThatThrownBy(() -> shortLived.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tampered_token_rejected() {
        String token = jwtService.generate(new TokenPayload(1L, "carol", "STUDENT"));
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void short_secret_rejected_at_construction() {
        assertThatThrownBy(() -> new JwtService("too-short", Duration.ofMinutes(1)))
                .isInstanceOf(WeakKeyException.class);
    }
}
