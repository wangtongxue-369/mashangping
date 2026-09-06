package com.mashangping.plagiarism;

import java.util.List;
import java.util.Set;

/**
 * 单份代码的归一化指纹：token 数 + 5-gram 滚动窗口哈希集合。
 * 提交源码不可变 ⇒ 指纹可按 submissionId 进程内缓存。
 */
public final class CodeFingerprint {

    private static final int WINDOW = 5;

    private final List<String> tokens;
    private final Set<Integer> shingles;

    private CodeFingerprint(List<String> tokens, Set<Integer> shingles) {
        this.tokens = tokens;
        this.shingles = shingles;
    }

    public static CodeFingerprint of(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return new CodeFingerprint(List.of(), Set.of());
        }
        Set<Integer> set = new java.util.HashSet<>();
        if (tokens.size() >= WINDOW) {
            for (int i = 0; i + WINDOW <= tokens.size(); i++) {
                set.add(hashWindow(tokens, i));
            }
        }
        return new CodeFingerprint(List.copyOf(tokens), Set.copyOf(set));
    }

    private static int hashWindow(List<String> tokens, int from) {
        int h = 1;
        for (int i = from; i < from + WINDOW; i++) {
            h = 31 * h + tokens.get(i).hashCode();
        }
        return h;
    }

    /** 是否可参与比对（token 非空）。 */
    public boolean valid() {
        return !tokens.isEmpty();
    }

    public int tokenCount() {
        return tokens.size();
    }

    public Set<Integer> shingles() {
        return shingles;
    }

    public List<String> tokens() {
        return tokens;
    }
}
