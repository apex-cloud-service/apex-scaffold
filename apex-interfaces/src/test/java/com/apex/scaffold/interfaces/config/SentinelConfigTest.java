package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.system.SystemRuleManager;
import com.alibaba.csp.sentinel.transport.endpoint.Endpoint;
import com.alibaba.csp.sentinel.transport.endpoint.Protocol;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.apex.scaffold.interfaces.util.MessageUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * SentinelConfig 流量治理配置测试
 *
 * <p>验证 SentinelConfig 的限流、熔断降级和系统保护规则的初始化逻辑，
 * 以及路径变量归一化、AI 慢调用阈值配置化和统一限流响应格式。</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SentinelConfig 流量治理配置测试")
class SentinelConfigTest {

    /** 国际化消息解析工具 Mock，按 key 返回固定文案。 */
    private MessageUtils messageUtils;

    /** 被测配置类实例。 */
    private SentinelConfig sentinelConfig;

    /**
     * 初始化被测实例与消息工具 Mock。
     *
     * <p>Mock 按消息 key 返回固定文案，便于断言限流/熔断响应的国际化消息。</p>
     */
    @BeforeEach
    void setUp() {
        messageUtils = mock(MessageUtils.class);
        // lenient：仅限流/熔断响应格式相关测试才会调用 MessageUtils
        lenient().when(messageUtils.get(any(Locale.class), anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(1);
            return switch (key) {
                case "flow.rate_limit" -> "请求过于频繁，请稍后再试";
                case "flow.circuit_breaker" -> "服务暂时不可用，请稍后再试";
                case "flow.degrade" -> "系统负载过高，请稍后再试";
                default -> key;
            };
        });
        sentinelConfig = new SentinelConfig(true, 0.8, 2000, 30000, 3000, messageUtils);
    }

    /**
     * 执行 {@code shouldInitializeFlowRules} 操作。
     */
    @Test
    @DisplayName("初始化限流规则后规则列表不应为空")
    void shouldInitializeFlowRules() {
        sentinelConfig.initFlowRules();

        assertThat(FlowRuleManager.getRules()).isNotEmpty();
    }

    /**
     * 执行 {@code shouldInitializeDegradeRules} 操作。
     */
    @Test
    @DisplayName("初始化熔断降级规则后规则列表不应为空")
    void shouldInitializeDegradeRules() {
        sentinelConfig.initDegradeRules();

        assertThat(DegradeRuleManager.getRules()).isNotEmpty();
    }

    /**
     * 执行 {@code shouldInitializeSystemRules} 操作。
     */
    @Test
    @DisplayName("初始化系统保护规则后规则列表不应为空")
    void shouldInitializeSystemRules() {
        sentinelConfig.initSystemRules();

        assertThat(SystemRuleManager.getRules()).isNotEmpty();
    }

    /**
     * 执行 {@code shouldContainLoginRule} 操作。
     */
    @Test
    @DisplayName("限流规则中应包含登录接口的规则")
    void shouldContainLoginRule() {
        sentinelConfig.initFlowRules();

        boolean hasLoginRule =
                FlowRuleManager.getRules().stream().anyMatch(rule -> "/api/v1/auth/login".equals(rule.getResource()));

        assertThat(hasLoginRule).isTrue();
    }

    /**
     * 执行 {@code shouldContainFileUploadDegradeRule} 操作。
     */
    @Test
    @DisplayName("熔断降级规则中应包含文件上传接口的规则")
    void shouldContainFileUploadDegradeRule() {
        sentinelConfig.initDegradeRules();

        boolean hasFileUploadRule = DegradeRuleManager.getRules().stream()
                .anyMatch(rule -> "/api/v1/file/upload".equals(rule.getResource()));

        assertThat(hasFileUploadRule).isTrue();
    }

