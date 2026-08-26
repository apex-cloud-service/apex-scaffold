package com.apex.scaffold.infrastructure.storage.config;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 火山引擎对象存储 TOS 配置属性类。
 *
 * <p>绑定 {@code storage.volcengine} 配置前缀，提供火山引擎 TOS（Tinder Object Storage）
 * 客户端的访问密钥、区域和端点等配置信息。通过 {@link VolcEngineTosConfig} 内部配置类
 * 按需创建 {@link TOSV2} 客户端 Bean。</p>
 *
 * <h2>配置示例</h2>
 * <pre>{@code * storage.volcengine.enabled=true
 * storage.volcengine.access-key=your-access-key
 * storage.volcengine.secret-key=your-secret-key
 * storage.volcengine.region=cn-beijing
 * storage.volcengine.endpoint=https://tos-cn-beijing.volces.com
 * }</pre>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see TOSV2
 * @see VolcEngineTosConfig
 */
@Data
public class VolcEngineConfig {
    private static final Logger log = LoggerFactory.getLogger(VolcEngineConfig.class);

    /** 是否启用火山云存储 */
    private boolean enabled = false;

    /** 访问密钥 */
    private String accessKey;

    /** 秘密密钥 */
    private String secretKey;

    /** 区域 */
    private String region;

    /** 端点 URL */
    private String endpoint;

    /**
     * 火山引擎 TOS 客户端配置类。
     *
     * <p>仅在 {@code storage.volcengine.enabled=true} 时生效，
     * 创建并注册 {@link TOSV2} 客户端 Bean 用于与火山引擎对象存储服务交互。</p>
     *
     * @author Apex Team
     * @version 1.0.0
     */
    @Configuration
    @ConditionalOnProperty(name = "storage.volcengine.enabled", havingValue = "true")
    public static class VolcEngineTosConfig {

        /**
         * 注册火山引擎配置属性 Bean（仅当 volcengine 启用时）。
         *
         * @return VolcEngineConfig 实例，Spring Boot 自动绑定 storage.volcengine.* 属性
         */
        @Bean
        @ConfigurationProperties(prefix = "storage.volcengine", ignoreInvalidFields = true)
        /**
         * volcEngineConfig
         *
         * @return 结果
         */
        public VolcEngineConfig volcEngineConfig() {
            return new VolcEngineConfig();
        }

        /**
         * 创建 TOS 客户端 Bean。
         *
         * @param config 火山引擎配置属性
         * @return TOSV2 客户端实例
         */
        @Bean
        /**
         * tosClient
         *
         * @param config 参数
         *
         * @return 结果
         */
        public TOSV2 tosClient(VolcEngineConfig config) {
            log.info("初始化火山引擎TOS客户端, endpoint: {}, region: {}", config.getEndpoint(), config.getRegion());
            return new TOSV2ClientBuilder()
                    .build(
                            config.getRegion(), config.getEndpoint(),
                            config.getAccessKey(), config.getSecretKey());
        }
    }
}
