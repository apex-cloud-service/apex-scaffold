package com.apex.scaffold.job;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class WorkflowJobBridgeTest {
    @Test
    void bridgeRequiresTypedBusinessPort() {
        org.assertj.core.api.Assertions.assertThat(
                        WorkflowJobBridge.class.getConstructors()[0].getParameterTypes()[0])
                .isNotEqualTo(Object.class);
    }

    @Test
    void scanFailureReachesExecutor() {
        assertThatThrownBy(() -> new WorkflowJobBridge(new Target()).execute()).hasMessageContaining("scan failed");
    }

    public static class Target implements WorkflowJobPort {
        public Mono<Void> scanTimeoutTasks() {
            return Mono.error(new IllegalStateException("scan failed"));
        }
    }
}
