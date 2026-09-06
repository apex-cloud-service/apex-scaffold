package com.apex.scaffold.interfaces.governance.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.governance.DrainParticipant;
import com.apex.scaffold.common.governance.DrainResult;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 资源排空注册表测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("ResourceDrainRegistry 单元测试")
class ResourceDrainRegistryTest {

    /**
     * 未完成的参与者必须在统一截止时间到达后返回超时结果。
     *
     * @throws Exception 测试等待异常
     */
    @Test
    @DisplayName("参与者未完成时应按统一截止时间结束排空")
    void shouldFinishDrainWhenParticipantDoesNotComplete() throws Exception {
        ResourceDrainRegistry registry = new ResourceDrainRegistry();
        registry.register(new DrainParticipant() {
            @Override
            public String name() {
                return "never-ending";
            }

            @Override
            public void stopAccepting() {}

            @Override
            public CompletableFuture<DrainResult> drain(Instant deadline) {
                return new CompletableFuture<>();
            }

            @Override
            public void forceStop() {}
        });

        List<DrainResult> results = registry.drain(Instant.now().plusMillis(50))
                .toCompletableFuture()
                .get(1, TimeUnit.SECONDS);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.participant()).isEqualTo("never-ending");
            assertThat(result.drained()).isFalse();
        });
    }
}
