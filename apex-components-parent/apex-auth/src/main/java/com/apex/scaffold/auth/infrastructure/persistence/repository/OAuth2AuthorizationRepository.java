package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.model.OAuth2Authorization;
import com.apex.scaffold.auth.domain.repository.OAuth2AuthorizationPort;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * OAuth2 授权记录持久化适配器。
 *
 * <p>凭据只以 SHA-256 哈希落库；所有访问都经 {@link JdbcTenantScope} 收口到可信租户上下文，
 * 时间统一按 UTC 写入。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.0
 */
@Repository
@RequiredArgsConstructor
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2AuthorizationRepository implements OAuth2AuthorizationPort {
    private static final String ACCESS_TOKEN_HASH = "access_token_hash";
    private static final String AUTHORIZATION_CODE_HASH = "authorization_code_hash";
    private static final String REFRESH_TOKEN_HASH = "refresh_token_hash";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Mono<Void> saveAuthorizationCode(String tenant, OAuth2AuthorizationCodeCommand command) {
        return insert(
                tenant,
                command.id(),
                command.clientId(),
                command.principalName(),
                "authorization_code",
                command.authorizedScopes(),
                command.redirectUri(),
                command.codeChallenge(),
                command.codeChallengeMethod(),
                command.authorizationCodeHash(),
                command.authorizationCodeExpiresAt(),
                null,
                null,
                null,
                null);
    }

    @Override
    public Mono<Void> saveTokenGrant(String tenant, OAuth2TokenGrantCommand command) {
        return insert(
                tenant,
                command.id(),
                command.clientId(),
                command.principalName(),
                command.authorizationGrantType(),
                command.authorizedScopes(),
                null,
                null,
                null,
                null,
                null,
                command.accessTokenHash(),
                command.accessTokenExpiresAt(),
                command.refreshTokenHash(),
                command.refreshTokenExpiresAt());
    }

    @Override
    public Mono<OAuth2Authorization> findByAuthorizationCode(String tenant, String codeHash) {
        return findByHash(tenant, AUTHORIZATION_CODE_HASH, codeHash);
    }

    @Override
    public Mono<OAuth2Authorization> findByAccessToken(String tenant, String accessTokenHash) {
        return findByHash(tenant, ACCESS_TOKEN_HASH, accessTokenHash);
    }

    @Override
    public Mono<OAuth2Authorization> findByRefreshToken(String tenant, String refreshTokenHash) {
        return findByHash(tenant, REFRESH_TOKEN_HASH, refreshTokenHash);
    }

    @Override
    public Mono<Boolean> exchangeCode(String tenant, OAuth2CodeExchangeCommand command) {
        return JdbcTenantScope.call(
                tenant,
                ignored -> jdbcTemplate.update(
                                "UPDATE oauth2_authorization SET authorization_code_hash = NULL, access_token_hash = ?, "
                                        + "access_token_expires_at = ?, refresh_token_hash = ?, refresh_token_expires_at = ?, updated_at = CURRENT_TIMESTAMP "
                                        + "WHERE tenant_id = ? AND authorization_code_hash = ? AND client_id = ? AND redirect_uri = ? "
                                        + "AND revoked = false AND authorization_code_expires_at > ?",
                                command.accessTokenHash(),
                                command.accessTokenExpiresAt(),
                                command.refreshTokenHash(),
                                command.refreshTokenExpiresAt(),
                                tenant,
                                command.authorizationCodeHash(),
                                command.clientId(),
                                command.redirectUri(),
                                LocalDateTime.now(ZoneOffset.UTC))
                        == 1);
    }

    @Override
    public Mono<Integer> revokeByTokenHash(String tenant, String tokenHash, String clientId) {
        return JdbcTenantScope.call(
                tenant,
                ignored -> jdbcTemplate.update(
                        "UPDATE oauth2_authorization SET revoked = true, updated_at = CURRENT_TIMESTAMP "
                                + "WHERE tenant_id = ? AND client_id = ? AND (access_token_hash = ? OR refresh_token_hash = ?) AND revoked = false",
                        tenant,
                        clientId,
                        tokenHash,
                        tokenHash));
    }

    @Override
    public Mono<Boolean> rotateRefresh(String tenant, OAuth2RefreshRotationCommand command) {
        return JdbcTenantScope.call(
                tenant,
                ignored -> jdbcTemplate.update(
                                "UPDATE oauth2_authorization SET refresh_token_hash = ?, refresh_token_expires_at = ?, "
                                        + "access_token_hash = ?, access_token_expires_at = ?, authorized_scopes = ?, updated_at = CURRENT_TIMESTAMP "
                                        + "WHERE tenant_id = ? AND client_id = ? AND refresh_token_hash = ? AND revoked = false "
                                        + "AND refresh_token_expires_at > ?",
                                command.newRefreshTokenHash(),
                                command.refreshTokenExpiresAt(),
                                command.accessTokenHash(),
                                command.accessTokenExpiresAt(),
                                command.authorizedScopes(),
                                tenant,
                                command.clientId(),
                                command.oldRefreshTokenHash(),
                                LocalDateTime.now(ZoneOffset.UTC))
                        == 1);
    }

    private Mono<Void> insert(
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

    /**
     * 按哈希列查询并映射为领域对象。
     *
     * <p>{@code column} 只由本类常量传入，列名拼入 SQL 前已限定在三个哈希列内。
     *
     * @param tenant 租户标识
     * @param column 哈希列名
     * @param hash   令牌或授权码哈希
     * @return 授权记录；未命中时为空 {@link Mono}
     */
    private Mono<OAuth2Authorization> findByHash(String tenant, String column, String hash) {
        return JdbcTenantScope.call(tenant, ignored -> jdbcTemplate
                        .queryForList(
                                "SELECT * FROM oauth2_authorization WHERE tenant_id = ? AND " + column + " = ?",
                                tenant,
                                hash)
                        .stream()
                        .findFirst()
                        .map(OAuth2AuthorizationRepository::toAuthorization)
                        .orElse(null))
                .map(Mono::just)
                .defaultIfEmpty(Mono.empty())
                .flatMap(mono -> mono);
    }

    private static OAuth2Authorization toAuthorization(Map<String, Object> row) {
        return new OAuth2Authorization(
                string(row, "client_id"),
                string(row, "principal_name"),
                string(row, "authorization_grant_type"),
                string(row, "authorized_scopes"),
                string(row, "redirect_uri"),
                string(row, "code_challenge"),
                string(row, "code_challenge_method"),
                string(row, AUTHORIZATION_CODE_HASH),
                time(row, "authorization_code_expires_at"),
                string(row, ACCESS_TOKEN_HASH),
                time(row, "access_token_expires_at"),
                string(row, REFRESH_TOKEN_HASH),
                time(row, "refresh_token_expires_at"),
                revoked(row));
    }

    private static String string(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : value.toString();
    }

    private static LocalDateTime time(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof LocalDateTime local) {
            return local;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private static boolean revoked(Map<String, Object> row) {
        Object value = row.get("revoked");
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }
}
