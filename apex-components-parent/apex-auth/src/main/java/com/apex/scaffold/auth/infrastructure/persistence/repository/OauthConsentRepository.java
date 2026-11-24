package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.repository.OauthConsentPort;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * OAuth2 用户授权同意仓储。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class OauthConsentRepository implements OauthConsentPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Mono<String> findActiveScope(String tenant, String principal, String clientId) {
        return JdbcTenantScope.call(tenant, ignored -> jdbcTemplate
                        .query(
                                "SELECT scope FROM auth_oauth2_consent "
                                        + "WHERE tenant_id = ? AND principal = ? AND client_id = ? AND deleted = 0",
                                (rs, rowNum) -> rs.getString("scope"),
                                tenant,
                                principal,
                                clientId)
                        .stream()
                        .findFirst()
                        .map(scope -> scope == null ? "" : scope)
                        .orElse(null))
                .map(Mono::just)
                .defaultIfEmpty(Mono.empty())
                .flatMap(mono -> mono);
    }

    @Override
    public Mono<Void> save(String tenant, String principal, String clientId, String scope) {
        return JdbcTenantScope.call(tenant, ignored -> {
                    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
                    try {
                        jdbcTemplate.update(
                                "INSERT INTO auth_oauth2_consent "
                                        + "(id, tenant_id, principal, client_id, scope, consented_at, version, deleted, create_time, update_time) "
                                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                                System.currentTimeMillis() + UUID.randomUUID().hashCode(),
                                tenant,
                                principal,
                                clientId,
                                scope,
                                now,
                                0,
                                0,
                                now,
                                now);
                    } catch (DuplicateKeyException duplicate) {
                        jdbcTemplate.update(
                                "UPDATE auth_oauth2_consent "
                                        + "SET scope = ?, consented_at = ?, deleted = 0, version = version + 1, update_time = ? "
                                        + "WHERE tenant_id = ? AND principal = ? AND client_id = ?",
                                scope,
                                now,
                                now,
                                tenant,
                                principal,
                                clientId);
                    }
                    return null;
                })
                .then();
    }

    @Override
    public Mono<Void> revoke(String tenant, String principal, String clientId) {
        return JdbcTenantScope.call(
                        tenant,
                        ignored -> jdbcTemplate.update(
                                "UPDATE auth_oauth2_consent SET deleted = 1, update_time = ? "
                                        + "WHERE tenant_id = ? AND principal = ? AND client_id = ?",
                                LocalDateTime.now(ZoneOffset.UTC),
                                tenant,
                                principal,
                                clientId))
                .then();
    }
}
