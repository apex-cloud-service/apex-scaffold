package com.apex.scaffold.infrastructure.remote.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
        verify(logService).log(any());
    }
}
