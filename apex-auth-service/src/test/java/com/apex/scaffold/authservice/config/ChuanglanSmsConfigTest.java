package com.apex.scaffold.authservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.auth.domain.model.entity.PasswordRecoveryEntity;
import com.apex.scaffold.auth.domain.service.RecoveryChallengeSender;
import com.apex.scaffold.auth.infrastructure.service.RecoveryChallengeSenderImpl;
import com.apex.scaffold.authservice.AuthServiceApplication;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanProperties;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanRequestSigner;
import com.apex.scaffold.infrastructure.chuanglan.strategy.AesPhoneDecryptStrategy;
import com.apex.scaffold.infrastructure.chuanglan.strategy.PhoneDecryptStrategy;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogProperties;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogSink;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class ChuanglanSmsConfigTest {

    private static final String PHONE = "13800138000";

    private static final String CODE = "123456";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class))
            // ApiCallLogService / ApiCallLogProperties 在生产由
            // RemoteApiClientConfig 的组件扫描提供；本隔离测试不加载该自动配置，
            // 因此显式补充，避免误判为 ChuanglanSmsConfig 的职责。
            .withUserConfiguration(
                    ChuanglanSmsConfig.class, RecoveryChallengeSenderImpl.class, ApiCallLogTestSupport.class);

    @Test
    void defaultConfigurationStartsWithoutSmsCredentialsOrLogSink() {
        contextRunner.run(context -> {
            assertSmsWiring(context);
            assertThat(context).doesNotHaveBean(ApiCallLogSink.class);
            // auth-service 的组件扫描不含 com.apex.scaffold.infrastructure，
            // 因此短信与邮件实现必须由本配置类显式注册，否则 MfaServiceImpl 无法装配。
            assertThat(context).hasBean("chuanglanSmsCodeService");
            assertThat(context).hasBean("defaultSmsCodeService");
            assertThat(context).hasBean("authServiceMailService");
            // defaultSmsCodeService 必须委托到创蓝实现，避免两个 SmsCodeService 平级注入歧义。
            assertThat(context.getBean("defaultSmsCodeService")).isInstanceOf(SmsCodeService.class);
            assertThat(context.getBeansOfType(SmsCodeService.class))
                    .containsOnlyKeys("chuanglanSmsCodeService", "defaultSmsCodeService");

            ChuanglanProperties.Sms sms =
                    context.getBean(ChuanglanProperties.class).getSms();
            assertThat(sms.getAccount()).isNullOrEmpty();
            assertThat(sms.getPassword()).isNullOrEmpty();
            assertThat(sms.getTemplateId()).isNullOrEmpty();
        });
    }

    @Test
    void configuredSmsUsesSingleDedicatedClientAndRealRecoverySender() {
        AtomicInteger exchanges = new AtomicInteger();
        AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

        interceptSmsHttp(configuredSmsRunner(), request -> {
                    exchanges.incrementAndGet();
                    sentRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"code\":\"000000\",\"msgId\":\"test-message\"}")
                            .build());
                })
                .run(context -> {
                    assertSmsWiring(context);
                    assertThat(context).doesNotHaveBean(ApiCallLogSink.class);
                    assertThat(context.getBeansOfType(SmsCodeService.class))
                            .containsOnlyKeys("chuanglanSmsCodeService", "defaultSmsCodeService");
                    ChuanglanProperties.Sms sms =
                            context.getBean(ChuanglanProperties.class).getSms();
                    assertThat(sms.getAccount()).isEqualTo("test-sms-account");
                    assertThat(sms.getPassword()).isEqualTo("test-sms-password");
                    assertThat(sms.getTemplateId()).isEqualTo("test-template");

                    StepVerifier.create(context.getBean(RecoveryChallengeSender.class)
                                    .send(PasswordRecoveryEntity.CHANNEL_SMS, PHONE, CODE))
                            .expectComplete()
                            .verify(Duration.ofSeconds(1));

                    assertThat(exchanges.get()).isEqualTo(1);
                    assertThat(sentRequest.get().method()).isEqualTo(HttpMethod.POST);
                    assertThat(sentRequest.get().url().toString())
                            .isEqualTo("https://sms.example.invalid/msg/sms/v2/tpl/send");
                    assertThat(sentRequest.get().headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
                });
    }

    /**
     * {@code ApiCallLogService} 由 {@code RemoteApiClientConfig} 的组件扫描提供；
     * 生产上下文里若已存在该 bean，{@code ChuanglanSmsConfig} 不再重复注册
     * （本配置类已不再声明该 bean，重复注册风险由自动配置的 {@code @ConditionalOnMissingBean} 兜底）。
     */
    @Test
    void existingApiCallLogServiceIsNotOverriddenBySmsConfig() {
        ApiCallLogService existingLogService = mock(ApiCallLogService.class);

        contextRunner
                .withBean("apiCallLogService", ApiCallLogService.class, () -> existingLogService)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ApiCallLogService.class)).isSameAs(existingLogService);
                });
    }

    @Test
    void smsRecoveryWithoutCredentialsFailsBeforeHttpExchange() {
        assertSmsRecoveryFailsWithoutHttp(contextRunner);
    }

    @ParameterizedTest
    @ValueSource(strings = {"account", "password", "template-id"})
    void smsRecoveryRejectsMissingRequiredSettingBeforeHttpExchange(String setting) {
        assertSmsRecoveryFailsWithoutHttp(configuredSmsRunner().withPropertyValues("chuanglan.sms." + setting + "="));
    }

    @Test
    void authServiceDoesNotScanSharedInfrastructure() {
        SpringBootApplication application = AuthServiceApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(application).isNotNull();
        assertThat(application.scanBasePackages())
                .containsExactlyInAnyOrder(
                        "com.apex.scaffold.authservice", "com.apex.scaffold.auth", "com.apex.scaffold.common");
        assertThat(application.scanBasePackageClasses()).isEmpty();
    }

    private ApplicationContextRunner configuredSmsRunner() {
        return contextRunner.withPropertyValues(
                "chuanglan.sms.host=sms.example.invalid",
                "chuanglan.sms.account=test-sms-account",
                "chuanglan.sms.password=test-sms-password",
                "chuanglan.sms.template-id=test-template",
                "chuanglan.sms.signature=Test Apex");
    }

    private void assertSmsWiring(AssertableApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(ChuanglanProperties.class);
        assertThat(context).hasSingleBean(ApiCallLogProperties.class);
        assertThat(context).hasSingleBean(MeterRegistry.class);
        assertThat(context).hasSingleBean(ApiCallLogService.class);
        assertThat(context).hasSingleBean(ChuanglanRequestSigner.class);
        assertThat(context).hasSingleBean(AesPhoneDecryptStrategy.class);
        assertThat(context).hasSingleBean(PhoneDecryptStrategy.class);
        assertThat(context).hasSingleBean(WebClient.class);
        assertThat(context.getBeansOfType(WebClient.class)).containsOnlyKeys("chuanglanWebClient");
        assertThat(context).hasSingleBean(ChuanglanApiService.class);
        assertThat(context).hasSingleBean(RecoveryChallengeSenderImpl.class);
        assertThat(context).hasSingleBean(RecoveryChallengeSender.class);
    }

    private void assertSmsRecoveryFailsWithoutHttp(ApplicationContextRunner runner) {
        AtomicInteger exchanges = new AtomicInteger();

        interceptSmsHttp(runner, request -> {
                    exchanges.incrementAndGet();
                    return Mono.error(new IllegalStateException("Unexpected SMS HTTP exchange"));
                })
                .run(context -> {
                    assertSmsWiring(context);
                    StepVerifier.create(context.getBean(RecoveryChallengeSender.class)
                                    .send(PasswordRecoveryEntity.CHANNEL_SMS, PHONE, CODE))
                            .expectErrorSatisfies(error -> {
                                assertThat(error).isInstanceOf(BusinessException.class);
                                assertThat(((BusinessException) error).getCode())
                                        .isEqualTo(ResultCode.SMS_SERVICE_ERROR.getCode());
                            })
                            .verify(Duration.ofSeconds(1));

                    assertThat(exchanges.get()).as("缺少短信配置时不得发起 HTTP 交换").isZero();
                });
    }

    private ApplicationContextRunner interceptSmsHttp(
            ApplicationContextRunner runner, ExchangeFunction exchangeFunction) {
        return runner.withInitializer(context -> context.getBeanFactory().addBeanPostProcessor(new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("chuanglanWebClient".equals(beanName) && bean instanceof WebClient webClient) {
                    // 保留生产配置创建的客户端与过滤器，仅替换 HTTP 交换边界，禁止真实网络访问。
                    return webClient.mutate().exchangeFunction(exchangeFunction).build();
                }
                return bean;
            }
        }));
    }

    /**
     * 生产环境由 {@code RemoteApiClientConfig} 的组件扫描提供 {@link ApiCallLogService}；
     * 本测试只加载 {@link ChuanglanSmsConfig}，故显式补充这两个协作 bean。
     */
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class ApiCallLogTestSupport {

        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
        ApiCallLogProperties apiCallLogProperties() {
            return new ApiCallLogProperties();
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
        org.springframework.data.redis.core.ReactiveStringRedisTemplate reactiveStringRedisTemplate() {
            return mock(org.springframework.data.redis.core.ReactiveStringRedisTemplate.class);
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
        io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry() {
            return io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.ofDefaults();
        }

        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
        ApiCallLogService apiCallLogService(
                ApiCallLogProperties properties,
                org.springframework.beans.factory.ObjectProvider<ApiCallLogSink> sink,
                MeterRegistry meterRegistry) {
            return new ApiCallLogService(properties, sink, meterRegistry);
        }
    }
}
