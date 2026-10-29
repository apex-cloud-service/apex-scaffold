package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.dto.monitor.MonitorSystemResponse;
import com.apex.scaffold.interfaces.monitor.dashboard.MonitorMetricsCollector;
import com.apex.scaffold.interfaces.util.MessageUtils;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/** 验证系统资源采集延迟订阅并隔离到通用阻塞池。 */
class MonitorDashboardHandlerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final MonitorMetricsCollector collector = mock(MonitorMetricsCollector.class);
    private final MonitorDashboardHandler handler = new MonitorDashboardHandler(mock(MessageUtils.class), collector);
    private final ServerRequest request = mock(ServerRequest.class);

    @Test
    void systemDefersCollectionAndCollectsAgainForEachSubscription() {
        when(collector.system()).thenReturn(mock(MonitorSystemResponse.class));

        var response = handler.system(request);

        verifyNoInteractions(collector);
        StepVerifier.create(response)
                .assertNext(result -> assertThat(result.statusCode().value()).isEqualTo(200))
                .expectComplete()
                .verify(TIMEOUT);
        StepVerifier.create(response).expectNextCount(1).expectComplete().verify(TIMEOUT);
        verify(collector, times(2)).system();
    }

    @Test
    void systemCollectsOnGeneralPoolWhenInvokedAndSubscribedOnRequestThread() {
        when(collector.system()).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("apex.general-");
            assertThat(Schedulers.isInNonBlockingThread()).isFalse();
            return mock(MonitorSystemResponse.class);
        });

        StepVerifier.create(Mono.defer(() -> handler.system(request)).subscribeOn(Schedulers.parallel()))
                .assertNext(result -> assertThat(result.statusCode().value()).isEqualTo(200))
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void systemPropagatesCollectionFailureFromGeneralPool() {
        IllegalStateException failure = new IllegalStateException("collection failed");
        when(collector.system()).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("apex.general-");
            throw failure;
        });

        var response = handler.system(request);

        verifyNoInteractions(collector);
        StepVerifier.create(response.subscribeOn(Schedulers.parallel()))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify(TIMEOUT);
    }
}
