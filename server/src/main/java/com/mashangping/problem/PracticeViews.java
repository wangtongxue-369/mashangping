package com.mashangping.problem;

import java.util.List;

/** practice 出参集合：物理上不含任何隐藏点字段，杜绝泄漏面 */
public final class PracticeViews {

    public record Summary(long id, String title, List<String> languages,
                          int timeLimitMs, int memoryLimitMb) {
        public static Summary from(Problem p) {
            return new Summary(p.getId(), p.getTitle(), Languages.parse(p.getAllowedLanguages()),
                    p.getTimeLimitMs(), p.getMemoryLimitMb());
        }
    }

    public record Sample(String input, String output) {}

    public record Detail(long id, String title, String description, List<String> languages,
                         int timeLimitMs, int memoryLimitMb, List<Sample> samples) {
    }

    private PracticeViews() {
    }
}
