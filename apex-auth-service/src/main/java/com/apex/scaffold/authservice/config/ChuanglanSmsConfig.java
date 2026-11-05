package com.apex.scaffold.authservice.config;

import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanProperties;
import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanWebClientConfig;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanRequestSigner;
import com.apex.scaffold.infrastructure.chuanglan.strategy.AesPhoneDecryptStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 创蓝短信装配。
 *
 * <p>认证服务的组件扫描范围不含 {@code com.apex.scaffold.infrastructure}，创蓝相关
 * {@code @Service} / {@code @Component} 不会自动注册，因此在此显式装配密码找回 SMS
 * 通道所需的依赖链：配置属性 → 签名器 → 解密策略 → 专用 WebClient → API 服务。</p>
 *
 * <p>短信通道本身通过 {@code ObjectProvider} 软依赖获取；未配置账号时仍可启动，
 * 仅在调用 SMS 通道时返回「投递服务不可用」。</p>
 *
 * @author Apex Team
 * @since 2.4.1
 */
@Configuration
@EnableConfigurationProperties({
    ChuanglanProperties.class,
    com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerProperties.class
})
@Import(ChuanglanWebClientConfig.class)
public class ChuanglanSmsConfig {

    /**
     * API 调用观测服务兜底。
     *
     * <p>{@link ChuanglanWebClientConfig} 的 WebClient 过滤器需要该服务。生产环境由
     * {@code RemoteApiClientConfig} 的组件扫描提供（其 {@code @ComponentScan} 会注册
     * {@code ApiCallLogProperties} 与 {@code ApiCallLogService}）；但 {@code @WebFluxTest}
     * 切片不加载该自动配置，故此处用 {@code @ConditionalOnMissingBean} 兜底。</p>
     *
     * <p>兜底时直接构造 {@code ApiCallLogProperties} 而不是要求容器注入：该属性类本身带
     * {@code @Component}，若同时出现在 {@code @EnableConfigurationProperties} 中会造成
     * “expected single matching bean but found 2”，而完全不声明又会在切片测试中缺失。</p>
     *
     * @param repository 服务专属写入适配（可为空）
     * @param meterRegistry 指标注册表
     * @return 观测服务实例
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
            com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService.class)
    public com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService apiCallLogService(
            org.springframework.beans.factory.ObjectProvider<
                            com.apex.scaffold.infrastructure.remote.logging.ApiCallLogSink>
                    repository,
            io.micrometer.core.instrument.MeterRegistry meterRegistry) {
        return new com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService(
                new com.apex.scaffold.infrastructure.remote.logging.ApiCallLogProperties(), repository, meterRegistry);
    }

    /**
     * 创蓝协议签名构建器。
     *
     * @return 签名器实例
     */
    @Bean
    public ChuanglanRequestSigner chuanglanRequestSigner() {
        return new ChuanglanRequestSigner();
    }

    /**
     * 创蓝手机号解密策略。
     *
     * <p>密码找回仅使用短信接口，该策略只用于满足 {@link ChuanglanApiService}
     * 构造依赖，认证服务未启用闪验一键登录。</p>
     *
     * @return AES 解密策略实例
     */
    @Bean
    public AesPhoneDecryptStrategy chuanglanAesPhoneDecryptStrategy() {
        return new AesPhoneDecryptStrategy();
    }

    /**
     * 创蓝 API 服务。
     *
     * @param properties 创蓝配置
     * @param chuanglanWebClient 创蓝专用 WebClient
     * @param decryptStrategy 手机号解密策略
     * @param requestSigner 协议签名构建器
     * @return API 服务实例
     */
    @Bean
    public ChuanglanApiService chuanglanApiService(
            ChuanglanProperties properties,
            @Qualifier("chuanglanWebClient") WebClient chuanglanWebClient,
            AesPhoneDecryptStrategy decryptStrategy,
            ChuanglanRequestSigner requestSigner) {
        return new ChuanglanApiService(properties, chuanglanWebClient, decryptStrategy, requestSigner);
    }

    /**
     * 断路器服务。
     *
     * <p>{@code CircuitBreakerService} 位于
     * {@code com.apex.scaffold.infrastructure.circuitbreaker}，注册方式为 {@code @Service}，
     * 不在 auth-service 的扫描范围内；{@code ChuanglanGatewayImpl} 依赖它，
     * 因此必须显式注册，否则短信/MFA 链路启动失败。</p>
     *
     * @param circuitBreakerRegistry resilience4j 注册表（由 resilience4j 自动配置提供）
     * @param properties 断路器配置属性
     * @return 断路器服务实例
     */
    @Bean
    public com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService circuitBreakerService(
            io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry circuitBreakerRegistry,
            com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerProperties properties) {
        return new com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService(
                circuitBreakerRegistry, properties);
    }

