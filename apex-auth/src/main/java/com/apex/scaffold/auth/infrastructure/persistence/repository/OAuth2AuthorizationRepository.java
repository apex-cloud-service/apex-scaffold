package com.apex.scaffold.auth.infrastructure.persistence.repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * OAuth2 授权数据持久化仓储。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.0
 */
@Repository
@RequiredArgsConstructor
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2AuthorizationRepository {
    private static final String ACCESS_TOKEN_HASH = "access_token_hash";
    private static final String AUTHORIZATION_CODE_HASH = "authorization_code_hash";
    private static final String REFRESH_TOKEN_HASH = "refresh_token_hash";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 插入OAuth2授权记录
     *
     * @param tenant 租户ID
     * @param id 授权记录ID
     * @param clientId 客户端ID
     * @param principal 资源所有者标识
     * @param grantType 授权类型
     * @param scopes 授权范围
     * @param redirectUri 重定向URI
     * @param challenge PKCE码挑战
     * @param challengeMethod PKCE挑战方法
     * @param codeHash 授权码哈希
     * @param codeExpires 授权码过期时间
     * @param accessHash 访问令牌哈希
     * @param accessExpires 访问令牌过期时间
     * @param refreshHash 刷新令牌哈希
     * @param refreshExpires 刷新令牌过期时间
     */
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

    /**
     * 用授权码兑换访问/刷新令牌（授权码一次性使用，兑换后置空）
     *
     * @param tenant 租户ID
     * @param hash 授权码哈希
     * @param clientId 客户端ID
     * @param redirect 重定向URI
     * @param accessHash 访问令牌哈希
     * @param accessExpires 访问令牌过期时间
     * @param refreshHash 刷新令牌哈希
     * @param refreshExpires 刷新令牌过期时间
     * @return 兑换成功时为true
     */
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

    /**
     * 按令牌哈希撤销指定客户端的授权记录
     *
     * @param tenant 租户ID
     * @param hash 令牌哈希（访问或刷新令牌）
     * @param clientId 客户端ID
     * @return 撤销的记录数量
     */
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

    /**
     * 轮换刷新令牌并同步更新访问令牌与授权范围
     *
     * @param tenant 租户ID
     * @param clientId 客户端ID
     * @param oldHash 旧刷新令牌哈希
     * @param newHash 新刷新令牌哈希
     * @param newExpires 新刷新令牌过期时间
     * @param accessHash 新访问令牌哈希
     * @param accessExpires 新访问令牌过期时间
     * @param scope 授权范围
     * @return 轮换成功时为true
     */
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
