package com.mashangping.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.security.UserAuthCache;
import com.mashangping.user.dto.UserCreateRequest;
import com.mashangping.user.dto.UserView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> VALID_ROLES =
            Set.of(User.ROLE_ADMIN, User.ROLE_TEACHER, User.ROLE_STUDENT);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserAuthCache userAuthCache;

    public User createUser(UserCreateRequest request) {
        if (!VALID_ROLES.contains(request.role())) {
            throw new BizException(ErrorCode.PARAM_INVALID, "非法角色：" + request.role());
        }
        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRealName(request.realName());
        user.setStudentNo(request.studentNo());
        user.setRole(request.role());
        user.setEnabled(true);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发兜底：唯一键冲突也归一到"用户名已存在"
            throw new BizException(ErrorCode.DUPLICATE_USERNAME);
        }
        return user;
    }

    public Page<UserView> listUsers(String role, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (role != null && !role.isBlank()) {
            wrapper.eq(User::getRole, role);
        }
        wrapper.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        // MP 3.5.7 的 IPage.convert 返回 IPage<R>，但其默认实现是原地 setRecords 并返回 this，
        // 故强转回 Page 是安全的；显式类型见证以满足编译器
        @SuppressWarnings("unchecked")
        Page<UserView> converted = (Page<UserView>) result.convert(UserView::from);
        return converted;
    }

    public void updateStatus(long id, boolean enabled) {
        User user = requireUser(id);
        user.setEnabled(enabled);
        userMapper.updateById(user);
        userAuthCache.invalidate(id);
    }

    public void resetPassword(long id, String newPassword) {
        User user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        userAuthCache.invalidate(id);
    }

    private User requireUser(long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
