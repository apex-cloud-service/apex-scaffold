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
import com.apex.scaffold.infrastructure.mail.MailService;
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
            .withUserConfiguration(ChuanglanSmsConfig.class, RecoveryChallengeSenderImpl.class);

    @Test
    void defaultConfigurationStartsWithoutSmsCredentialsOrLogSink() {
        contextRunner.run(context -> {
            assertSmsWiring(context);
            assertThat(context).doesNotHaveBean(ApiCallLogSink.class);
            assertThat(context).doesNotHaveBean(MailService.class);
            assertThat(context).doesNotHaveBean(SmsCodeService.class);

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
                    assertThat(context).doesNotHaveBean(SmsCodeService.class);
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

    @Test
    void existingApiCallLogServicePreventsDuplicateRegistration() {
        ApiCallLogService existingLogService = mock(ApiCallLogService.class);

        contextRunner
                .withBean("existingApiCallLogService", ApiCallLogService.class, () -> existingLogService)
                .run(context -> {
                    assertSmsWiring(context);
                    assertThat(context.getBeansOfType(ApiCallLogService.class))
                            .containsOnlyKeys("existingApiCallLogService");
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
}
