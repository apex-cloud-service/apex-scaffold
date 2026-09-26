package com.apex.scaffold.application.dto.log;

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
public class UnifiedBusinessLogDTO {

    /**
     * 日志ID
     */
    private String id;

    /**
     * 日志类型（login/operation/api）
     */
    private String logType;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 用户名
     */
    private String username;

    /**
     * 模块名称
     */
    private String module;

    /**
     * 操作描述
     */
    private String operation;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 浏览器
     */
    private String browser;

    /**
     * 操作系统
     */
    private String operatingSystem;

    /**
     * 执行时长（毫秒）
     */
    private Long duration;

    /**
     * 状态（成功/失败）
     */
    private Integer status;

    /**
     * 消息或错误信息
     */
    private String message;
}
