package com.mashangping.security;

import com.mashangping.user.User;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户认证快照微缓存：过滤器每个请求用它换取"最多一次主键查询"。
 * 管理操作（停用/启用/重置密码）后须调 invalidate(uid) 即刻生效。
 */
@Component
public class UserAuthCache {

    private static final long DEFAULT_TTL_MILLIS = 30_000;
    private static final int DEFAULT_MAX_ENTRIES = 1000;

    private record Entry(User user, long expireAtMillis) {}

    private final long ttlMillis;
    private final int maxEntries;
    private final Map<Long, Entry> store = new ConcurrentHashMap<>();

    public UserAuthCache() {
        this(DEFAULT_TTL_MILLIS, DEFAULT_MAX_ENTRIES);
    }

    /** 测试用：可注入 TTL 与容量上限 */
    UserAuthCache(long ttlMillis, int maxEntries) {
        this.ttlMillis = ttlMillis;
        this.maxEntries = maxEntries;
    }

    public User get(long uid) {
        Entry e = store.get(uid);
        if (e == null) {
            return null;
        }
        if (System.currentTimeMillis() > e.expireAtMillis()) {
            store.remove(uid);
            return null;
        }
        return e.user();
    }

    public void put(long uid, User user) {
        if (store.size() >= maxEntries) {
            store.clear(); // 200人规模到不了上限；超限全清是简单正确的自愈
        }
        store.put(uid, new Entry(user, System.currentTimeMillis() + ttlMillis));
    }

    public void invalidate(long uid) {
        store.remove(uid);
    }
}
