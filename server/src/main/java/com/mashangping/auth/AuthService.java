package com.mashangping.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mashangping.auth.dto.LoginRequest;
import com.mashangping.auth.dto.LoginResponse;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.security.JwtService;
import com.mashangping.security.TokenPayload;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import com.mashangping.user.dto.UserView;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.username()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            // 用户不存在与密码错误同提示，防用户名枚举
            throw new BizException(ErrorCode.BAD_CREDENTIALS);
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        String token = jwtService.generate(new TokenPayload(user.getId(), user.getUsername(), user.getRole()));
        return new LoginResponse(token, UserView.from(user));
    }
}
