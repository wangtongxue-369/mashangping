package com.mashangping.plagiarism;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterC;
import org.treesitter.TreeSitterCpp;
import org.treesitter.TreeSitterJava;
import org.treesitter.TreeSitterPython;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tree-sitter 实现：解析后遍历 CST，产出归一化 token 流。
 * 归一口径（spec §4.1）：
 * - 注释 / ERROR / MISSING 节点跳过（容错半截代码）；
 * - 匿名 token（运算符/关键字/标点）按原文产出——保留 a+b 与 a-b 的差异；
 * - 具名叶子：标识符 → ID、字符串/字符 → STR:原文（保留）、数值 → NUM、true/false/null → NUM/NIL；
 * - 具名内部节点按类型名（小写）产出结构 token。
 */
@Component
public class TreeSitterCodeParser implements CodeParser {

    private static final Set<String> BOOL_WORDS = Set.of("true", "false", "True", "False");
    private static final Set<String> NIL_WORDS = Set.of("null", "NULL", "None");

    @Override
    public List<String> parse(String languageKey, String code) {
        if (code == null || code.isBlank()) {
            return List.of();
        }
        TSLanguage language = languageFor(languageKey);
        TSParser parser = new TSParser();
        parser.setLanguage(language);
        // bonede API 无公开 close/delete：原生内存由绑定层管理，对象随 GC 回收
        TSTree tree = parser.parseString(null, code);
        List<String> tokens = new ArrayList<>();
        walk(tree.getRootNode(), code, tokens);
        return List.copyOf(tokens);
    }

    private static TSLanguage languageFor(String key) {
        return switch (key) {
            case "C" -> new TreeSitterC();
            case "CPP" -> new TreeSitterCpp();
            case "JAVA" -> new TreeSitterJava();
            case "PYTHON" -> new TreeSitterPython();
            default -> throw new IllegalArgumentException("不支持的语言键: " + key);
        };
    }

    private static void walk(TSNode node, String code, List<String> out) {
        if (node.isNull()) {
            return;
        }
        String type = node.getType();
        if ("comment".equals(type) || "ERROR".equals(type) || "MISSING".equals(type)) {
            return;
        }
        if (node.isNamed()) {
            if (node.getChildCount() == 0) {
                out.add(classifyNamedLeaf(node, code));
            } else {
                out.add(type.toLowerCase(Locale.ROOT));
            }
        } else {
            // 匿名 token：运算符/关键字/标点等，保留原文以区分算子差异
            out.add(type);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), code, out);
        }
    }

    private static String classifyNamedLeaf(TSNode node, String code) {
        String type = node.getType();
        if (type.contains("identifier")) {
            return "ID";
        }
        String text = nodeText(node, code);
        if (type.contains("string") || type.contains("char")) {
            return "STR:" + text;
        }
        if (isNumericText(text)) {
            return "NUM";
        }
        if (BOOL_WORDS.contains(text)) {
            return "NUM";
        }
        if (NIL_WORDS.contains(text)) {
            return "NIL";
        }
        // 其它具名叶子（罕见）按类型名产出
        return type.toLowerCase(Locale.ROOT);
    }

    private static String nodeText(TSNode node, String code) {
        int start = node.getStartByte();
        int end = node.getEndByte();
        byte[] bytes = code.getBytes(StandardCharsets.UTF_8);
        if (start < 0 || end > bytes.length || start > end) {
            return "";
        }
        return new String(bytes, start, end - start, StandardCharsets.UTF_8);
    }

    private static boolean isNumericText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.matches("^[+-]?(?:0[xX][0-9a-fA-F]+|\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");
    }
}
