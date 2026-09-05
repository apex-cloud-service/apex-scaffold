package com.apex.scaffold.application.dto.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运行时日志 DTO
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeLogDTO {

    /**
     * 时间戳
     */
    private String timestamp;

    /**
     * 日志级别
     */
    private String level;

    /**
     * 日志名称
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
     * 异常堆栈
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
     * 请求ID
     */
    private String traceId;
}
