package com.mashangping.plagiarism;

import java.util.List;

/** 源码 → 归一化结构 token 流（抗改名/排版/注释；字符串字面量保留原文）。 */
public interface CodeParser {

    /**
     * 解析指定语言的源码，返回归一化 token 列表（顺序即结构）。
     *
     * @param languageKey 判题语言键 C / CPP / JAVA / PYTHON
     * @param code        源码原文
     */
    List<String> parse(String languageKey, String code);
}
