package com.mashangping.judging;

import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import com.mashangping.problem.Languages;

/** 判题语言矩阵：编译命令/运行命令/时限放大系数/源码文件名。键集合与 Languages.ALL 对齐 */
public enum JudgeLanguage {

    C(new String[]{"gcc", "-O2", "main.c", "-o", "main"}, new String[]{"./main"}, 1, "main.c"),
    CPP(new String[]{"g++", "-std=c++17", "-O2", "main.cpp", "-o", "main"},
        new String[]{"./main"}, 1, "main.cpp"),
    JAVA(new String[]{"javac", "Main.java"}, new String[]{"java", "Main"}, 2, "Main.java"),
    PYTHON(new String[]{"python", "-m", "py_compile", "main.py"},
           new String[]{"python", "main.py"}, 1, "main.py");

    private final String[] compileCommand;
    private final String[] runCommand;
    private final int timeMultiplier;
    private final String sourceFileName;

    JudgeLanguage(String[] compileCommand, String[] runCommand,
                  int timeMultiplier, String sourceFileName) {
        this.compileCommand = compileCommand;
        this.runCommand = runCommand;
        this.timeMultiplier = timeMultiplier;
        this.sourceFileName = sourceFileName;
    }

    public String[] compileCommand() { return compileCommand; }
    public String[] runCommand() { return runCommand; }
    public int timeMultiplier() { return timeMultiplier; }
    public String sourceFileName() { return sourceFileName; }

    public static JudgeLanguage of(String key) {
        if (key != null && Languages.ALL.contains(key.trim().toUpperCase(java.util.Locale.ROOT))) {
            return valueOf(key.trim().toUpperCase(java.util.Locale.ROOT));
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "不支持的语言：" + key);
    }
}