    /**
     * 执行 {@code shouldContainCpuRule} 操作。
     */
    @Test
    @DisplayName("系统保护规则应包含 CPU 使用率规则")
    void shouldContainCpuRule() {
        sentinelConfig.initSystemRules();

        boolean hasCpuRule = SystemRuleManager.getRules().stream().anyMatch(rule -> rule.getHighestCpuUsage() == 0.8);

        assertThat(hasCpuRule).isTrue();
        assertThat(SystemRuleManager.getRules()).noneMatch(rule -> rule.getAvgRt() > 0);
    }

    /**
     * 验证 {@code shouldProvideCompatibleBlockRequestHandler} 场景。
     */
    @Test
    @DisplayName("WebFlux 阻塞响应应使用项目内兼容处理器")
    void shouldProvideCompatibleBlockRequestHandler() {
        assertThat(sentinelConfig.blockRequestHandler()).isNotNull();
    }

    /**
     * 验证 {@code shouldExcludeHealthProbesFromSentinelResources} 场景。
     */
    @Test
    @DisplayName("健康探针不应进入 Sentinel 流量治理")
    void shouldExcludeHealthProbesFromSentinelResources() {
        assertThat(SentinelConfig.cleanSentinelResource("/actuator/health")).isNull();
        assertThat(SentinelConfig.cleanSentinelResource("/actuator/health/liveness"))
                .isNull();
        assertThat(SentinelConfig.cleanSentinelResource("/actuator/health/readiness"))
                .isNull();
        assertThat(SentinelConfig.cleanSentinelResource("/api/v1/health")).isNull();
        assertThat(SentinelConfig.cleanSentinelResource("/health")).isNull();
    }

    /**
     * 验证 {@code shouldKeepBusinessApisAsSentinelResources} 场景。
     */
    @Test
    @DisplayName("业务接口仍应进入 Sentinel 流量治理")
    void shouldKeepBusinessApisAsSentinelResources() {
        assertThat(SentinelConfig.cleanSentinelResource("/api/v1/auth/login")).isEqualTo("/api/v1/auth/login");
    }

    /**
     * 验证 {@code shouldNormalizePathVariablesToTemplate} 场景。
     *
     * <p>核心写接口（投票提交、跳过、投票、收藏等）的运行时 URL 必须归一化为
     * 规则模板名，否则对应限流/熔断规则因资源名不匹配而失效。</p>
     */
    @Test
    @DisplayName("含路径变量的URL应归一化为规则模板")
    void shouldNormalizePathVariablesToTemplate() {
        assertThat(SentinelConfig.normalizePathVariables("/api/v1/explore/battles/123/submit"))
                .isEqualTo("/api/v1/explore/battles/{battleId}/submit");
        assertThat(SentinelConfig.normalizePathVariables("/api/v1/explore/battles/123/skip"))
                .isEqualTo("/api/v1/explore/battles/{battleId}/skip");
        assertThat(SentinelConfig.normalizePathVariables("/api/v1/media/photos/999/vote"))
                .isEqualTo("/api/v1/media/photos/{photoId}/vote");
        assertThat(SentinelConfig.normalizePathVariables("/api/v1/user/42")).isEqualTo("/api/v1/user/{userId}");
        assertThat(SentinelConfig.normalizePathVariables(null)).isNull();
    }

    /**
     * 验证 {@code shouldKeepStaticPathsUnchanged} 场景。
     */
    @Test
    @DisplayName("无路径变量的静态路径应原样保留")
    void shouldKeepStaticPathsUnchanged() {
        assertThat(SentinelConfig.normalizePathVariables("/api/v1/auth/login")).isEqualTo("/api/v1/auth/login");
        assertThat(SentinelConfig.normalizePathVariables("/api/v2/explore/choice-2/current"))
                .isEqualTo("/api/v2/explore/choice-2/current");
    }

    /**
     * 验证 {@code shouldNormalizeBusinessApiWithPathVariables} 场景。
     */
    @Test
    @DisplayName("cleanSentinelResource 对含路径变量的业务接口应归一化为模板")
    void shouldNormalizeBusinessApiWithPathVariables() {
        assertThat(SentinelConfig.cleanSentinelResource("/api/v1/explore/battles/123/submit"))
                .isEqualTo("/api/v1/explore/battles/{battleId}/submit");
    }

