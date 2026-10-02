package com.apex.scaffold.authcenter.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2AuthorizationRepository {
    private static final String ACCESS_TOKEN_HASH = "access_token_hash";
    private static final String AUTHORIZATION_CODE_HASH = "authorization_code_hash";
    private static final String REFRESH_TOKEN_HASH = "refresh_token_hash";

    private final JdbcTemplate jdbcTemplate;

    public Mono<Void> insert(
            String tenant,
            String id,
            String clientId,
            String principal,
            String grantType,
            String scopes,
            String redirectUri,
            String challenge,
            String challengeMethod,
            String codeHash,
            LocalDateTime codeExpires,
            String accessHash,
            LocalDateTime accessExpires,
            String refreshHash,
            LocalDateTime refreshExpires) {
        return JdbcTenantScope.call(tenant, ignored -> {
                    jdbcTemplate.update(
                            "INSERT INTO oauth2_authorization "
                                    + "(id, tenant_id, registered_client_id, client_id, principal_name, authorization_grant_type, "
                                    + "authorized_scopes, redirect_uri, code_challenge, code_challenge_method, authorization_code_hash, authorization_code_expires_at, "
                                    + "access_token_hash, access_token_expires_at, refresh_token_hash, "
                                    + "refresh_token_expires_at, revoked, created_at, updated_at) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                            id,
                            tenant,
                            clientId,
                            clientId,
                            principal,
                            grantType,
                            scopes,
                            redirectUri,
                            challenge,
                            challengeMethod,
                            codeHash,
                            codeExpires,
                            accessHash,
                            accessExpires,
                            refreshHash,
                            refreshExpires);
                    return null;
                })
                .then();
    }

    public Mono<Map<String, Object>> findByHash(String tenant, String column, String hash) {
        if (!Set.of(ACCESS_TOKEN_HASH, REFRESH_TOKEN_HASH, AUTHORIZATION_CODE_HASH)
                .contains(column)) {
            return Mono.error(new IllegalArgumentException("Invalid token column"));
        }
        return JdbcTenantScope.call(tenant, ignored -> jdbcTemplate
                .queryForList(
                        "SELECT * FROM oauth2_authorization WHERE tenant_id = ? AND " + column + " = ?", tenant, hash)
                .stream()
                .findFirst()
                .orElse(null));
    }

    public Mono<Boolean> exchangeCode(
            String tenant,
            String hash,
            String clientId,
            String redirect,
            String accessHash,
            LocalDateTime accessExpires,
            String refreshHash,
            LocalDateTime refreshExpires) {
        return JdbcTenantScope.call(
                tenant,
                ignored -> jdbcTemplate.update(
                                "UPDATE oauth2_authorization SET authorization_code_hash = NULL, access_token_hash = ?, "
                                        + "access_token_expires_at = ?, refresh_token_hash = ?, refresh_token_expires_at = ?, updated_at = CURRENT_TIMESTAMP "
                                        + "WHERE tenant_id = ? AND authorization_code_hash = ? AND client_id = ? AND redirect_uri = ? "
                                        + "AND revoked = false AND authorization_code_expires_at > ?",
                                accessHash,
                                accessExpires,
                                refreshHash,
                                refreshExpires,
                                tenant,
                                hash,
                                clientId,
                                redirect,
                                LocalDateTime.now(java.time.ZoneOffset.UTC))
                        == 1);
    }

    public Mono<Integer> revokeByHash(String tenant, String hash, String clientId) {
        return JdbcTenantScope.call(
                tenant,
                ignored -> jdbcTemplate.update(
                        "UPDATE oauth2_authorization SET revoked = true, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE tenant_id = ? AND client_id = ? AND (access_token_hash = ? OR refresh_token_hash = ?) AND revoked = false",
                        tenant,
                        clientId,
                        hash,
                        hash));
    }

    public Mono<Boolean> rotateRefresh(
            String tenant,
            String clientId,
            String oldHash,
            String newHash,
            LocalDateTime newExpires,
            String accessHash,
            LocalDateTime accessExpires,
            String scope) {
        return JdbcTenantScope.call(
                tenant,
                ignored -> jdbcTemplate.update(
                                "UPDATE oauth2_authorization SET refresh_token_hash = ?, refresh_token_expires_at = ?, "
                                        + "access_token_hash = ?, access_token_expires_at = ?, authorized_scopes = ?, updated_at = CURRENT_TIMESTAMP "
                                        + "WHERE tenant_id = ? AND client_id = ? AND refresh_token_hash = ? AND revoked = false "
                                        + "AND refresh_token_expires_at > ?",
                                newHash,
                                newExpires,
                                accessHash,
                                accessExpires,
                                scope,
                                tenant,
                                clientId,
                                oldHash,
                                LocalDateTime.now(java.time.ZoneOffset.UTC))
                        == 1);
    }
}
