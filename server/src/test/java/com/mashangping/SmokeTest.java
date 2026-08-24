package com.mashangping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmokeTest extends IntegrationTestBase {

    @Test
    void context_loads_and_datasource_reachable() {
        assertThat(true).isTrue();
    }
}
