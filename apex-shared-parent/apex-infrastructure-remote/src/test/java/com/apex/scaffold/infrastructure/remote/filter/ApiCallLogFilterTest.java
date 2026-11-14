package com.apex.scaffold.infrastructure.remote.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogProperties;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogSink;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * API 调用日志过滤器测试。
 *
 * @author weiqiang.ling
 */
class ApiCallLogFilterTest {

    /**
     * 验证 {@code imageResponse_DoesNotConsumeBody} 场景。
     */
    @Test
    @DisplayName("图片响应只记录元数据且不提前聚合响应体")
    void imageResponse_DoesNotConsumeBody() {
        ApiCallLogService logService = mock(ApiCallLogService.class);
        when(logService.log(any())).thenReturn(Mono.empty());
        AtomicBoolean bodySubscribed = new AtomicBoolean();
        byte[] image = new byte[512 * 1024];
        Flux<org.springframework.core.io.buffer.DataBuffer> body = Flux.defer(() -> {
            bodySubscribed.set(true);
            return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(image));
        });
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, "image/png")
                .body(body)
                .build();
        ExchangeFunction next = ignored -> Mono.just(response);
        ClientRequest request = ClientRequest.create(HttpMethod.POST, URI.create("https://apex.beauty/api/image"))
                .attribute(ApiCallLogFilter.ATTRIBUTE_SERVICE_NAME, "face-analysis")
                .build();

        ClientResponse filtered =
                ApiCallLogFilter.create(logService).filter(request, next).block();

        assertThat(filtered).isNotNull();
        assertThat(filtered).isSameAs(response);
        assertThat(bodySubscribed).isFalse();
        verify(logService, timeout(1000)).log(any());
    }

    /**
     * 验证真实服务经 JDBC 和 EVENT 两次调度后，延迟持久化仍能读取请求租户。
     */
    @Test
    @DisplayName("真实日志服务延迟写入 EVENT 线程仍携带请求租户，恢复调用线程上下文")
    void logSubmission_OffloadsAndPreservesTenant() throws InterruptedException {
        CountDownLatch logged = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean nonBlockingThread = new AtomicBoolean();
        AtomicReference<String> loggedTenant = new AtomicReference<>();
        AtomicReference<String> persistenceThread = new AtomicReference<>();
        ApiCallLogSink sink = observation -> Mono.fromCallable(() -> {
                    assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
                    nonBlockingThread.set(Schedulers.isInNonBlockingThread());
                    persistenceThread.set(Thread.currentThread().getName());
                    loggedTenant.set(TenantContext.currentTenantId().orElse(null));
                    logged.countDown();
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.event())
                .then();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("apiCallLogSink", sink);
        ApiCallLogProperties properties = new ApiCallLogProperties();
        properties.setEnabled(true);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        try {
            ApiCallLogService logService =
                    new ApiCallLogService(properties, beanFactory.getBeanProvider(ApiCallLogSink.class), meterRegistry);
            ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("https://apex.beauty/api/image"))
                    .attribute(ApiCallLogFilter.ATTRIBUTE_SERVICE_NAME, "face-analysis")
                    .attribute(ApiCallLogFilter.ATTRIBUTE_TENANT_ID, "request-tenant")
                    .build();
            ClientResponse response = ClientResponse.create(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, "image/png")
                    .build();
            Scheduler worker = Schedulers.newSingle("api-log-test");
            AtomicReference<String> restoredTenant = new AtomicReference<>();
            try {
                worker.schedule(() -> {
                    String previous = TenantContext.setTenantId("worker-tenant");
                    try {
                        ApiCallLogFilter.create(logService)
                                .filter(request, ignored -> Mono.just(response))
                                .subscribe();
                        restoredTenant.set(TenantContext.currentTenantId().orElse(null));
                    } finally {
                        TenantContext.restoreTenantId(previous);
                        finished.countDown();
                    }
                });
                assertThat(logged.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(nonBlockingThread).isFalse();
                assertThat(persistenceThread.get()).startsWith("apex.event-");
                assertThat(loggedTenant).hasValue("request-tenant");
                assertThat(restoredTenant).hasValue("worker-tenant");
            } finally {
                worker.dispose();
            }
        } finally {
            meterRegistry.close();
        }
    }
}
