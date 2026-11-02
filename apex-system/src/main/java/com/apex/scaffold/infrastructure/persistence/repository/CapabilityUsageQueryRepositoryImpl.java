package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.repository.CapabilityUsageQueryRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 能力调用用量只读查询的 JDBC 实现（方案 D4 计量）。
 *
 * <p>基于 {@code JdbcTemplate} 只读聚合 {@code sys_operation_log}：
 * 该表自 V14 起携带 {@code tenant_id} 列与租户索引，适合按
 * 租户 × 能力（请求 URI 前缀清单）做计数聚合。
 *
 * <p>隔离说明：JdbcTemplate 直查不经过 MyBatis-Plus 租户行级拦截器，
 * 因此 {@code tenant_id} 过滤条件必须在 SQL 中显式携带；
 * {@code tenantId} 为空表示平台视角聚合全部租户，仅限持有
 * {@code capability:usage:read} 权限的管理入口调用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class CapabilityUsageQueryRepositoryImpl implements CapabilityUsageQueryRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Mono<List<TenantUsageCount>> countCallsByUriPrefixes(
            List<String> uriPrefixes, LocalDateTime from, LocalDateTime to, String tenantId) {
        if (uriPrefixes == null || uriPrefixes.isEmpty()) {
            return Mono.error(new IllegalArgumentException("uriPrefixes 不能为空"));
        }
        return Mono.fromCallable(() -> query(uriPrefixes, from, to, tenantId)).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 执行聚合查询。
     *
     * @param uriPrefixes 请求 URI 前缀清单
     * @param from        起始时间（含）
     * @param to          结束时间（含）
     * @param tenantId    租户标识；为空时聚合全部租户
     * @return 租户维度调用计数
     */
    private List<TenantUsageCount> query(
            List<String> uriPrefixes, LocalDateTime from, LocalDateTime to, String tenantId) {
        String likeClause = String.join(" OR ", Collections.nCopies(uriPrefixes.size(), "request_uri LIKE ?"));
        StringBuilder sql = new StringBuilder(256)
                .append("SELECT tenant_id, COUNT(*) AS call_count FROM sys_operation_log ")
                .append("WHERE deleted = 0 AND operation_time >= ? AND operation_time <= ? AND (")
                .append(likeClause)
                .append(")");
        List<Object> params = new ArrayList<>(uriPrefixes.size() + 3);
        params.add(from);
        params.add(to);
        for (String prefix : uriPrefixes) {
            params.add(prefix + "%");
        }
        if (tenantId != null && !tenantId.isBlank()) {
            sql.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        sql.append(" GROUP BY tenant_id ORDER BY tenant_id");
        return jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> new TenantUsageCount(rs.getString("tenant_id"), rs.getLong("call_count")),
                params.toArray());
    }
}
