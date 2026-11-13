package com.apex.scaffold.infrastructure.security;

import com.apex.scaffold.domain.service.HealthSnapshotSignatureKeyPort;
import com.apex.scaffold.infrastructure.config.OperationsHealthProperties;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 体检快照签名密钥实现。
 *
 * <p>优先使用 {@code apex.operations.health.signature.secret}；未配置时复用
 * {@code apex.security.data-encryption-key}。后者已在部署校验与配置文档中要求
 * 「Base64 编码 32 字节、跨实例跨重启一致」，是天然的稳定密钥来源。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>密钥缺失时返回 {@code null}，调用方降级为 UNVERIFIABLE，不静默使用空密钥</li>
 *   <li>显式校验占位模板值（{@code CHANGE_ME}、{@code your-} 等），避免把模板值当作真实密钥</li>
 *   <li>不打印密钥内容，日志只记录来源与是否生效</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see HealthSnapshotSignatureKeyPort
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HealthSnapshotSignatureKeyAdapter implements HealthSnapshotSignatureKeyPort {

    /** 明确视为未配置的模板值前缀。 */
    private static final Set<String> PLACEHOLDER_VALUES =
            Set.of("change_me", "changeme", "your-key", "your_key", "placeholder", "todo");

    private final OperationsHealthProperties properties;

    private final Environment environment;

    @Value("${apex.security.data-encryption-key:}")
    private String dataEncryptionKey;

    @Override
    public String currentSecret() {
        if (!properties.getSignature().isEnabled()) {
            return null;
        }
        String configured = properties.getSignature().getSecret();
        String secret = configured != null && !configured.isBlank() ? configured : dataEncryptionKey;
        if (secret == null || secret.isBlank()) {
            return null;
        }
        if (isPlaceholder(secret)) {
            log.warn("体检快照签名密钥为模板占位值，已降级为 UNVERIFIABLE：profile={}", environment.getActiveProfiles());
            return null;
        }
        return secret;
    }

    private static boolean isPlaceholder(String secret) {
        String normalized = secret.trim().toLowerCase();
        return PLACEHOLDER_VALUES.stream().anyMatch(normalized::startsWith);
    }
}
