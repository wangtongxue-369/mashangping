package com.mashangping.register;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mashangping.auth.dto.LoginResponse;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.course.Enrollment;
import com.mashangping.course.EnrollmentMapper;
import com.mashangping.register.dto.RegisterRequest;
import com.mashangping.security.JwtService;
import com.mashangping.security.TokenPayload;
import com.mashangping.user.User;
import com.mashangping.user.UserMapper;
import com.mashangping.user.dto.UserView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterService {

    private final VerificationCodeService verificationCodeService;
    private final UserMapper userMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 注册即登录：验码 → 建号(STUDENT, username=学号) → 自动激活所有预置名单 → 签发 token。
     * 并发同学号由 uk_user_student_no / uk_user_username 兜底，冲突归一为 40013。
     */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        verificationCodeService.verify(request.email(), request.code());

        Long occupied = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, request.studentNo()));
        if (occupied != null && occupied > 0) {
            throw new BizException(ErrorCode.STUDENT_NO_CONFLICT);
        }

        User user = new User();
        user.setUsername(request.studentNo());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setStudentNo(request.studentNo());
        user.setEmail(request.email());
        user.setRole(User.ROLE_STUDENT);
        user.setEnabled(true);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发窗口兜底：唯一键冲突统一按"学号已被占用"对外表达
            throw new BizException(ErrorCode.STUDENT_NO_CONFLICT);
        }

        // 自动激活：该学号在各门课的 PENDING 名单一并转正
        int activated = enrollmentMapper.update(null, new LambdaUpdateWrapper<Enrollment>()
                .eq(Enrollment::getStudentNo, request.studentNo())
                .eq(Enrollment::getStatus, Enrollment.STATUS_PENDING)
                .set(Enrollment::getStudentId, user.getId())
                .set(Enrollment::getStatus, Enrollment.STATUS_ACTIVE));
        if (activated > 0) {
            log.info("注册自动激活：学号={} 激活 {} 门课程名单", request.studentNo(), activated);
        }

        String token = jwtService.generate(
                new TokenPayload(user.getId(), user.getUsername(), user.getRole()));
        return new LoginResponse(token, UserView.from(user));
    }
}
