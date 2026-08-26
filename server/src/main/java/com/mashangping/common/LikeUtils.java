package com.mashangping.common;

/** LIKE 通配符转义：把用户关键字中的 % _ \\ 转为字面量，避免被当通配符。MySQL LIKE 默认转义符即反斜杠 */
public final class LikeUtils {

    private LikeUtils() {
    }

    public static String escapeForLike(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
