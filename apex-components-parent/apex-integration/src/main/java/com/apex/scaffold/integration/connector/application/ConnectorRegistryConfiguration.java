package com.apex.scaffold.integration.connector.application;

import com.apex.scaffold.integration.connector.domain.ConnectorLoader;
import com.apex.scaffold.integration.connector.domain.CredentialCipher;
import com.apex.scaffold.integration.connector.domain.IntegrationConnector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 连接器注册表装配（方案 M5 / 批次 E1）。
 *
 * <p>合并 Spring Bean（内置连接器）与 ServiceLoader 发现（第三方 JAR），
 * 按类型索引注入 {@link ConnectorExecutionService}。重复类型显式失败。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class ConnectorRegistryConfiguration {

    /**
     * 连接器注册表（类型 → 实现）。
     *
     * @param builtin 内置连接器（Spring Bean）
     * @return 不可变注册表
     */
    @Bean
    public java.util.Map<String, IntegrationConnector> integrationConnectors(
            ObjectProvider<IntegrationConnector> builtin) {
        return ConnectorLoader.merge(builtin.orderedStream().toList());
    }

    /**
     * 凭据加密器（AES-256-GCM，密文/IV 分列）。
     *
     * <p>密钥沿 {@code apex.security.*} 配置模式注入；校验延迟到首次加解密，
     * 未配置密钥的宿主仅在不使用凭据功能时方可正常启动。
     *
     * @param encryptionKey 配置的 32 字节加密密钥（可空）
     * @return 凭据加密器
     */
    @Bean
    public CredentialCipher credentialCipher(
            @Value("${apex.security.credential-encryption-key:}") String encryptionKey) {
        return new CredentialCipher(encryptionKey);
    }
}
