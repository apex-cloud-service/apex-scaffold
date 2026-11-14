package com.apex.scaffold.domain.model.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 验证体检快照 HMAC 签名与三态完整性结论。 */
class HealthSnapshotSignerTest {

    private static final String SECRET = "unit-test-secret";

    @Test
    void signIsStableForSamePayload() {
        HealthSnapshotEntity entity = snapshot();

        String first = HealthSnapshotSigner.sign(entity, SECRET);
        String second = HealthSnapshotSigner.sign(entity, SECRET);

        assertThat(first).isNotNull().hasSize(64).isEqualTo(second);
    }

    @Test
    void signReturnsNullWhenSecretMissing() {
        assertThat(HealthSnapshotSigner.sign(snapshot(), " ")).isNull();
        assertThat(HealthSnapshotSigner.sign(snapshot(), null)).isNull();
    }

    @Test
    void verifyReportsValidForUntouchedSnapshot() {
        HealthSnapshotEntity entity = snapshot();
        entity.setSignature(HealthSnapshotSigner.sign(entity, SECRET));

        assertThat(HealthSnapshotSigner.verify(entity, SECRET)).isEqualTo(HealthSnapshotSigner.INTEGRITY_VALID);
    }

    @Test
    void verifyReportsInvalidAfterConclusionTampered() {
        HealthSnapshotEntity entity = snapshot();
        entity.setSignature(HealthSnapshotSigner.sign(entity, SECRET));
        entity.setOverallStatus("UP");
        entity.setDownCount(0);

        assertThat(HealthSnapshotSigner.verify(entity, SECRET)).isEqualTo(HealthSnapshotSigner.INTEGRITY_INVALID);
    }

    @Test
    void verifyReportsUnverifiableWithoutSignatureOrSecret() {
        HealthSnapshotEntity legacy = snapshot();

        assertThat(HealthSnapshotSigner.verify(legacy, SECRET)).isEqualTo(HealthSnapshotSigner.INTEGRITY_UNVERIFIABLE);

        HealthSnapshotEntity signed = snapshot();
        signed.setSignature(HealthSnapshotSigner.sign(signed, SECRET));
        assertThat(HealthSnapshotSigner.verify(signed, null)).isEqualTo(HealthSnapshotSigner.INTEGRITY_UNVERIFIABLE);
    }

    @Test
    void tenantAndTimestampsDoNotParticipateInSignature() {
        HealthSnapshotEntity entity = snapshot();
        String signature = HealthSnapshotSigner.sign(entity, SECRET);

        entity.setTenantId("another-tenant");
        entity.setCreateTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        entity.setSignature(signature);

        assertThat(HealthSnapshotSigner.verify(entity, SECRET)).isEqualTo(HealthSnapshotSigner.INTEGRITY_VALID);
    }

    private static HealthSnapshotEntity snapshot() {
        HealthSnapshotEntity entity = new HealthSnapshotEntity();
        entity.setCaptureSource(HealthSnapshotEntity.CAPTURE_SOURCE_MANUAL);
        entity.setServiceName("apex-system-service");
        entity.setInstanceId("1234@host");
        entity.setScope(HealthSnapshotEntity.SCOPE_INSTANCE);
        entity.setRuleVersion("health-report-v1");
        entity.setOverallStatus("DEGRADED");
        entity.setSummary("redis latency high");
        entity.setUpCount(8);
        entity.setDegradedCount(1);
        entity.setDownCount(1);
        entity.setUnknownCount(2);
        entity.setNotConfiguredCount(2);
        entity.setGeneratedAt(LocalDateTime.of(2026, 10, 1, 8, 0));
        entity.setTenantId("t-1");
        return entity;
    }
}
