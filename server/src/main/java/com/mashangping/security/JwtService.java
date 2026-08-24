package com.mashangping.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiry;

    /** 生产/装配用构造器：从配置读取密钥，有效期固定 24h（规格 §9.2） */
    @Autowired
    public JwtService(@Value("${msp.jwt.secret}") String secret) {
        this(secret, Duration.ofHours(24));
    }

    /** 测试用构造器：可注入有效期（传负数秒即得过期分支） */
    JwtService(String secret, Duration expiry) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiry = expiry;
    }

    public String generate(TokenPayload payload) {
        Date now = new Date();
        return Jwts.builder()
                .subject(payload.username())
                .claim("uid", payload.uid())
                .claim("role", payload.role())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry.toMillis()))
                .signWith(key)
                .compact();
    }

    public TokenPayload parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        // 注意坑：小数值会被 Jackson 反序列化为 Integer，
        // 直接 claims.get("uid", Long.class) 可能抛 ClassCastException
        long uid = ((Number) claims.get("uid")).longValue();
        return new TokenPayload(uid, claims.getSubject(), claims.get("role", String.class));
    }
}
