package com.apex.scaffold.application.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "运行时日志DTO，封装应用运行时产生的单条日志条目，用于日志查询与展示")
public class RuntimeLogDTO {

    /**
     * 时间戳
     */
    @Schema(description = "日志时间戳，格式 yyyy-MM-dd HH:mm:ss.SSS", example = "2026-09-26 10:00:00.123")
    private String timestamp;

    /**
     * 日志级别
     */
    @Schema(description = "日志级别：DEBUG/INFO/WARN/ERROR", example = "INFO")
    private String level;

    /**
     * 日志名称
     */
    @Schema(description = "日志名称（产生日志的记录器名称）", example = "com.apex.scaffold.application.service.UserService")
    private String logger;

    /**
     * 日志消息
     */
    @Schema(description = "日志消息内容", example = "用户登录成功")
    private String message;

    /**
     * 线程名
     */
    @Schema(description = "产生日志的线程名", example = "reactor-http-nio-3")
    private String thread;

    /**
     * 异常堆栈
     */
    @Schema(description = "异常堆栈信息，非异常日志为空", example = "java.lang.NullPointerException: id must not be null")
    private String exception;

    /**
     * 租户ID
     */
    @Schema(description = "租户ID", example = "1")
    private String tenantId;

    /**
     * 用户ID
     */
    @Schema(description = "操作用户ID", example = "1001")
    private String userId;

    /**
     * 请求ID
     */
    @Schema(description = "请求ID（链路追踪标识）", example = "a1b2c3d4e5f6")
    private String traceId;
}
