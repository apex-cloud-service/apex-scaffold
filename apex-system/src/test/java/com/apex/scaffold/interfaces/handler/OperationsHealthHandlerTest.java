package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.HealthExportService;
import com.apex.scaffold.application.service.OperationsHealthService;
import com.apex.scaffold.interfaces.dto.monitor.HealthReportResponse;
import com.apex.scaffold.interfaces.monitor.health.HealthReportAssembler;
import com.apex.scaffold.interfaces.util.MessageUtils;
import jakarta.validation.Validator;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

/** 验证体检报告延迟订阅并隔离到通用阻塞池。 */
class OperationsHealthHandlerTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HealthReportAssembler assembler = mock(HealthReportAssembler.class);
    private final OperationsHealthService operationsHealthService = mock(OperationsHealthService.class);
    private final HealthExportService healthExportService = mock(HealthExportService.class);
    private final Validator validator = mock(Validator.class);
    private final OperationsHealthHandler handler = new OperationsHealthHandler(
            mock(MessageUtils.class), validator, assembler, operationsHealthService, healthExportService);
    private final ServerRequest request = mock(ServerRequest.class);

    @Test
    void healthReportDefersAssemblyAndAssemblesAgainForEachSubscription() {
        when(assembler.report()).thenReturn(mock(HealthReportResponse.class));

        var response = handler.healthReport(request);

        verifyNoInteractions(assembler);
        StepVerifier.create(response)
                .assertNext(result -> assertThat(result.statusCode().value()).isEqualTo(200))
                .expectComplete()
                .verify(TIMEOUT);
        StepVerifier.create(response).expectNextCount(1).expectComplete().verify(TIMEOUT);
        verify(assembler, times(2)).report();
    }

    @Test
    void healthReportAssemblesOnGeneralPoolWhenSubscribedOnRequestThread() {
        when(assembler.report()).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("apex.general-");
            assertThat(Schedulers.isInNonBlockingThread()).isFalse();
            return mock(HealthReportResponse.class);
        });

        StepVerifier.create(Mono.defer(() -> handler.healthReport(request)).subscribeOn(Schedulers.parallel()))
                .assertNext(result -> assertThat(result.statusCode().value()).isEqualTo(200))
                .expectComplete()
                .verify(TIMEOUT);
    }

    @Test
    void healthReportPropagatesAssemblyFailureFromGeneralPool() {
        IllegalStateException failure = new IllegalStateException("assembly failed");
        when(assembler.report()).thenAnswer(invocation -> {
            assertThat(Thread.currentThread().getName()).startsWith("apex.general-");
            throw failure;
        });

        var response = handler.healthReport(request);

        verifyNoInteractions(assembler);
        StepVerifier.create(response.subscribeOn(Schedulers.parallel()))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify(TIMEOUT);
    }
}
