package com.mashangping.security;

import com.mashangping.user.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAuthCacheTest {

    private User user(long id) {
        User u = new User();
        u.setId(id);
        u.setRole("STUDENT");
        u.setEnabled(true);
        return u;
    }

    @Test
    void put_then_get_hit() {
        UserAuthCache cache = new UserAuthCache(60_000, 100);
        cache.put(1L, user(1L));
        assertThat(cache.get(1L)).isNotNull();
    }

    @Test
    void expired_entry_returns_null() {
        UserAuthCache cache = new UserAuthCache(-1, 100); // TTL为负=立即过期
        cache.put(1L, user(1L));
        assertThat(cache.get(1L)).isNull();
    }

    @Test
    void invalidate_forces_miss() {
        UserAuthCache cache = new UserAuthCache(60_000, 100);
        cache.put(1L, user(1L));
        cache.invalidate(1L);
        assertThat(cache.get(1L)).isNull();
    }

    @Test
    void exceeding_max_entries_clears_all() {
        UserAuthCache cache = new UserAuthCache(60_000, 3);
        for (long i = 1; i <= 4; i++) {
            cache.put(i, user(i));
        }
        assertThat(cache.get(4L)).isNotNull();  // 最新写入仍在
        assertThat(cache.get(1L)).isNull();     // 旧的全清
    }
}
