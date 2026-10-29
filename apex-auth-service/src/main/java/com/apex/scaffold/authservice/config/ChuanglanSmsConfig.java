package com.apex.scaffold.authservice.config;

import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanProperties;
import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanWebClientConfig;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanRequestSigner;
import com.apex.scaffold.infrastructure.chuanglan.strategy.AesPhoneDecryptStrategy;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogProperties;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogSink;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
@EnableConfigurationProperties({ChuanglanProperties.class, ApiCallLogProperties.class})
@Import(ChuanglanWebClientConfig.class)
public class ChuanglanSmsConfig {

    /**
     * API 调用观测服务。
     *
     * <p>{@link ChuanglanWebClientConfig} 的 WebClient 过滤器需要该服务；认证服务不在
     * {@code com.apex.scaffold.infrastructure} 的扫描范围内，故显式注册。认证服务未提供
     * {@link ApiCallLogSink} 适配，观测仅做脱敏与指标统计，不落库。</p>
     *
     * @param properties API 调用日志配置
     * @param repository 服务专属写入适配（可为空）
     * @param meterRegistry 指标注册表
     * @return 观测服务实例
     */
    @Bean
    @ConditionalOnMissingBean(ApiCallLogService.class)
    public ApiCallLogService apiCallLogService(
            ApiCallLogProperties properties, ObjectProvider<ApiCallLogSink> repository, MeterRegistry meterRegistry) {
        return new ApiCallLogService(properties, repository, meterRegistry);
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
}
