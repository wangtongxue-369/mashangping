package com.mashangping.judging;

import java.util.ArrayList;
import java.util.List;

/** OJ 惯例输出比对：忽略每行行尾空白与文末多余空行，其余精确匹配 */
public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean compare(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private static List<String> normalize(String s) {
        List<String> lines = new ArrayList<>();
        if (s == null) {
            return lines;
        }
        for (String line : s.split("\n", -1)) {
            lines.add(stripTrailing(line));
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /** 剥单个字符的行尾空白（\t 与空格），不折返剥离内部内容 */
    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
            end--;
        }
        return line.substring(0, end);
    }
}
