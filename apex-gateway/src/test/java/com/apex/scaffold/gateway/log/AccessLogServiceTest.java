package com.apex.scaffold.gateway.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link AccessLogService} 访问日志落库服务单元测试.
 *
 * <p>覆盖 Task 4 的三条核心不变量：
 * <ol>
 *   <li><b>脱敏红线</b>：敏感请求头（Authorization/Cookie 等）落库前必须以 {@code ***} 占位，
 *       严禁真实凭据入库；</li>
 *   <li><b>异步吞异常</b>：落库发生在网关事件循环之外，且仓储异常一律被吞掉，
 *       绝不允许因访问日志写库失败而影响正常响应；</li>
 *   <li><b>开关与采样</b>：总开关关闭或未命中采样时，不产生任何仓储调用。</li>
 * </ol>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("AccessLogService 访问日志落库服务测试")
class AccessLogServiceTest {

    private final AccessLogRepository repository = mock(AccessLogRepository.class);

    /**
     * 构造被测服务（指定开关与采样率）。
     *
     * @param enabled    落库总开关
     * @param sampleRate 采样率（0.0–1.0）
     * @return 访问日志服务实例
     */
    private AccessLogService newService(boolean enabled, double sampleRate) {
        AccessLogProperties properties = new AccessLogProperties();
        properties.setEnabled(enabled);
        properties.setSampleRate(sampleRate);
        return new AccessLogService(repository, properties);
    }

    /**
     * 构造一条用于落库校验的访问日志记录。
     *
     * @return 访问日志记录样例
     */
    private AccessLogRecord sampleRecord() {
        return new AccessLogRecord(
                null,
                "trace-1",
                "GET",
                "/api/v1/orders",
                "order-domain",
                "apex-order-service",
                "stable",
                200,
                12L,
                "127.0.0.1",
                "junit",
                "{}",
                "1001",
                "1",
                LocalDateTime.now());
    }

    @Nested
    @DisplayName("脱敏")
    class MaskingTests {

        @Test
        @DisplayName("敏感请求头必须脱敏为 ***，不得落真实值")
        void masksSensitiveHeadersBeforePersist() {
            AccessLogService service = newService(true, 1.0d);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer super-secret-token");
            headers.set(HttpHeaders.COOKIE, "session=abc");
            headers.setContentType(MediaType.APPLICATION_JSON);

            String json = service.sanitizeHeaders(headers);

            assertThat(json)
                    .doesNotContain("super-secret-token")
                    .doesNotContain("session=abc")
                    .contains("\"Authorization\":\"***\"")
                    .contains("application/json");
        }

        @Test
        @DisplayName("未在白名单内的普通头不落库，避免信息面外溢")
        void ignoresHeadersOutsideWhitelist() {
            AccessLogService service = newService(true, 1.0d);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Debug", "do-not-log");

            assertThat(service.sanitizeHeaders(headers)).doesNotContain("do-not-log");
        }
    }

    @Nested
    @DisplayName("异步落库")
    class PersistTests {

        @Test
        @DisplayName("仓储异常必须被吞掉，不向调用方传播")
        void persistsAsyncAndSwallowsRepositoryError() {
            AccessLogService service = newService(true, 1.0d);
            when(repository.save(any())).thenReturn(Mono.error(new IllegalStateException("db down")));

            StepVerifier.create(service.save(sampleRecord())).verifyComplete();

            verify(repository).save(any());
            assertThat(service.persistFailureCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("正常落库时透传记录且不计数失败")
        void persistsRecordSuccessfully() {
            AccessLogService service = newService(true, 1.0d);
            when(repository.save(any())).thenReturn(Mono.empty());

            StepVerifier.create(service.save(sampleRecord())).verifyComplete();

            verify(repository).save(any());
            assertThat(service.persistFailureCount()).isZero();
        }
    }

    @Nested
    @DisplayName("开关与采样")
    class GateTests {

        @Test
        @DisplayName("总开关关闭时不触发落库")
        void doesNotPersistWhenDisabled() {
            AccessLogService service = newService(false, 1.0d);

            StepVerifier.create(service.save(sampleRecord())).verifyComplete();

            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("采样率为 0 时不触发落库")
        void doesNotPersistWhenNotSampled() {
            AccessLogService service = newService(true, 0.0d);

            StepVerifier.create(service.save(sampleRecord())).verifyComplete();

            verify(repository, never()).save(any());
        }
    }
}
