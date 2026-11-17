package com.apex.scaffold.office.infrastructure.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 办公平台集成配置。
 *
 * <p>绑定 {@code apex.office} 前缀配置，经 {@code @ConfigurationPropertiesScan} 自动注册为 Bean。
 * 包含登录租户、办公集成总开关、调用超时与最大重试次数，并按 {@code OfficePlatform}
 * 维度组织各平台配置（appId、密钥环境变量引用、OAuth 回跳地址、签名密钥引用、授权 scopes），
 * 供钉钉、飞书、企业微信的 OAuth 客户端与组织架构客户端读取。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Data
@ConfigurationProperties(prefix = "apex.office")
public class OfficePlatformProperties {
    private String loginTenantId;
    private boolean enabled;
    private Duration timeout = Duration.ofSeconds(5);
    private int maxRetries = 2;
    private Map<com.apex.scaffold.office.domain.OfficePlatform, Platform> platforms =
            new EnumMap<>(com.apex.scaffold.office.domain.OfficePlatform.class);

    /**
     * 单个办公平台的接入参数，未配置的字段沿用全局默认值。
     */
    @Data
    public static class Platform {
        private boolean enabled;
        private String appId;
        private String secretReference;
        /**
         * 平台 API 基址覆盖；留空时使用各客户端内置的官方地址。
         *
         * <p>仅用于本地联调与私有化部署指向兼容网关，默认不改变生产地址。
         */
        private String baseUrl;

        private String redirectUri;
        private String signingSecretReference;
        private String agentId;
        private java.util.Set<String> scopes = new java.util.HashSet<>();
    }
}
