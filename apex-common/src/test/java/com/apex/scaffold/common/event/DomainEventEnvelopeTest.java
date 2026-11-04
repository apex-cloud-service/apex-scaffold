package com.apex.scaffold.common.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 领域事件信封序列化契约：跨进程传输必须保留事件 ID、租户、追踪号与发生时间。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("领域事件信封序列化")
class DomainEventEnvelopeTest {

    @Test
    @DisplayName("JSON 应包含信封字段，反序列化后可恢复稳定身份")
    void shouldRoundTripEnvelope() {
        SampleEvent original = new SampleEvent(42L);
        original.attachTenantContext("tenant-a");

        String json = JsonUtil.toJson(original);
        assertThat(json).contains("eventId", "createdAt", "traceId", "eventType", "tenantId");

        SampleEvent restored = JsonUtil.fromJson(json, SampleEvent.class);
        assertThat(restored.getEventId()).isEqualTo(original.getEventId());
        assertThat(restored.getTenantId()).isEqualTo("tenant-a");
        assertThat(restored.getTraceId()).isEqualTo(original.getTraceId());
        assertThat(restored.getEventType()).isEqualTo(original.getEventType());
        assertThat(restored.getCreatedAt())
                .isEqualTo(
                        original.getCreatedAt().withNano(restored.getCreatedAt().getNano()));
        assertThat(restored.getPayload()).isEqualTo(42L);
    }

    @Test
    @DisplayName("显式事件 ID 在跨进程传输后保持不变，支撑消费幂等")
    void shouldKeepExplicitEventId() {
        SampleEvent original = new SampleEvent(7L, "order-7-approved-v1");
        String json = JsonUtil.toJson(original);

        SampleEvent restored = JsonUtil.fromJson(json, SampleEvent.class);
        assertThat(restored.getEventId()).isEqualTo("order-7-approved-v1");
    }

    /** 最小业务事件：模拟契约事件仅有业务负载构造器。 */
    static class SampleEvent extends AbstractDomainEvent {

        private final Long payload;

        @JsonCreator
        SampleEvent(@JsonProperty("payload") Long payload) {
            super("SAMPLE", null);
            this.payload = payload;
        }

        SampleEvent(Long payload, String eventId) {
            super("SAMPLE", null, eventId);
            this.payload = payload;
        }

        public Long getPayload() {
            return payload;
        }

        @Override
        public String getSourceId() {
            return String.valueOf(payload);
        }
    }
}
