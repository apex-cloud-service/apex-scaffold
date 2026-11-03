package com.apex.scaffold.gateway.log;

import com.apex.scaffold.common.util.id.SnowflakeIdGenerator;
import java.time.LocalDateTime;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 基于 R2DBC 的访问日志仓储实现.
 *
 * <p>将 {@link AccessLogRecord} 异步写入独立库 {@code apex_gateway.sys_gateway_access_log}，
 * 由 {@link AccessLogConfig} 注册，复用 Spring Boot 依据 {@code spring.r2dbc.*}
 * 自动配置的 {@link DatabaseClient}（非阻塞响应式）。表结构见迁移 V60。
 *
 * <p><b>主键</b>：调用方组装时可为 {@code null}，本实现落库前用
 * {@link SnowflakeIdGenerator} 统一生成雪花 ID，避免多网关实例自增冲突。
 *
 * <p><b>事务说明</b>：访问日志为单表 INSERT，天然原子，无需开启事务；
 * 落库失败由 {@link AccessLogService#save} 统一 {@code onErrorResume} 吞掉，
 * 不影响响应链路。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Repository
@lombok.RequiredArgsConstructor
public class R2dbcAccessLogRepository implements AccessLogRepository {

    /** 目标表名（迁移 V60 建表，独立库 apex_gateway） */
    private static final String TABLE_NAME = "sys_gateway_access_log";

    /** 雪花 ID 生成器（数据中心=1，机器=1） */
    private static final SnowflakeIdGenerator ID_GENERATOR = new SnowflakeIdGenerator(1L, 1L);

    /** R2DBC 响应式数据库客户端 */
    private final DatabaseClient databaseClient;

    @Override
    public Mono<Void> save(AccessLogRecord record) {
        String sql =
                """
                INSERT INTO %s
                    (id, trace_id, method, path, route_id, service_id, gray_tag,
                     status, cost_ms, client_ip, user_agent, request_headers,
                     user_id, tenant_id, client_id, create_time)
                VALUES
                    (:id, :traceId, :method, :path, :routeId, :serviceId, :grayTag,
                     :status, :costMs, :clientIp, :userAgent, :requestHeaders,
                     :userId, :tenantId, :clientId, :createTime)
                """
                        .formatted(TABLE_NAME);

        long id = record.id() != null ? record.id() : ID_GENERATOR.nextId();
        return databaseClient
                .sql(sql)
                .bind("id", id)
                .bind("traceId", nvl(record.traceId()))
                .bind("method", nvl(record.method()))
                .bind("path", nvl(record.path()))
                .bind("routeId", nvl(record.routeId()))
                .bind("serviceId", nvl(record.serviceId()))
                .bind("grayTag", nvl(record.grayTag()))
                .bind("status", record.status())
                .bind("costMs", record.costMs())
                .bind("clientIp", nvl(record.clientIp()))
                .bind("userAgent", nvl(record.userAgent()))
                .bind("requestHeaders", nvl(record.requestHeadersJson()))
                .bind("userId", nvl(record.userId()))
                .bind("tenantId", nvl(record.tenantId()))
                .bind("clientId", nvl(record.clientId()))
                .bind("createTime", record.createTime() != null ? record.createTime() : LocalDateTime.now())
                .fetch()
                .rowsUpdated()
                .then();
    }

    /**
     * 将可空的字符串字段转为数据库可绑定的非空值，规避部分驱动对无类型 null 绑定的限制.
     *
     * @param value 原始字段值，可为 {@code null}
     * @return 非空字符串或空串
     */
    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
