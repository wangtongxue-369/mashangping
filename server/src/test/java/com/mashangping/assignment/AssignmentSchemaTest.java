package com.mashangping.assignment;

import com.mashangping.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentSchemaTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbc;

    private Long tableCount(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = ?",
                Long.class, table);
    }

    private Long fkCount(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE constraint_schema = DATABASE() AND table_name = ? "
                        + "AND constraint_type = 'FOREIGN KEY'",
                Long.class, table);
    }

    @Test
    void assignment_tables_exist() {
        assertThat(tableCount("assignment")).isEqualTo(1L);
        assertThat(tableCount("assignment_problem")).isEqualTo(1L);
    }

    @Test
    void assignment_problem_has_both_foreign_keys() {
        assertThat(fkCount("assignment_problem")).isEqualTo(2L);
    }

    @Test
    void course_problem_has_foreign_keys() {
        assertThat(fkCount("course_problem")).isEqualTo(2L);
    }

    @Test
    void assignment_has_default_fields() {
        Long lateDefault = jdbc.queryForObject(
                "SELECT COLUMN_DEFAULT FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'assignment' "
                        + "AND column_name = 'late_days'",
                Long.class);
        assertThat(lateDefault).isEqualTo(0L);
    }
}
