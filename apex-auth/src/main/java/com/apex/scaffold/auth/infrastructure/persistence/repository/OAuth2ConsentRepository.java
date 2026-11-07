package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.auth.domain.repository.OAuth2ConsentPort;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * OAuth2 用户授权同意仓储（统一认证收口 A5，RFC 6749 §4.1.2 语义的持久化）。
 *
 * <p>租户隔离口径：授权同意始终在认证流程内发生，租户经 {@link JdbcTenantScope}
 * 收口到可信上下文，查询按三元键 (tenant_id, principal, client_id) 精确匹配。
 * 时间统一按 UTC 写入。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class OAuth2ConsentRepository implements OAuth2ConsentPort {

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

    /**
     * 记录同意（upsert：唯一键冲突时恢复未删除标记并刷新 scope 与同意时间）。
     *
     * <p>撤销后重新同意、扩大授权范围的场景必须更新既有行，否则表面成功而
     * 实际仍沿用旧的 deleted/scope 状态。
     */
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
                        // upsert：恢复逻辑删除并刷新 scope/同意时间（version 递增避免乐观锁滞后）
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
