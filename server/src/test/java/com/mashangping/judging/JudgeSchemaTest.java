package com.mashangping.judging;

import com.mashangping.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class JudgeSchemaTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;

    private Long tableCount(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Long.class, table);
    }

    @Test
    void three_judging_tables_exist() {
        assertThat(tableCount("submission")).isEqualTo(1L);
        assertThat(tableCount("judge_task")).isEqualTo(1L);
        assertThat(tableCount("judge_detail")).isEqualTo(1L);
    }

    @Test
    void submission_nullable_anchor_columns() {
        // 双锚列必须可空（练习路径为 NULL）
        Long nullable = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'submission' "
                        + "AND column_name IN ('assignment_id','assignment_problem_id','score') "
                        + "AND IS_NULLABLE = 'YES'",
                Long.class);
        assertThat(nullable).isEqualTo(3L);
    }

    @Test
    void judge_task_submission_unique_index() {
        Long uk = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'judge_task' "
                        + "AND index_name = 'uk_judge_task_submission'",
                Long.class);
        assertThat(uk).isGreaterThan(0L);
    }
}
