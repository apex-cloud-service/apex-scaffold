package com.apex.scaffold.domain.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobTenantTest {

    @Test
    void shouldKeepTenantOnJobAndLogSnapshot() {
        JobEntity job = JobEntity.create("cleanup", "SYSTEM", "cleanupBean.run()", "0 0 2 * * ?", "tenant-a");

        JobLogEntity log = JobLogEntity.start(job);

        assertThat(job.getTenantId()).isEqualTo("tenant-a");
        assertThat(log.getTenantId()).isEqualTo("tenant-a");
    }
}
