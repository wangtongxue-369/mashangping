package com.mashangping.problem;

import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 语言键白名单：允许集合固定为 C/CPP/JAVA/PYTHON，空=null 表示全支持 */
public final class Languages {

    public static final List<String> ALL = List.of("C", "CPP", "JAVA", "PYTHON");

    private Languages() {
    }

    /** 入参归一化为 CSV 存储：空白/空列表→null（全支持）；含非法键→PARAM_INVALID；去重保序 */
    public static String normalize(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return null;
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String raw : requested) {
            String key = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (!ALL.contains(key)) {
                throw new BizException(ErrorCode.PARAM_INVALID, "不支持的语言：" + raw);
            }
            cleaned.add(key);
        }
        return cleaned.size() == ALL.size() ? null : String.join(",", cleaned);
    }

    /** 存储值解析回列表：null→全支持全集 */
    public static List<String> parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return ALL;
        }
        return Arrays.stream(stored.split(",")).map(String::trim).toList();
    }
}
