package com.mashangping.plagiarism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 相似度引擎语义：同码=1、改名同构≈1、不同算法显著低、短代码兜底∈[0,1]、幂等。 */
class SimilarityTest {

    private final TreeSitterCodeParser parser = new TreeSitterCodeParser();
    private final SimilarityEngine engine = new SimilarityEngine();

    private double sim(String codeA, String codeB) {
        return engine.similarity(
                CodeFingerprint.of(parser.parse("C", codeA)),
                CodeFingerprint.of(parser.parse("C", codeB)));
    }

    @Test
    void identicalCodeIsOne() {
        String code = "int main(){int a=1,b=2;printf(\"%d\",a+b);return 0;}";
        assertThat(sim(code, code)).isEqualTo(1.0);
    }

    @Test
    void renamedCloneIsNearlyOne() {
        String a = "int main(){int total=0;for(int i=0;i<10;i++){total+=i;}printf(\"%d\",total);return 0;}";
        String b = "int main(){int res=0;for(int j=0;j<10;j++){res+=j;}printf(\"%d\",res);return 0;}";
        assertThat(sim(a, b)).isGreaterThanOrEqualTo(0.99);
    }

    @Test
    void differentAlgorithmsAreLow() {
        // 循环求和 vs 公式求和
        String loop = "int main(){int s=0;for(int i=1;i<=100;i++){s+=i;}printf(\"%d\",s);return 0;}";
        String formula = "int main(){int n=100;int s=n*(n+1)/2;printf(\"%d\",s);return 0;}";
        assertThat(sim(loop, formula)).isLessThan(0.5);
    }

    @Test
    void commentAndWhitespaceDiffStillOne() {
        String a = "int main(){int x=1;printf(\"%d\",x);return 0;}";
        String b = "/* note */\nint main(){\n int x=1;\n printf(\"%d\", x);\n return 0;\n}\n";
        assertThat(sim(a, b)).isEqualTo(1.0);
    }

    @Test
    void shortCodeGuardInRange() {
        String a = "int main(){return 0;}";
        String b = "int main(){return 1;}";
        double s = sim(a, b);
        assertThat(s).isBetween(0.0, 1.0);
    }

    @Test
    void fingerprintIsIdempotent() {
        String code = "int main(){int a=1;return a;}";
        CodeFingerprint f1 = CodeFingerprint.of(parser.parse("C", code));
        CodeFingerprint f2 = CodeFingerprint.of(parser.parse("C", code));
        assertThat(f1.shingles()).isEqualTo(f2.shingles());
        assertThat(f1.tokenCount()).isEqualTo(f2.tokenCount());
        assertThat(f1.valid()).isTrue();
    }
}
