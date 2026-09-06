package com.mashangping.plagiarism;

import java.util.List;

/** 查重出参集合（仅教师端点消费）。 */
public final class PlagiarismViews {

    /** 参与比对的提交与作者信息（含最高分提交 id，供对比页取码）。 */
    public record StudentInfo(long studentId, String studentNo, String realName,
                              String language, Integer score, boolean ac, long submissionId) {
    }

    /** 疑似对：a 与 b 为两名学生，similarity∈[0,1]，flag=HIGH(≥高疑似阈值)/MID。 */
    public record PairItem(StudentInfo a, StudentInfo b, double similarity, String flag) {
    }

    /** 报告：该作业该题同班两两比对，按相似度降序。 */
    public record Report(long assignmentId, long problemId, String problemTitle,
                         double highThreshold, int participants, List<PairItem> items) {
        public Report {
            items = items == null ? java.util.List.of() : items;
        }
    }

    /** 对比视图：两名学生最高分提交的元信息与源码（供前端左右分栏高亮）。 */
    public record CompareView(StudentInfo a, StudentInfo b, String codeA, String codeB) {
    }

    private PlagiarismViews() {
    }
}
