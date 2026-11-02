package com.apex.scaffold.contract.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 主数据变更事件契约测试（方案 D5）.
 *
 * <p>校验 {@code apex-domain-event} 主题下用户/租户主数据变更事件的业务负载构造、
 * 事件源解析与继承自 {@link AbstractDomainEvent} 的事件信封能力，
 * 保障发布方（system）与订阅方之间的负载契约不漂移。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@DisplayName("主数据变更事件契约测试")
class MasterDataEventContractTest {

    @Nested
    @DisplayName("UserChangedEvent 用户主数据变更事件")
    class UserChangedEventTest {

        @Test
        @DisplayName("构造后业务负载可通过 getter 读取")
        void exposesBusinessPayload() {
            UserChangedEvent created = new UserChangedEvent(1001L, UserChangedEvent.ACTION_CREATED);
            assertThat(created.getUserId()).isEqualTo(1001L);
            assertThat(created.getAction()).isEqualTo(UserChangedEvent.ACTION_CREATED);

            UserChangedEvent updated = new UserChangedEvent(1001L, UserChangedEvent.ACTION_UPDATED);
            assertThat(updated.getAction()).isEqualTo(UserChangedEvent.ACTION_UPDATED);
        }

        @Test
        @DisplayName("getSourceId 返回用户 ID 字符串，用户 ID 为空时返回 null")
        void resolvesSourceId() {
            assertThat(new UserChangedEvent(1001L, UserChangedEvent.ACTION_UPDATED).getSourceId())
                    .isEqualTo("1001");
            assertThat(new UserChangedEvent(null, UserChangedEvent.ACTION_CREATED).getSourceId())
                    .isNull();
        }

        @Test
        @DisplayName("继承事件信封：事件名/事件 ID/时间戳/租户上下文/已处理标记")
        void inheritsEventEnvelope() {
            UserChangedEvent event = new UserChangedEvent(1001L, UserChangedEvent.ACTION_CREATED);
            assertThat(event).isInstanceOf(AbstractDomainEvent.class);
            assertThat(event.getEventName()).isEqualTo("UserChangedEvent");
            assertThat(event.getEventId()).isNotBlank();
            assertThat(event.getCreatedAt()).isNotNull();
            event.attachTenantContext("tenant-1");
            assertThat(event.getTenantId()).isEqualTo("tenant-1");
            event.markAsProcessed();
            assertThat(event.isProcessed()).isTrue();
        }

        @Test
        @DisplayName("事件 ID 逐实例唯一，支持消费端幂等去重")
        void generatesUniqueEventIdPerInstance() {
            UserChangedEvent first = new UserChangedEvent(1001L, UserChangedEvent.ACTION_UPDATED);
            UserChangedEvent second = new UserChangedEvent(1001L, UserChangedEvent.ACTION_UPDATED);
            assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
        }
    }

    @Nested
    @DisplayName("TenantChangedEvent 租户主数据变更事件")
    class TenantChangedEventTest {

        @Test
        @DisplayName("构造后业务负载可通过 getter 读取")
        void exposesBusinessPayload() {
            TenantChangedEvent event = new TenantChangedEvent("T001", TenantChangedEvent.ACTION_CREATED);
            assertThat(event.getTenantCode()).isEqualTo("T001");
            assertThat(event.getAction()).isEqualTo(TenantChangedEvent.ACTION_CREATED);
        }

        @Test
        @DisplayName("getSourceId 返回租户编码，编码为空时返回 null")
        void resolvesSourceId() {
            assertThat(new TenantChangedEvent("T001", TenantChangedEvent.ACTION_UPDATED).getSourceId())
                    .isEqualTo("T001");
            assertThat(new TenantChangedEvent(null, TenantChangedEvent.ACTION_UPDATED).getSourceId())
                    .isNull();
        }

        @Test
        @DisplayName("继承事件信封：事件名/事件 ID/时间戳/租户上下文/已处理标记")
        void inheritsEventEnvelope() {
            TenantChangedEvent event = new TenantChangedEvent("T001", TenantChangedEvent.ACTION_UPDATED);
            assertThat(event).isInstanceOf(AbstractDomainEvent.class);
            assertThat(event.getEventName()).isEqualTo("TenantChangedEvent");
            assertThat(event.getEventId()).isNotBlank();
            assertThat(event.getCreatedAt()).isNotNull();
            // 信封 tenantId 为发布者上下文租户，与负载中的被变更租户编码相互独立
            event.attachTenantContext("tenant-1");
            assertThat(event.getTenantId()).isEqualTo("tenant-1");
            event.markAsProcessed();
            assertThat(event.isProcessed()).isTrue();
        }
    }
}
