package com.apex.scaffold.application.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一业务日志 DTO。
 *
 * <p>用于统一封装登录日志、操作日志、接口调用日志等业务日志信息，供日志查询与展示使用。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code id} - 日志 ID</li>
 *   <li>{@code logType} - 日志类型（login/operation/api）</li>
 *   <li>{@code timestamp} - 时间戳</li>
 *   <li>{@code username} - 用户名</li>
 *   <li>{@code module} - 模块名称</li>
 *   <li>{@code operation} - 操作描述</li>
 *   <li>{@code ipAddress} - IP 地址</li>
 *   <li>{@code browser} - 浏览器</li>
 *   <li>{@code operatingSystem} - 操作系统</li>
 *   <li>{@code duration} - 执行时长（毫秒）</li>
 *   <li>{@code status} - 状态（成功/失败）</li>
 *   <li>{@code message} - 消息或错误信息</li>
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
@Schema(description = "统一业务日志DTO，聚合登录日志、操作日志、接口调用日志等业务日志信息，用于日志查询与展示")
public class UnifiedBusinessLogDTO {

    /**
     * 日志ID
     */
    @Schema(description = "日志ID", example = "1001")
    private String id;

    /**
     * 日志类型（login/operation/api）
     */
    @Schema(description = "日志类型：login(登录)/operation(操作)/api(接口调用)", example = "login")
    private String logType;

    /**
     * 时间戳
     */
    @Schema(description = "日志时间戳", example = "2026-09-26T10:00:00")
    private LocalDateTime timestamp;

    /**
     * 用户名
     */
    @Schema(description = "操作用户名", example = "admin")
    private String username;

    /**
     * 模块名称
     */
    @Schema(description = "模块名称", example = "用户登录")
    private String module;

    /**
     * 操作描述
     */
    @Schema(description = "操作描述", example = "登录成功")
    private String operation;

    /**
     * IP地址
     */
    @Schema(description = "客户端IP地址", example = "192.168.1.100")
    private String ipAddress;

    /**
     * 浏览器
     */
    @Schema(description = "浏览器类型", example = "Chrome 126")
    private String browser;

    /**
     * 操作系统
     */
    @Schema(description = "操作系统类型", example = "macOS 14")
    private String operatingSystem;

    /**
     * 执行时长（毫秒）
     */
    @Schema(description = "执行时长（毫秒），仅接口调用日志有值", example = "230")
    private Long duration;

    /**
     * 状态（成功/失败）
     */
    @Schema(description = "执行状态：1-成功，0-失败", example = "1")
    private Integer status;

    /**
     * 消息或错误信息
     */
    @Schema(description = "消息或错误信息", example = "登录成功")
    private String message;
}
