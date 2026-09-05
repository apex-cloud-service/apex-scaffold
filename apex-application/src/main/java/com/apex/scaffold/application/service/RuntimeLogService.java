package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.log.RuntimeLogDTO;
import com.apex.scaffold.domain.log.RuntimeLogRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 运行时日志查询服务
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeLogService {

    private final RuntimeLogRepository runtimeLogRepository;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 查询运行时日志
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param level     日志级别
     * @param logger    日志名称
     * @param message   消息关键词
     * @param limit     返回条数
     * @return 日志列表
     */
    public Mono<List<RuntimeLogDTO>> queryLogs(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String level,
            String logger,
            String message,
            int limit) {
        
        log.debug("Query runtime logs: start={}, end={}, level={}, logger={}, message={}, limit={}",
                startTime, endTime, level, logger, message, limit);
        
        return runtimeLogRepository.queryLogs(startTime, endTime, level, logger, message, limit)
            .map(entries -> entries.stream()
                .map(entry -> RuntimeLogDTO.builder()
                    .timestamp(entry.getTimestamp().format(FORMATTER))
                    .level(entry.getLevel())
                    .logger(entry.getLogger())
                    .message(entry.getMessage())
                    .thread(entry.getThread())
                    .exception(entry.getException())
                    .tenantId(entry.getTenantId())
                    .userId(entry.getUserId())
                    .traceId(entry.getTraceId())
                    .build())
                .collect(Collectors.toList()));
    }

    /**
     * 统计日志数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param level     日志级别
     * @return 日志数量
     */
    public Mono<Long> countLogs(
            LocalDateTime startTime,
            LocalDateTime endTime,
            String level) {
        
        return runtimeLogRepository.countLogs(startTime, endTime, level);
    }
}
