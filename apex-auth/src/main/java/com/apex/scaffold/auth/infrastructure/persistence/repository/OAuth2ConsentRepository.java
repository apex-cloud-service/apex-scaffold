package com.apex.scaffold.auth.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * OAuth2 用户授权同意仓储（统一认证收口 A5，RFC 6749 §4.1.2 语义的持久化）。
 *
 * <p>租户隔离口径：授权同意始终在认证流程内发生，租户来自请求上下文的可信参数
 * （{@code authorize} 与 {@code consent} 端点均要求已认证主体），查询按三元键
 * (tenant_id, principal, client_id) 精确匹配。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Repository
@RequiredArgsConstructor
public class OAuth2ConsentRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询主体对客户端的有效同意 scope。
     *
     * @param tenant    租户
     * @param principal 已认证主体
     * @param clientId  客户端 ID
     * @return 有效同意记录的 scope（空串表示同意记录未限定范围）；无有效同意记录时为空 Mono
     */
    public Mono<String> findActiveScope(String tenant, String principal, String clientId) {
        return Mono.fromCallable(() -> jdbcTemplate
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
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 记录同意（upsert：唯一键冲突时恢复未删除标记并刷新 scope 与同意时间）。
     *
     * <p>撤销后重新同意、扩大授权范围的场景必须更新既有行，否则表面成功而
     * 实际仍沿用旧的 deleted/scope 状态。
     *
     * @param tenant    租户
     * @param principal 已认证主体
     * @param clientId  客户端 ID
     * @param scope     同意的授权范围
     * @return 完成信号
     */
    public Mono<Void> save(String tenant, String principal, String clientId, String scope) {
        return Mono.fromCallable(() -> {
                    LocalDateTime now = LocalDateTime.now();
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
                    } catch (DuplicateKeyException ignored) {
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
                    return (Void) null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 撤销同意（逻辑删除；拒绝场景复用）。
     *
     * @param tenant    租户
     * @param principal 已认证主体
     * @param clientId  客户端 ID
     * @return 完成信号
     */
    public Mono<Void> revoke(String tenant, String principal, String clientId) {
        return Mono.fromCallable(() -> {
                    jdbcTemplate.update(
                            "UPDATE auth_oauth2_consent SET deleted = 1, update_time = ? "
                                    + "WHERE tenant_id = ? AND principal = ? AND client_id = ?",
                            LocalDateTime.now(),
                            tenant,
                            principal,
                            clientId);
                    return (Void) null;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
