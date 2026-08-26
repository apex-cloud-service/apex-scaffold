package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
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

    /** Sets module. For persistence/construction use only. */
    public void setModule(String value) {
        this.module = value;
    }

    /** Sets operationType. For persistence/construction use only. */
    public void setOperationType(String value) {
        this.operationType = value;
    }

    /** Sets description. For persistence/construction use only. */
    public void setDescription(String value) {
        this.description = value;
    }

    /** Sets requestMethod. For persistence/construction use only. */
    public void setRequestMethod(String value) {
        this.requestMethod = value;
    }

    /** Sets requestUri. For persistence/construction use only. */
    public void setRequestUri(String value) {
        this.requestUri = value;
    }

    /** Sets requestParams. For persistence/construction use only. */
    public void setRequestParams(String value) {
        this.requestParams = value;
    }

    /** Sets responseResult. For persistence/construction use only. */
    public void setResponseResult(String value) {
        this.responseResult = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /** Sets errorMsg. For persistence/construction use only. */
    public void setErrorMsg(String value) {
        this.errorMsg = value;
    }

    /** Sets executionTime. For persistence/construction use only. */
    public void setExecutionTime(Long value) {
        this.executionTime = value;
    }

    /** Sets ip. For persistence/construction use only. */
    public void setIp(String value) {
        this.ip = value;
    }

    /** Sets location. For persistence/construction use only. */
    public void setLocation(String value) {
        this.location = value;
    }

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets userName. For persistence/construction use only. */
    public void setUserName(String value) {
        this.userName = value;
    }

    /** Sets operationTime. For persistence/construction use only. */
    public void setOperationTime(LocalDateTime value) {
        this.operationTime = value;
    }

    /** Sets deviceType. For persistence/construction use only. */
    public void setDeviceType(String value) {
        this.deviceType = value;
    }

    /** Sets browser. For persistence/construction use only. */
    public void setBrowser(String value) {
        this.browser = value;
    }

    /** Sets os. For persistence/construction use only. */
    public void setOs(String value) {
        this.os = value;
    }

    /** Sets traceId. For persistence/construction use only. */
    public void setTraceId(String value) {
        this.traceId = value;
    }
}
