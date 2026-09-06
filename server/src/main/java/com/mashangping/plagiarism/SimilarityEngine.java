package com.mashangping.plagiarism;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** 相似度引擎：默认 5-gram shingle Jaccard；极短代码退化为逐 token 多重集 Jaccard。 */
public final class SimilarityEngine {

    /** 短代码阈值：token 少于该数则走多重集比对（避免 shingle 退化失真）。 */
    private static final int SHORT_CODE_THRESHOLD = 6;

    /** 0~1 相似度；任一指纹无效返回 0。幂等、无副作用。 */
    public double similarity(CodeFingerprint a, CodeFingerprint b) {
        if (a == null || b == null || !a.valid() || !b.valid()) {
            return 0.0;
        }
        if (a.tokenCount() < SHORT_CODE_THRESHOLD || b.tokenCount() < SHORT_CODE_THRESHOLD) {
            return multisetJaccard(a.tokens(), b.tokens());
        }
        return setJaccard(a.shingles(), b.shingles());
    }

    private static double setJaccard(Set<Integer> xs, Set<Integer> ys) {
        if (xs.isEmpty() || ys.isEmpty()) {
            return 0.0;
        }
        Set<Integer> inter = new HashSet<>(xs);
        inter.retainAll(ys);
        Set<Integer> union = new HashSet<>(xs);
        union.addAll(ys);
        return (double) inter.size() / union.size();
    }

    private static double multisetJaccard(java.util.List<String> xs, java.util.List<String> ys) {
        Map<String, Integer> cx = count(xs);
        Map<String, Integer> cy = count(ys);
        int inter = 0;
        int union = 0;
        for (Map.Entry<String, Integer> e : cx.entrySet()) {
            int c2 = cy.getOrDefault(e.getKey(), 0);
            inter += Math.min(e.getValue(), c2);
            union += e.getValue();
        }
        for (Map.Entry<String, Integer> e : cy.entrySet()) {
            if (!cx.containsKey(e.getKey())) {
                union += e.getValue();
            }
        }
        return union == 0 ? 0.0 : (double) inter / union;
    }

    private static Map<String, Integer> count(java.util.List<String> tokens) {
        Map<String, Integer> m = new HashMap<>();
        for (String t : tokens) {
            m.merge(t, 1, Integer::sum);
        }
        return m;
    }
}