    /**
     * 验证 {@code shouldApplyConfiguredAiSlowCallThreshold} 场景。
     *
     * <p>AI 分析慢调用阈值来自构造配置，避免硬编码 3s 导致正常上传流量被批量误熔断。</p>
     */
    @Test
    @DisplayName("AI分析慢调用阈值应来自配置")
    void shouldApplyConfiguredAiSlowCallThreshold() {
        SentinelConfig custom = new SentinelConfig(true, 0.8, 2000, 15000, 3000, messageUtils);
        custom.initDegradeRules();

        DegradeRule faceRule = DegradeRuleManager.getRules().stream()
                .filter(rule -> "/api/v1/onboarding/face-profile".equals(rule.getResource()))
                .filter(rule -> rule.getGrade() == com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_RT)
                .findFirst()
                .orElseThrow();
        assertThat(faceRule.getCount()).isEqualTo(15000.0);
        assertThat(faceRule.getGrade()).isEqualTo(com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_RT);
    }

    /**
     * 验证 {@code shouldReturnUnifiedResultOnRateLimit} 场景。
     *
     * <p>限流响应应使用全站统一 {@link Result} 结构（code/msg/data/traceId），
     * 业务码与 IP 限流一致（2001），并携带 traceId 便于排查。</p>
     */
    @Test
    @DisplayName("限流响应应使用统一Result结构且包含traceId")
    void shouldReturnUnifiedResultOnRateLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login").header("Accept-Language", "zh-CN"));
        BlockRequestHandler handler = sentinelConfig.blockRequestHandler();

