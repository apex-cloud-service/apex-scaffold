package com.apex.scaffold.application.dto.log;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运行时日志 DTO。
 *
 * <p>用于封装应用运行时产生的日志条目，包含日志级别、记录器、消息、线程、异常堆栈以及租户/用户/请求追踪信息。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code timestamp} - 时间戳（字符串）</li>
 *   <li>{@code level} - 日志级别</li>
 *   <li>{@code logger} - 日志名称</li>
 *   <li>{@code message} - 日志消息</li>
 *   <li>{@code thread} - 线程名</li>
 *   <li>{@code exception} - 异常堆栈</li>
 *   <li>{@code tenantId} - 租户 ID</li>
 *   <li>{@code userId} - 用户 ID</li>
 *   <li>{@code traceId} - 请求 ID（链路追踪）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * RuntimeLogDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
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
