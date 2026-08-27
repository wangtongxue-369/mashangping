package com.mashangping.judging;

import com.mashangping.common.BizException;
import com.mashangping.common.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgeLanguageTest {

    @Test
    void c_cpp_command_matrix() {
        assertThat(JudgeLanguage.C.compileCommand()).containsExactly("gcc", "-O2", "main.c", "-o", "main");
        assertThat(JudgeLanguage.C.runCommand()).containsExactly("./main");
        assertThat(JudgeLanguage.CPP.compileCommand())
                .containsExactly("g++", "-std=c++17", "-O2", "main.cpp", "-o", "main");
    }

    @Test
    void java_multiplier_is_two_and_python_compiles_via_py_compile() {
        assertThat(JudgeLanguage.JAVA.timeMultiplier()).isEqualTo(2);
        assertThat(JudgeLanguage.C.timeMultiplier()).isEqualTo(1);
        assertThat(JudgeLanguage.PYTHON.compileCommand())
                .containsExactly("python", "-m", "py_compile", "main.py");
        assertThat(JudgeLanguage.PYTHON.runCommand()).containsExactly("python", "main.py");
    }

    @Test
    void source_file_names_follow_main_class_convention() {
        assertThat(JudgeLanguage.C.sourceFileName()).isEqualTo("main.c");
        assertThat(JudgeLanguage.CPP.sourceFileName()).isEqualTo("main.cpp");
        assertThat(JudgeLanguage.JAVA.sourceFileName()).isEqualTo("Main.java");
        assertThat(JudgeLanguage.PYTHON.sourceFileName()).isEqualTo("main.py");
    }

    @Test
    void of_parses_stored_keys_and_rejects_others() {
        assertThat(JudgeLanguage.of("JAVA")).isEqualTo(JudgeLanguage.JAVA);
        assertThat(JudgeLanguage.of("cpp")).isEqualTo(JudgeLanguage.CPP);
        assertThatThrownBy(() -> JudgeLanguage.of("RUST"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode().getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }
}
