package com.mashangping.user.dto;

import com.mashangping.user.User;

/** 用户出参视图：绝不包含密码哈希。全平台复用。 */
public record UserView(long id, String username, String realName, String studentNo, String role) {

    public static UserView from(User u) {
        return new UserView(u.getId(), u.getUsername(), u.getRealName(), u.getStudentNo(), u.getRole());
    }
}
