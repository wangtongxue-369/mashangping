package com.mashangping.security;

import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 无状态过滤链中的认证过滤器。
 * 规格 D1（实时吊销）：token 只用于识别 uid，账号状态与角色以数据库为准；
 * 快照经 UserAuthCache 微缓存（30s），管理操作后主动失效即刻生效。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAuthCache userAuthCache;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                TokenPayload payload = jwtService.parse(header.substring(7));
                User user = userAuthCache.get(payload.uid());
                if (user == null) {
                    user = userMapper.selectById(payload.uid());
                    if (user != null) {
                        userAuthCache.put(payload.uid(), user);
                    }
                }
                if (user != null && Boolean.TRUE.equals(user.getEnabled())) {
                    var authorities =
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
                    var authentication = new UsernamePasswordAuthenticationToken(
                            payload, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    // 账号不存在或已停用：实时吊销
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
