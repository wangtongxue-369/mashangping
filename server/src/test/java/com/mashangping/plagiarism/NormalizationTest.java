package com.mashangping.plagiarism;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 归一化口径：改名/排版/注释不改变 token 流；改字符串会改变；四种语言可用。 */
class NormalizationTest {

    private TreeSitterCodeParser parser;

    @BeforeEach
    void setUp() {
        parser = new TreeSitterCodeParser();
    }

    @Test
    void cRenamedVariablesProduceIdenticalTokens() {
        String a = "int main(void){int total=0;int n=5;total=n+total;printf(\"%d\",total);return 0;}";
        String b = "int main(void){int sum=0;int k=7;sum=k+sum;printf(\"%d\",sum);return 0;}";
        List<String> ta = parser.parse("C", a);
        List<String> tb = parser.parse("C", b);
        assertThat(ta).isNotEmpty();
        assertThat(ta).isEqualTo(tb);
    }

    @Test
    void whitespaceAndCommentsDoNotChangeTokens() {
        String plain = "int add(int a,int b){return a+b;}\nint main(){return add(1,2);}\n";
        String padded = "/* 注释 */\n  int   add(int a, int b){\n    // 行注释\n    return a + b ;\n  }\nint main() { return add(1, 2); }\n";
        assertThat(parser.parse("C", plain)).isEqualTo(parser.parse("C", padded));
    }

    @Test
    void changingStringLiteralChangesTokens() {
        String a = "int main(){printf(\"hello\");return 0;}";
        String b = "int main(){printf(\"world\");return 0;}";
        assertThat(parser.parse("C", a)).isNotEqualTo(parser.parse("C", b));
    }

    @Test
    void javaRenamedProducesIdenticalTokens() {
        String a = "public class Main { public static void main(String[] args) { int sum = 0; sum = sum + 1; System.out.println(sum); } }";
        String b = "public class Main { public static void main(String[] args) { int tot = 0; tot = tot + 9; System.out.println(tot); } }";
        List<String> ta = parser.parse("JAVA", a);
        List<String> tb = parser.parse("JAVA", b);
        assertThat(ta).isNotEmpty();
        assertThat(ta).isEqualTo(tb);
    }

    @Test
    void pythonRenamedProducesIdenticalTokens() {
        String a = "def add(a, b):\n    return a + b\nprint(add(1, 2))\n";
        String b = "def sum_(x, y):\n    return x + y\nprint(sum_(1, 2))\n";
        assertThat(parser.parse("PYTHON", a)).isEqualTo(parser.parse("PYTHON", b));
    }

    @Test
    void cppParses() {
        List<String> tokens = parser.parse("CPP", "#include <iostream>\nint main(){int x=0;std::cout<<x;return 0;}");
        assertThat(tokens).isNotEmpty();
    }

    @Test
    void brokenCodeStillYieldsTokensOrEmptyWithoutThrowing() {
        // 容错：半截/语法错误代码不得抛异常（error 节点跳过）
        List<String> tokens = parser.parse("C", "int main( { int a = ; return");
        assertThat(tokens).isNotNull();
        List<String> empty = parser.parse("C", "");
        assertThat(empty).isEmpty();
    }
}
