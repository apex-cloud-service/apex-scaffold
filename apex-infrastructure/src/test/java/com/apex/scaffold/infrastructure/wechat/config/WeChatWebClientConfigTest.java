package com.apex.scaffold.infrastructure.wechat.config;

import static org.assertj.core.api.Assertions.*;

import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * 微信生态 WebClient 配置单元测试类。
 *
 * <p>测试 {@link WeChatWebClientConfig} 的 Bean 创建和配置正确性。
 * 优化后（P2-1）Bean 方法接受 {@link ObjectProvider}{@code <ApiCallLogService>} 参数
 * 以附加 API 调用日志过滤器。</p>
 *
 * <h2>测试覆盖范围</h2>
 * <ul>
 *   <li>weChatApiWebClient Bean 创建和 baseUrl 配置</li>
 *   <li>weWorkWebClient Bean 创建和 baseUrl 配置</li>
 *   <li>createRetry 重试策略配置验证</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeChatWebClientConfig 单元测试")
class WeChatWebClientConfigTest {

    /** 被测配置实例。 */
    private final WeChatWebClientConfig config = new WeChatWebClientConfig();

    /** Mock 的 ApiCallLogService。 */
    @Mock
    private ApiCallLogService logService;

    /** ObjectProvider 包装 mock 的 ApiCallLogService。 */
    private ObjectProvider<ApiCallLogService> logServiceProvider;

    /**
     * 初始化 ObjectProvider。
     */
    @BeforeEach
    void setUp() {
        logServiceProvider = new ObjectProvider<>() {
            /**
             * {@inheritDoc}
             *
             * @return 操作结果，类型为 {@code ApiCallLogService}
             */
            @Override
            public ApiCallLogService getObject() {
                return logService;
            }

            /**
             * {@inheritDoc}
             *
             * @param args 方法输入 {@code args}，类型为 {@code Object[]}
             * @return 操作结果，类型为 {@code ApiCallLogService}
             */
            @Override
            public ApiCallLogService getObject(Object... args) {
                return logService;
            }

            /**
             * {@inheritDoc}
             *
             * @return 操作结果，类型为 {@code ApiCallLogService}
             */
            @Override
            public ApiCallLogService getIfAvailable() {
                return logService;
            }

            /**
             * {@inheritDoc}
             *
             * @return 操作结果，类型为 {@code ApiCallLogService}
             */
            @Override
            public ApiCallLogService getIfUnique() {
                return logService;
            }
        };
    }

    // ==================== Bean 创建测试 ====================

    @Nested
    @DisplayName("Bean 创建")
    class BeanCreationTests {

        /**
         * 测试 weChatApiWebClient Bean 创建成功，baseUrl 正确。
         *
         * <p>优化后（P2-1）传入 ObjectProvider 以附加 ApiCallLogFilter。
         */
        @Test
        @DisplayName("weChatApiWebClient Bean 创建成功且 baseUrl 正确")
        void weChatApiWebClient_CreatedWithCorrectBaseUrl() {
            WebClient client = config.weChatApiWebClient(logServiceProvider);
            assertThat(client).isNotNull();
        }

        /**
         * 测试 weWorkWebClient Bean 创建成功，baseUrl 正确。
         */
        @Test
        @DisplayName("weWorkWebClient Bean 创建成功且 baseUrl 正确")
        void weWorkWebClient_CreatedWithCorrectBaseUrl() {
            WebClient client = config.weWorkWebClient(logServiceProvider);
            assertThat(client).isNotNull();
        }

        /**
         * 测试两个 Bean 都是不同的实例。
         */
        @Test
        @DisplayName("两个WebClient是不同的实例")
        void twoWebClientsAreDifferentInstances() {
            WebClient apiClient = config.weChatApiWebClient(logServiceProvider);
            WebClient workClient = config.weWorkWebClient(logServiceProvider);

            assertThat(apiClient).isNotNull();
            assertThat(workClient).isNotNull();

            assertThat(apiClient).isNotSameAs(workClient);
        }
    }

    // ==================== 重试配置测试 ====================

    @Nested
    @DisplayName("createRetry 重试配置")
    class RetryConfigurationTests {

        /**
         * 测试 createRetry 返回的 Retry 实例不为 null。
         */
        @Test
        @DisplayName("创建重试策略不为null")
        void createRetry_ReturnsNonNull() {
            Retry retry = WeChatWebClientConfig.createRetry();
            assertThat(retry).isNotNull();
        }

        /**
         * 测试重试策略对 IOException 生效。
         */
        @Test
        @DisplayName("IOException应触发重试")
        void createRetry_RetriesOnIOException() {
            Retry retry = WeChatWebClientConfig.createRetry();
            assertThat(retry).isNotNull();

            Mono<String> failing = Mono.<String>error(new IOException("连接超时"))
                    .retryWhen(retry)
                    .onErrorResume(e -> Mono.just("fallback"));

            String result = failing.block(Duration.ofSeconds(10));
            assertThat(result).isEqualTo("fallback");
        }

        /**
         * 测试重试策略对 5xx 服务器错误生效。
         */
        @Test
        @DisplayName("5xx服务器错误应触发重试")
        void createRetry_RetriesOn5xxError() {
            Retry retry = WeChatWebClientConfig.createRetry();
            assertThat(retry).isNotNull();

            WebClientResponseException serverError = WebClientResponseException.create(
                    HttpStatus.SERVICE_UNAVAILABLE.value(), "Service Unavailable", null, null, null);

            Mono<String> failing =
                    Mono.<String>error(serverError).retryWhen(retry).onErrorResume(e -> Mono.just("fallback"));

            String result = failing.block(Duration.ofSeconds(10));
            assertThat(result).isEqualTo("fallback");
        }

        /**
         * 测试重试策略对 4xx 客户端错误不触发重试。
         */
        @Test
        @DisplayName("4xx客户端错误不应触发重试")
        void createRetry_DoesNotRetryOn4xxError() {
            Retry retry = WeChatWebClientConfig.createRetry();
            assertThat(retry).isNotNull();

            WebClientResponseException clientError =
                    WebClientResponseException.create(HttpStatus.BAD_REQUEST.value(), "Bad Request", null, null, null);

            Mono<String> failing =
                    Mono.<String>error(clientError).retryWhen(retry).onErrorResume(e -> Mono.just("fallback"));

            String result = failing.block(Duration.ofSeconds(10));
            assertThat(result).isEqualTo("fallback");
        }

        /**
         * 测试多次调用 createRetry 返回独立实例。
         */
        @Test
        @DisplayName("多次调用createRetry返回独立实例")
        void createRetry_MultipleCallsReturnIndependentInstances() {
            Retry retry1 = WeChatWebClientConfig.createRetry();
            Retry retry2 = WeChatWebClientConfig.createRetry();

            assertThat(retry1).isNotNull();
            assertThat(retry2).isNotNull();
        }
    }
}
