package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.workflow.domain.repository.ScanLease;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 基于数据库单行条件更新的调度租约。
 *
 * <p>时间比较在 Java 侧计算完成后以参数绑定，避免 MySQL/PostgreSQL/H2 的
 * 时间函数与类型差异。抢占与释放均为单条条件 UPDATE，影响行数即仲裁结果。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class JdbcScanLease implements ScanLease {
    private final JdbcTemplate jdbcTemplate;

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> acquire(String name, String owner, Duration ttl) {
        return Mono.fromCallable(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    int updated = jdbcTemplate.update(
                            "UPDATE wf_scheduler_lease SET owner = ?, lease_until = ?, update_time = ? "
                                    + "WHERE lock_name = ? AND (owner = ? OR lease_until <= ?)",
                            owner,
                            now.plus(ttl),
                            now,
                            name,
                            owner,
                            now);
                    return updated == 1;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> release(String name, String owner) {
        return Mono.fromRunnable(() -> {
                    LocalDateTime now = LocalDateTime.now();
                    jdbcTemplate.update(
                            "UPDATE wf_scheduler_lease SET owner = '', lease_until = ?, update_time = ? "
                                    + "WHERE lock_name = ? AND owner = ?",
                            now,
                            now,
                            name,
                            owner);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }
}
