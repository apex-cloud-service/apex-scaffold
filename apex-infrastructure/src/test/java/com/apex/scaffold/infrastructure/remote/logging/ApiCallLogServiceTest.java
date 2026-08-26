package com.apex.scaffold.infrastructure.remote.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import com.apex.scaffold.domain.repository.ApiCallLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * API 调用日志服务单元测试。
 *
 * <p>测试 {@link ApiCallLogService} 的核心功能：</p>
 * <ul>
 *   <li>敏感请求头脱敏（Authorization、ApiKey、Secret 等）</li>
 *   <li>请求体 / 响应体截断（按 {@link ApiCallLogProperties#getMaxBodyLength()} 限制）</li>
 *   <li>日志记录启用 / 禁用开关</li>
 *   <li>异步写入委托至 {@link ApiCallLogRepository}</li>
 * </ul>
 *
 * <h2>测试隔离</h2>
 * <p>使用 Mockito 模拟 {@link ApiCallLogRepository}，确保不触发真实数据库操作。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiCallLogService
 * @see ApiCallLogProperties
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("API调用日志服务测试")
class ApiCallLogServiceTest {

    /** Mock 的日志仓储。 */
    @Mock
    private ApiCallLogRepository repository;

    /** 使用默认配置的日志属性。 */
    private ApiCallLogProperties properties;

    /** 指标注册表。 */
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    /** 被测服务实例。 */
    private ApiCallLogService service;

    /**
     * 每个测试前初始化配置和被测服务。
     */
    @BeforeEach
    void setUp() {
        properties = new ApiCallLogProperties();
        properties.setEnabled(true);
        properties.setMaxBodyLength(100);
        properties.setLogRequestBody(true);
        properties.setLogResponseBody(true);
        service = new ApiCallLogService(properties, repository, meterRegistry);
    }

    // ==================== 脱敏测试 ====================

    /**
     * 测试请求头和响应头中敏感字段的脱敏逻辑。
     */
    @Nested
    @DisplayName("敏感头脱敏测试")
    class SanitizationTests {

        /**
         * 验证 Authorization / ApiKey / Secret 等字段的值被替换为 ***。
         *
         */
        @Test
        @DisplayName("应脱敏 Authorization / ApiKey / Secret / Token 字段")
        void shouldMaskSensitiveHeaders() {
            ApiCallLogEntity entity = buildBaseEntity();
            entity.setRequestHeaders("{\"authorization\":\"Bearer sk-12345\",\"content-type\":\"application/json\","
                    + "\"apikey\":\"secret-key-789\",\"x-request-id\":\"req-001\"}");
            entity.setResponseHeaders("{\"content-type\":\"application/json\",\"secret\":\"my-secret-value\"}");

            when(repository.save(any())).thenReturn(Mono.empty());

            StepVerifier.create(service.log(entity)).verifyComplete();

            verify(repository, times(1)).save(any());
            String reqHeaders = entity.getRequestHeaders();
            assertNotNull(reqHeaders, "请求头不应为 null");
            // 验证敏感值不存在于脱敏后的头信息中
            assertThat(reqHeaders).as("敏感token sk-12345 应被脱敏").doesNotContain("sk-12345");
            assertThat(reqHeaders).as("敏感key secret-key-789 应被脱敏").doesNotContain("secret-key-789");
            // 非敏感字段应保持原样
            assertThat(reqHeaders).as("content-type 不应被脱敏").contains("\"content-type\":\"application/json\"");
            // 响应头敏感字段验证
            assertThat(entity.getResponseHeaders()).as("响应头 Secret 应被脱敏").doesNotContain("my-secret-value");
        }

        /**
         * 验证 null 输入不触发 NPE。
         *
         */
        @Test
        @DisplayName("null 请求头应返回 null")
        void shouldHandleNullHeaders() {
            ApiCallLogEntity entity = buildBaseEntity();
            entity.setRequestHeaders(null);
            entity.setResponseHeaders(null);

            when(repository.save(any())).thenReturn(Mono.empty());

            StepVerifier.create(service.log(entity)).verifyComplete();

            assertNull(entity.getRequestHeaders(), "null 请求头应保持 null");
            assertNull(entity.getResponseHeaders(), "null 响应头应保持 null");
        }
    }

    // ==================== 截断测试 ====================

    /**
     * 测试请求体 / 响应体按配置的最大长度截断。
     */
    @Nested
    @DisplayName("内容截断测试")
    class TruncationTests {

        /**
         * 验证超过 maxBodyLength 的内容被截断并追加 "[truncated]"。
         *
         */
        @Test
        @DisplayName("超长请求体应被截断")
        void shouldTruncateLongRequestBody() {
            ApiCallLogEntity entity = buildBaseEntity();
            // 构造150字符的请求体，超过 maxBodyLength=100
            entity.setRequestBody("A".repeat(150));
            entity.setResponseBody("B".repeat(50)); // 50字符，未超过限制

            when(repository.save(any())).thenReturn(Mono.empty());

            StepVerifier.create(service.log(entity)).verifyComplete();

            String reqBody = entity.getRequestBody();
            assertNotNull(reqBody, "截断后请求体不应为 null");
            assertEquals(true, reqBody.endsWith("...[truncated]"), "超长请求体应以 [truncated] 结尾");
            assertEquals(true, reqBody.length() <= 100 + "...[truncated]".length(), "截断后长度不应超过 maxBodyLength + 后缀长度");

            // 短响应体不应被截断
            assertEquals(50, entity.getResponseBody().length(), "未超长响应体不应被截断");
        }

        /**
         * 验证关闭响应体日志时不记录响应体。
         *
         */
        @Test
        @DisplayName("关闭响应体日志时应清空 responseBody")
        void shouldClearResponseBodyWhenDisabled() {
            properties.setLogResponseBody(false);
            service = new ApiCallLogService(properties, repository, meterRegistry);

            ApiCallLogEntity entity = buildBaseEntity();
            entity.setResponseBody("some response body");

            when(repository.save(any())).thenReturn(Mono.empty());

            StepVerifier.create(service.log(entity)).verifyComplete();

            assertNull(entity.getResponseBody(), "关闭响应体日志时 responseBody 应为 null");
        }
    }

    // ==================== 开关测试 ====================

    /**
     * 测试日志采集的启用 / 禁用开关。
     */
    @Nested
    @DisplayName("启用/禁用开关测试")
    class ToggleTests {

        /**
         * 验证禁用时不调用仓储保存。
         *
         */
        @Test
        @DisplayName("disabled=true 时不调用 repository.save()")
        void shouldSkipLogWhenDisabled() {
            properties.setEnabled(false);
            service = new ApiCallLogService(properties, repository, meterRegistry);

            ApiCallLogEntity entity = buildBaseEntity();

            StepVerifier.create(service.log(entity)).verifyComplete();

            verify(repository, times(0)).save(any());
        }

        /**
         * 验证 null 实体不触发任何操作。
         *
         */
        @Test
        @DisplayName("null 实体不应触发保存操作")
        void shouldHandleNullEntity() {
            StepVerifier.create(service.log(null)).verifyComplete();
            verify(repository, times(0)).save(any());
        }
    }

    // ==================== 指标测试 ====================

    /**
     * 测试第三方调用 Micrometer 指标记录。
     */
    @Nested
    @DisplayName("Micrometer 指标测试")
    class MetricsTests {

        /**
         * 验证成功调用记录耗时 Timer 与 success 结果计数。
         *
         */
        @Test
        @DisplayName("status=0 时记录耗时 Timer 与 success 计数")
        void shouldRecordSuccessMetrics() {
            when(repository.save(any())).thenReturn(Mono.empty());
            ApiCallLogEntity entity = buildBaseEntity();

            StepVerifier.create(service.log(entity)).verifyComplete();

            assertEquals(
                    150.0,
                    meterRegistry.get("apex.api.call").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS),
                    "Timer 应记录调用耗时");
            assertEquals(
                    1.0,
                    meterRegistry
                            .get("apex.api.call.result")
                            .tag("service", "wechat")
                            .tag("result", "success")
                            .counter()
                            .count(),
                    "成功调用应计数 1");
        }

        /**
         * 验证 5xx 调用按 server_error 结果计数。
         *
         */
        @Test
        @DisplayName("status=2 时按 server_error 计数")
        void shouldRecordServerErrorResult() {
            when(repository.save(any())).thenReturn(Mono.empty());
            ApiCallLogEntity entity = buildBaseEntity();
            entity.setStatus(2);

            StepVerifier.create(service.log(entity)).verifyComplete();

            assertEquals(
                    1.0,
                    meterRegistry
                            .get("apex.api.call.result")
                            .tag("service", "wechat")
                            .tag("result", "server_error")
                            .counter()
                            .count(),
                    "5xx 调用应按 server_error 计数");
        }

        /**
         * 验证 status 为 null 时按 unknown 计数。
         *
         */
        @Test
        @DisplayName("status=null 时按 unknown 计数")
        void shouldRecordUnknownResultWhenStatusNull() {
            when(repository.save(any())).thenReturn(Mono.empty());
            ApiCallLogEntity entity = buildBaseEntity();
            entity.setStatus(null);
            entity.setExecutionTime(null);

            StepVerifier.create(service.log(entity)).verifyComplete();

            assertEquals(
                    1.0,
                    meterRegistry
                            .get("apex.api.call.result")
                            .tag("service", "wechat")
                            .tag("result", "unknown")
                            .counter()
                            .count(),
                    "status 为 null 时应按 unknown 计数");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建含基础字段的 API 调用日志实体。
     *
     * @return 预填充的日志实体
     */
    private ApiCallLogEntity buildBaseEntity() {
        ApiCallLogEntity entity = new ApiCallLogEntity();
        entity.setServiceName("wechat");
        entity.setMethod("POST");
        entity.setPath("/sns/oauth2/access_token");
        entity.setUrl("https://api.weixin.qq.com/sns/oauth2/access_token?appid=test");
        entity.setRequestHeaders("{\"Content-Type\":\"application/x-www-form-urlencoded\"}");
        entity.setResponseStatus("200");
        entity.setResponseHeaders("{\"Content-Type\":\"application/json\"}");
        entity.setExecutionTime(150L);
        entity.setStatus(0);
        entity.setTraceId("trace-test-001");
        return entity;
    }
}
