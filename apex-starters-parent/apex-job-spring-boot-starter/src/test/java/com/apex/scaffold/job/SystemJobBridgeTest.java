package com.apex.scaffold.job;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class SystemJobBridgeTest {
    @Test
    void restoresThreadTenantAndPropagatesReactiveTenantAfterLateFailure() {
        com.apex.scaffold.common.context.TenantContext.setTenantId("previous");
        try {
            SystemJobPort port = id -> {
                org.assertj.core.api.Assertions.assertThat(
                                com.apex.scaffold.common.context.TenantContext.currentTenantId())
                        .contains("fixed");
                return reactor.core.publisher.Mono.deferContextual(context -> {
                    org.assertj.core.api.Assertions.assertThat(context.<String>get(
                                    com.apex.scaffold.common.context.TenantContext.REACTOR_CONTEXT_KEY))
                            .isEqualTo("fixed");
                    return reactor.core.publisher.Mono.<Void>error(new IllegalStateException("late failure"));
                });
            };
            assertThatThrownBy(() -> new SystemJobBridge(port, "fixed", 1L).execute())
                    .hasMessageContaining("late failure");
            org.assertj.core.api.Assertions.assertThat(com.apex.scaffold.common.context.TenantContext.currentTenantId())
                    .contains("previous");
        } finally {
            com.apex.scaffold.common.context.TenantContext.clear();
        }
    }

    @Test
    void bridgeRequiresTypedBusinessPort() {
        org.assertj.core.api.Assertions.assertThat(
                        SystemJobBridge.class.getConstructors()[0].getParameterTypes()[0])
                .isNotEqualTo(Object.class);
    }

    @Test
    void fixedServiceFailurePropagates() {
        assertThatThrownBy(() -> new SystemJobBridge(new Target(), "tenant", 1L).execute())
                .hasMessageContaining("late failure");
    }

    @Test
    void rejectsMissingOrNonPositiveJobIds() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThatThrownBy(() -> new SystemJobBridge(new Target(), "tenant", id))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new SystemJobBridge(new Target(), " ", 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    public static class Target implements SystemJobPort {
        public Flux<Void> runOnce(Long id) {
            return Flux.error(new IllegalStateException("late failure"));
        }
    }
}
