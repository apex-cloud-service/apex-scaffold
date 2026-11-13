package com.apex.scaffold.office.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.application.port.OfficeMessageStatsQueryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 办公模块站内信统计查询端口实现
 *
 * <p>使用 {@link JdbcTemplate} 显式 SQL，而非 MyBatis-Plus 通道：内部统计端点无租户上下文，
 * 租户条件须显式携带，不能依赖租户行级拦截器自动改写。阻塞查询通过专用调度器隔离。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OfficeMessageStatsQueryPortImpl implements OfficeMessageStatsQueryPort {

    private static final String COUNT_MESSAGES_SQL =
            "SELECT COUNT(*) FROM off_message WHERE deleted = 0 AND tenant_id = ?";

    private final JdbcTemplate jdbcTemplate;
    private final BlockingIoSchedulers blockingIoSchedulers;

    @Override
    public Mono<Long> countMessages(String tenantId) {
        return Mono.fromCallable(() -> {
                    Long count = jdbcTemplate.queryForObject(COUNT_MESSAGES_SQL, Long.class, tenantId);
                    return count == null ? 0L : count;
                })
                .subscribeOn(blockingIoSchedulers.jdbc());
    }
}
