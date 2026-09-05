package com.apex.scaffold.domain.log;

import java.time.LocalDateTime;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 运行时日志查询仓储接口
 *
 * <p>定义从Grafana Loki查询应用运行时日志的能力。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface RuntimeLogRepository {

    /**
     * 查询运行时日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param level     日志级别（可选，如：ERROR、WARN、INFO）
     * @param logger    日志名称（可选，如：com.apex.scaffold）
     * @param message   消息关键词（可选）
     * @param limit     返回条数限制
     * @return 日志条目列表
     */
    Mono<List<RuntimeLogEntry>> queryLogs(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String level,
            String logger,
            String message,
            int limit
    );

    /**
     * 统计日志数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param level     日志级别（可选）
     * @return 日志数量
     */
    Mono<Long> countLogs(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String level
    );
}
