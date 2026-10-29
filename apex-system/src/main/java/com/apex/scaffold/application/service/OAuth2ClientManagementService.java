package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.OAuth2ClientAssembler;
import com.apex.scaffold.application.dto.OAuth2ClientRequest;
import com.apex.scaffold.application.dto.OAuth2ClientResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth2客户端管理服务
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientManagementService {

    private final OAuth2RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2ClientAssembler oAuth2ClientAssembler;
    private final TenantGuard tenantGuard;

    /**
     * 注册 OAuth2 客户端
     *
     * <p>校验客户端 ID 唯一后创建客户端记录，密钥经 {@link PasswordEncoder} 加密存储，
     * 并写入客户端设置（PKCE、授权同意）与令牌设置（各类令牌 TTL）。
     *
     * @param request 客户端注册请求（含客户端 ID、名称、认证方式、授权类型、回调地址、作用域及令牌配置）
     * @return 创建后的客户端响应；客户端 ID 已存在时 Mono 以 BusinessException（PARAM_ERROR）错误终止
     */
    public Mono<OAuth2ClientResponse> createClient(OAuth2ClientRequest request) {
        return tenantGuard
                .assertActive()
                .then(Mono.defer(() -> clientRepository
                        .findByClientId(request.getClientId())
                        .flatMap(existing -> Mono.<OAuth2RegisteredClientEntity>error(
                                new BusinessException(ResultCode.PARAM_ERROR, "客户端ID已存在")))
                        .switchIfEmpty(Mono.defer(() -> {
                            OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
                            entity.setId(UUID.randomUUID().toString());
                            entity.setClientId(request.getClientId());

                            if (request.getClientSecret() != null
                                    && !request.getClientSecret().isEmpty()) {
                                entity.setClientSecret(passwordEncoder.encode(request.getClientSecret()));
                            }

                            entity.setClientName(request.getClientName());
                            entity.setClientIdIssuedAt(Instant.now());
                            entity.setClientAuthenticationMethods(request.getClientAuthenticationMethods());
                            entity.setAuthorizationGrantTypes(request.getAuthorizationGrantTypes());
                            entity.setRedirectUris(request.getRedirectUris());
                            entity.setPostLogoutRedirectUris(request.getPostLogoutRedirectUris());
                            entity.setScopes(request.getScopes());
                            entity.setClientSettings(buildClientSettings(request));
                            entity.setTokenSettings(buildTokenSettings(request));

                            return clientRepository.save(entity);
                        }))))
                .map(oAuth2ClientAssembler::toResponse);
    }

    /**
     * 更新 OAuth2 客户端
     *
     * <p>按客户端 ID 更新客户端配置，请求中非空字段生效，客户端设置与令牌设置整体重建。
     *
     * @param clientId 客户端 ID
     * @param request 客户端更新请求
     * @return 更新后的客户端响应；客户端不存在时 Mono 以 BusinessException（NOT_FOUND）错误终止
     */
    public Mono<OAuth2ClientResponse> updateClient(String clientId, OAuth2ClientRequest request) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .flatMap(entity -> {
                    if (request.getClientSecret() != null
                            && !request.getClientSecret().isEmpty()) {
                        entity.setClientSecret(passwordEncoder.encode(request.getClientSecret()));
                    }

                    if (request.getClientName() != null) {
                        entity.setClientName(request.getClientName());
                    }

                    if (request.getClientAuthenticationMethods() != null) {
                        entity.setClientAuthenticationMethods(request.getClientAuthenticationMethods());
                    }

                    if (request.getAuthorizationGrantTypes() != null) {
                        entity.setAuthorizationGrantTypes(request.getAuthorizationGrantTypes());
                    }

                    if (request.getRedirectUris() != null) {
                        entity.setRedirectUris(request.getRedirectUris());
                    }

                    if (request.getScopes() != null) {
                        entity.setScopes(request.getScopes());
                    }

                    entity.setClientSettings(buildClientSettings(request));
                    entity.setTokenSettings(buildTokenSettings(request));

                    return clientRepository.save(entity);
                })
                .map(oAuth2ClientAssembler::toResponse);
    }

    /**
     * 删除 OAuth2 客户端
     *
     * @param clientId 客户端 ID
     */
    public Mono<Void> deleteClient(String clientId) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .flatMap(entity -> clientRepository.deleteById(entity.getId()));
    }

    /**
     * 查询 OAuth2 客户端详情
     *
     * @param clientId 客户端 ID
     * @return 客户端响应；客户端不存在时 Mono 以 BusinessException（NOT_FOUND）错误终止
     */
    public Mono<OAuth2ClientResponse> getClient(String clientId) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .map(oAuth2ClientAssembler::toResponse);
    }

    /**
     * 分页查询 OAuth2 客户端列表
     *
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 当前页客户端响应流
     */
    public Flux<OAuth2ClientResponse> listClients(int page, int size) {
        return clientRepository.findAll(page, size).map(oAuth2ClientAssembler::toResponse);
    }

    /**
     * 统计 OAuth2 客户端总数
     *
     * @return 客户端总数
     */
    public Mono<Long> countClients() {
        return clientRepository.count();
    }

    /**
     * 重置 OAuth2 客户端密钥
     *
     * <p>生成新的随机密钥并加密落库，明文密钥仅在本次返回中可见，请妥善保存。
     *
     * @param clientId 客户端 ID
     * @return 新生成的明文密钥；客户端不存在时 Mono 以 BusinessException（NOT_FOUND）错误终止
     */
    public Mono<String> resetClientSecret(String clientId) {
        String newSecret = UUID.randomUUID().toString().replace("-", "");

        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .flatMap(entity -> {
                    entity.setClientSecret(passwordEncoder.encode(newSecret));
                    return clientRepository.save(entity);
                })
                .thenReturn(newSecret);
    }

    private String buildClientSettings(OAuth2ClientRequest request) {
        Map<String, Object> settings = new HashMap<>(16);
        settings.put("@class", "java.util.HashMap");

        if (request.getRequireProofKey() != null) {
            settings.put("settings.client.require-proof-key", request.getRequireProofKey());
        }

        if (request.getRequireAuthorizationConsent() != null) {
            settings.put("settings.client.require-authorization-consent", request.getRequireAuthorizationConsent());
        }

        return serializeSettings(settings);
    }

    private String buildTokenSettings(OAuth2ClientRequest request) {
        Map<String, Object> settings = new HashMap<>(16);
        settings.put("@class", "java.util.HashMap");

        if (request.getAuthorizationCodeTimeToLive() != null) {
            settings.put(
                    "settings.token.authorization-code-time-to-live",
                    java.time.Duration.ofSeconds(request.getAuthorizationCodeTimeToLive()));
        }

        if (request.getAccessTokenTimeToLive() != null) {
            settings.put(
                    "settings.token.access-token-time-to-live",
                    java.time.Duration.ofSeconds(request.getAccessTokenTimeToLive()));
        }

        if (request.getRefreshTokenTimeToLive() != null) {
            settings.put(
                    "settings.token.refresh-token-time-to-live",
                    java.time.Duration.ofSeconds(request.getRefreshTokenTimeToLive()));
        }

        settings.put("settings.token.reuse-refresh-tokens", false);

        return serializeSettings(settings);
    }

    private String serializeSettings(Map<String, Object> settings) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;

            json.append("\"").append(entry.getKey()).append("\":");

            Object value = entry.getValue();
            if (value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof java.time.Duration) {
                java.time.Duration duration = (java.time.Duration) value;
                json.append("[\"java.time.Duration\",")
                        .append(duration.getSeconds())
                        .append("]");
            } else {
                json.append("\"").append(value).append("\"");
            }
        }

        json.append("}");
        return json.toString();
    }
}