        Mono<ServerResponse> responseMono = handler.handleRequest(exchange, new FlowException("rate limited"));
        StepVerifier.create(responseMono)
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS))
                .verifyComplete();

        Result<?> result = writeAndParseBody(responseMono, exchange);
        assertThat(result.getCode()).isEqualTo(ResultCode.RATE_LIMIT.getCode());
        assertThat(result.getMsg()).isEqualTo("请求过于频繁，请稍后再试");
        assertThat(result.getTraceId()).isNotBlank();
    }

    /**
     * 验证 {@code shouldReturnServiceUnavailableOnDegrade} 场景。
     */
    @Test
    @DisplayName("熔断降级响应应使用统一Result结构且返回503")
    void shouldReturnServiceUnavailableOnDegrade() {
        MockServerWebExchange exchange =
                MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/onboarding/face-profile"));
        BlockRequestHandler handler = sentinelConfig.blockRequestHandler();

        Mono<ServerResponse> responseMono = handler.handleRequest(exchange, new DegradeException("degraded"));
        StepVerifier.create(responseMono)
                .assertNext(response -> assertThat(response.statusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
                .verifyComplete();

        Result<?> result = writeAndParseBody(responseMono, exchange);
        assertThat(result.getCode()).isEqualTo(ResultCode.DEGRADE.getCode());
        assertThat(result.getMsg()).isEqualTo("系统负载过高，请稍后再试");
        assertThat(result.getTraceId()).isNotBlank();
    }

    /**
     * 将 {@link ServerResponse} 写入模拟交换对象并解析响应体 JSON。
     *
     * <p>{@code ServerResponse} 仅在 {@code writeTo(exchange, context)} 时才真正写出响应体，
     * 此处使用 {@link ServerCodecConfigurer} 提供消息写入器，随后从
     * {@code MockServerHttpResponse.getBodyAsString()} 读取并解析为 {@link Result}。</p>
     *
     * @param responseMono 待写出的响应
     * @param exchange     模拟交换对象
     * @return 解析后的统一响应体
     */
    private Result<?> writeAndParseBody(Mono<ServerResponse> responseMono, MockServerWebExchange exchange) {
        ServerResponse.Context context = new ServerResponse.Context() {
            private final List<org.springframework.http.codec.HttpMessageWriter<?>> writers =
                    ServerCodecConfigurer.create().getWriters();

            /**
             * {@inheritDoc}
             *
             * @return 操作结果，类型为 {@code List<org.springframework.http.codec.HttpMessageWriter<?>>}
             */
            @Override
            public List<org.springframework.http.codec.HttpMessageWriter<?>> messageWriters() {
                return writers;
            }

            /**
             * {@inheritDoc}
             *
             * @return 操作结果，类型为 {@code List<org.springframework.web.reactive.result.view.ViewResolver>}
             */
            @Override
            public List<org.springframework.web.reactive.result.view.ViewResolver> viewResolvers() {
                return List.of();
            }
        };
        responseMono.flatMap(response -> response.writeTo(exchange, context)).block();
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).isNotBlank();
        return JsonUtil.fromJson(body, Result.class);
    }

    @Test
    @DisplayName("Nacos限流模板应覆盖全部静态限流规则")
    void shouldKeepFlowTemplateConsistentWithStaticRules() throws Exception {
        sentinelConfig.initFlowRules();
        List<Map<String, Object>> template = readRuleTemplate("/sentinel/apex-sentinel-flow-rules.json");

        assertThat(template).hasSize(43);
        assertThat(template).extracting(rule -> rule.get("resource"))
                .containsExactlyInAnyOrderElementsOf(
                        FlowRuleManager.getRules().stream().map(rule -> rule.getResource()).toList());
        assertThat(template).allSatisfy(rule -> {
            assertThat(rule.get("grade")).isEqualTo(1);
            assertThat(rule.get("limitApp")).isEqualTo("default");
        });
    }

    @Test
    @DisplayName("Nacos降级模板应覆盖全部静态降级规则")
    void shouldKeepDegradeTemplateConsistentWithStaticRules() throws Exception {
        sentinelConfig.initDegradeRules();
        List<Map<String, Object>> template = readRuleTemplate("/sentinel/apex-sentinel-degrade-rules.json");

        assertThat(template).hasSize(30);
        assertThat(template).extracting(rule -> rule.get("resource"))
                .containsExactlyInAnyOrderElementsOf(
                        DegradeRuleManager.getRules().stream().map(rule -> rule.getResource()).toList());
        assertThat(template).allSatisfy(rule -> {
            assertThat(rule.get("grade")).isIn(
                    RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO,
                    RuleConstant.DEGRADE_GRADE_RT);
            assertThat(rule.get("minRequestAmount")).isEqualTo(5);
            if (Integer.valueOf(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO).equals(rule.get("grade"))) {
                assertThat(rule).doesNotContainKey("statIntervalMs");
            } else {
                assertThat(rule.get("statIntervalMs")).isEqualTo(30000);
            }
        });
    }

    private List<Map<String, Object>> readRuleTemplate(String resource) throws Exception {
        Path templatePath = Path.of(System.getProperty("user.dir"), "..", "config", "sentinel", resource.substring(resource.lastIndexOf('/') + 1))
                .toAbsolutePath()
                .normalize();
        assertThat(Files.exists(templatePath)).isTrue();
        return JsonUtil.fromJson(
                Files.readString(templatePath),
                new TypeReference<>() {});
    }

    /**
     * 验证 {@code shouldEncodeSentinelHeartbeatForm} 场景。
     */
    @Test
    @DisplayName("Sentinel 心跳参数应使用标准表单编码")
    void shouldEncodeSentinelHeartbeatForm() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("app", "apex service");
        values.put("ip", "127.0.0.1");

        assertThat(JdkHttpHeartbeatSender.encodeForm(values)).isEqualTo("app=apex+service&ip=127.0.0.1");
    }

    /**
     * 验证 {@code shouldBuildSentinelHeartbeatUri} 场景。
     */
    @Test
    @DisplayName("Sentinel 心跳地址应指向控制台注册接口")
    void shouldBuildSentinelHeartbeatUri() {
        Endpoint endpoint = new Endpoint(Protocol.HTTP, "124.174.25.161", 8859);

        assertThat(JdkHttpHeartbeatSender.buildHeartbeatUri(endpoint))
                .hasToString("http://124.174.25.161:8859/registry/machine");
    }
}
