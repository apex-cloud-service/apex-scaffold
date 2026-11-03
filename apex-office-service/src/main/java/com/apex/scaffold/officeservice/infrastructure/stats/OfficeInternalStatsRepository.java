package com.apex.scaffold.officeservice.infrastructure.stats;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * office 内部统计查询仓储（方案 D4 能力计量：消息量维度）.
 *
 * <p>统计当前租户站内信总量，供 {@code GET /internal/v1/office/messages/stats}
 * 内部端点消费（system 能力计量与聚合门面）。使用 {@link JdbcTemplate} 显式 SQL
 * 而非 MyBatis-Plus 通道：内部调用无租户上下文，租户条件须显式携带，
 * 不能依赖 TenantLineInnerInterceptor 自动改写。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class OfficeInternalStatsRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 统计指定租户的站内信总量（含全部状态，逻辑删除行除外）。
     *
     * @param tenantId 租户标识（调用方保证非空）
     * @return 消息总量
     */
    public Mono<Long> countMessages(String tenantId) {
        return Mono.fromCallable(() -> jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM off_message WHERE deleted = 0 AND tenant_id = ?", Long.class, tenantId))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
