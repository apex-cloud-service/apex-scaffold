package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 操作日志持久化对象
 *
 * <p>领域实体
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OperationLogEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建操作日志实体对象。 */
    public OperationLogEntity() {}

    /** 操作模块。 */
    private String module;

    /** 操作类型。 */
    private String operationType;

    /** 操作描述。 */
    private String description;

    /** 请求方法。 */
    private String requestMethod;

    /** 请求 URI。 */
    private String requestUri;

    /** 请求参数。 */
    private String requestParams;

    /** 响应结果。 */
    private String responseResult;

    /** 操作状态（0-成功，1-失败）。 */
    private Integer status;

    /** 错误信息。 */
    private String errorMsg;

    /** 执行时间（毫秒）。 */
    private Long executionTime;

    /** 操作 IP。 */
    private String ip;

    /** 操作地点。 */
    private String location;

    /** 操作用户 ID。 */
    private Long userId;

    /** 操作用户名。 */
    private String userName;

    /** 操作时间。 */
    private LocalDateTime operationTime;

    /** 设备类型。 */
    private String deviceType;

    /** 浏览器类型。 */
    private String browser;

    /** 操作系统。 */
    private String os;

    /** 追踪 ID。 */
    private String traceId;
}
