package com.mashangping.common;

/** 分页参数钳制：page≥1，size∈[1,50] 越界取边界；所有分页端点统一使用 */
public final class PageUtils {

    public static final int MAX_SIZE = 50;

    private PageUtils() {
    }

    public static int page(int page) {
        return Math.max(1, page);
    }

    public static int size(int size) {
        return Math.min(Math.max(1, size), MAX_SIZE);
    }
}
