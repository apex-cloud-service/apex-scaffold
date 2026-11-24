package com.apex.scaffold.integration.connector.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.Credential;
import com.apex.scaffold.integration.connector.domain.CredentialCipher;
import com.apex.scaffold.integration.connector.domain.repository.CredentialRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 连接器配置凭据引用解析器（方案 M5 / 批次 E2）。
 *
 * <p>遍历 {@code config_value} JSON 树：{@code credentialRef} / {@code secretRef}
 * 键引用 {@code int_credential.credential_key}，解析为解密后的明文并以 {@code secret}
 * 键注入副本；其余嵌套 Map 递归处理。明文仅存在于本次调用的瞬时内存，不落日志。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialReferenceResolver {

    private static final String CREDENTIAL_REF_KEY = "credentialRef";
    private static final String SECRET_REF_KEY = "secretRef";
    private static final String SECRET_KEY = "secret";

    private final CredentialRepository credentialRepository;

    private final CredentialCipher cipher;

    private final ObjectMapper objectMapper;

    /**
     * 解析配置 JSON 文本中的凭据引用。
     *
     * @param tenantId    租户编码
     * @param configValue 配置 JSON 文本
     * @return 解析后的配置（敏感项已替换为解密明文）
     */
    public Mono<Map<String, Object>> resolve(String tenantId, String configValue) {
        Map<String, Object> root;
        try {
            root = objectMapper.readValue(
                    configValue == null || configValue.isBlank() ? "{}" : configValue,
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "连接器配置 JSON 非法"));
        }
        return resolveNode(tenantId, root);
    }

    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> resolveNode(String tenantId, Map<String, Object> node) {
        Object ref = node.get(CREDENTIAL_REF_KEY) != null ? node.get(CREDENTIAL_REF_KEY) : node.get(SECRET_REF_KEY);
        if (ref instanceof String credentialKey && !credentialKey.isBlank()) {
            return credentialRepository
                    .findByKey(tenantId, credentialKey)
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "凭据引用不存在: " + credentialKey)))
                    .map(credential -> withSecret(node, decrypt(credential)));
        }
        Map<String, Object> resolved = new HashMap<>(node.size());
        Mono<Map<String, Object>> chain = Mono.just(resolved);
        for (Map.Entry<String, Object> entry : node.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                chain = chain.flatMap(ignored -> resolveNode(tenantId, nestedMap))
                        .doOnNext(child -> resolved.put(entry.getKey(), child))
                        .map(ignored -> resolved);
            } else {
                resolved.put(entry.getKey(), entry.getValue());
            }
        }
        return chain;
    }

    private Map<String, Object> withSecret(Map<String, Object> node, String plaintext) {
        Map<String, Object> copy = new HashMap<>(node);
        copy.remove(CREDENTIAL_REF_KEY);
        copy.remove(SECRET_REF_KEY);
        copy.put(SECRET_KEY, plaintext);
        return copy;
    }

    private String decrypt(Credential credential) {
        try {
            return cipher.decrypt(credential.getSecretCipher(), credential.getSecretIv());
        } finally {
            // 仅记录凭据元数据，不携带任何密文或明文
            log.debug(
                    "凭据已解密: credentialKey={}, versionNo={}", credential.getCredentialKey(), credential.getVersionNo());
        }
    }
}
