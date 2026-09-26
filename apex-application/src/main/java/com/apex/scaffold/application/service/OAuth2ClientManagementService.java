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
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2ClientManagementService {

    private final OAuth2RegisteredClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2ClientAssembler oAuth2ClientAssembler;

    public Mono<OAuth2ClientResponse> createClient(OAuth2ClientRequest request) {
        return clientRepository
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
                }))
                .map(oAuth2ClientAssembler::toResponse);
    }

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

    public Mono<Void> deleteClient(String clientId) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .flatMap(entity -> clientRepository.deleteById(entity.getId()));
    }

    public Mono<OAuth2ClientResponse> getClient(String clientId) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "客户端不存在")))
                .map(oAuth2ClientAssembler::toResponse);
    }

    public Flux<OAuth2ClientResponse> listClients(int page, int size) {
        return clientRepository.findAll(page, size).map(oAuth2ClientAssembler::toResponse);
    }

    public Mono<Long> countClients() {
        return clientRepository.count();
    }

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
        Map<String, Object> settings = new HashMap<>();
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
        Map<String, Object> settings = new HashMap<>();
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
