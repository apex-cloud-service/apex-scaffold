package com.apex.scaffold.application.dto.log;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一业务日志 DTO
 *
 * @author Apex Team
 * @since 1.3.0
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
