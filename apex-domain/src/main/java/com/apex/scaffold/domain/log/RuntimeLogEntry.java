package com.apex.scaffold.domain.log;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运行时日志条目实体
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeLogEntry {

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 日志级别（ERROR、WARN、INFO、DEBUG）
     */
    private String level;

    /**
     * 日志名称（通常是类全名）
     */
    private String logger;

    /**
     * 日志消息
     */
    private String message;

    /**
     * 线程名
     */
    private String thread;

    /**
     * 异常堆栈（如果有）
     */
    private String exception;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 请求ID（链路追踪）
     */
    private String traceId;
}
