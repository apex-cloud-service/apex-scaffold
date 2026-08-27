package com.apex.scaffold.common.exception;

import com.apex.scaffold.common.util.TraceIdUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 告警事件类
 *
 * <p>用于封装告警相关信息，告警级别使用 {@link AlertLevel} 枚举定义，替代硬编码字符串。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvent {

    /**
     * 告警级别枚举
     *
     * <p>定义告警的严重程度，从低到高依次为 INFO、WARNING、ERROR、CRITICAL。
     *
     * @author Apex Team
     * @since 1.0.0
     */
    public enum AlertLevel {
        /** 信息级别 */
        INFO,
        /** 警告级别 */
        WARNING,
        /** 错误级别 */
        ERROR,
        /** 严重级别 */
        CRITICAL
    }

    /** 异常类型 */
    private String exceptionType;

    /** 异常消息 */
    private String message;

    /** 追踪 ID */
    private String traceId;

    /** 发生次数 */
    @Builder.Default
    private Integer count = 0;

    /** 服务名称 */
    private String serviceName;

    /** 告警级别 */
    @Builder.Default
    private AlertLevel level = AlertLevel.ERROR;

    /**
     * 构造告警事件
     *
     * @param ex 异常对象，不能为 null
     * @throws IllegalArgumentException 如果 ex 为 null
     */
    public AlertEvent(Exception ex) {
        if (ex == null) {
            throw new IllegalArgumentException("ex 不能为 null");
        }
        this.exceptionType = ex.getClass().getSimpleName();
        this.message = ex.getMessage();
        this.traceId = TraceIdUtil.getTraceId();
        // 兜底默认值与应用名 apex-scaffold 保持一致（Spring 环境下通常由调用方显式注入真实服务名）
        this.serviceName = System.getProperty("spring.application.name", "apex-scaffold");
        this.count = 1;
        this.level = ex instanceof BusinessException ? AlertLevel.WARNING : AlertLevel.ERROR;
    }
}