    /**
     * 创蓝短信网关。
     *
     * <p>{@code ChuanglanGatewayImpl} 的 {@code @Component} 同样在扫描范围之外，
     * 且是 {@code ChuanglanSmsCodeServiceImpl} 的构造依赖。</p>
     *
     * @param chuanglanApiService 创蓝 API 服务
     * @param circuitBreakerService 断路器服务
     * @return 创蓝网关实例
     */
    @Bean
    public com.apex.scaffold.infrastructure.chuanglan.gateway.ChuanglanGatewayImpl chuanglanGateway(
            ChuanglanApiService chuanglanApiService,
            com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService circuitBreakerService) {
        return new com.apex.scaffold.infrastructure.chuanglan.gateway.ChuanglanGatewayImpl(
                chuanglanApiService, circuitBreakerService);
    }

    /**
     * 创蓝短信验证码服务（真实发送 + Redis 原子核销）。
     *
     * <p>Bean 名称 {@code chuanglanSmsCodeService} 被
     * {@code com.apex.scaffold.infrastructure.cache.service.SmsCodeServiceImpl} 通过
     * {@code @Qualifier} 引用，改名或缺失都会导致 MFA/登录验证码链路无法启动。</p>
     *
     * @param redisTemplate Redis 模板
     * @param gateway 创蓝网关
     * @return 短信验证码服务
     */
    @Bean("chuanglanSmsCodeService")
    public com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanSmsCodeServiceImpl chuanglanSmsCodeService(
            org.springframework.data.redis.core.ReactiveStringRedisTemplate redisTemplate,
            com.apex.scaffold.infrastructure.chuanglan.gateway.ChuanglanGatewayImpl gateway) {
        return new com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanSmsCodeServiceImpl(
                redisTemplate, gateway);
    }

    /**
     * 默认短信验证码服务适配器。
     *
     * <p>auth-service 的扫描范围不含 {@code com.apex.scaffold.infrastructure.cache}，
     * 而 {@code MfaServiceImpl} 直接依赖 {@code SmsCodeService}，因此显式注册该适配器。
     * 适配器把发送与核销统一委托给 {@code chuanglanSmsCodeService}，不自行生成或记录验证码。</p>
     *
     * @param delegate 创蓝短信验证码服务
     * @return 默认短信验证码服务
     */
    @Bean("defaultSmsCodeService")
    public com.apex.scaffold.infrastructure.cache.service.SmsCodeServiceImpl defaultSmsCodeService(
            @Qualifier("chuanglanSmsCodeService")
                    com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanSmsCodeServiceImpl delegate) {
        return new com.apex.scaffold.infrastructure.cache.service.SmsCodeServiceImpl(delegate);
    }

    /**
     * 邮件发送器兜底。
     *
     * <p>auth-service 未声明 {@code spring.mail.host}，Spring Boot 的
     * {@code MailSenderAutoConfiguration} 有条件装配、不会注册 {@code JavaMailSender}；
     * 而 {@code MfaServiceImpl} 通过 {@code MailService} 依赖它。这里仅在容器缺少该 bean 时
     * 提供一个未配置连接的 {@code JavaMailSenderImpl}：服务可正常启动，实际发送邮件时
     * 才因缺少 SMTP 连接失败——与 {@code MailService} 文档声明的降级语义一致。</p>
     *
     * @return 邮件发送器
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
            org.springframework.mail.javamail.JavaMailSender.class)
    public org.springframework.mail.javamail.JavaMailSender authServiceJavaMailSender() {
        return new org.springframework.mail.javamail.JavaMailSenderImpl();
    }

    /**
     * 邮件服务。
     *
     * <p>{@code MailService} 的 {@code @Service} 位于
     * {@code com.apex.scaffold.infrastructure.mail}，不在 auth-service 扫描范围内；
     * {@code MfaServiceImpl} 依赖它，故显式注册。未配置 {@code spring.mail.*} 时
     * 仍可启动，仅在真正发送邮件时失败。</p>
     *
     * @param mailSender 邮件发送器
     * @return 邮件服务实例
     */
    @Bean
    public com.apex.scaffold.infrastructure.mail.MailService authServiceMailService(
            org.springframework.mail.javamail.JavaMailSender mailSender) {
        return new com.apex.scaffold.infrastructure.mail.MailService(mailSender);
    }
}
