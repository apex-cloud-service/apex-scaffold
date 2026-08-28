package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 操作审计日志 DTO。
 *
 * <p>管理后台审计日志查询的展示模型，字段与 {@code sys_operation_log} 表对齐
 * （由基础设施层 PO 转换而来，避免 PO 跨层暴露）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Schema(description = "操作审计日志")
public class AuditLogDTO {

    /** 主键 ID */
    @Schema(description = "主键ID")
    private Long id;

    /** 操作人 ID */
    @Schema(description = "操作人ID")
    private String operatorId;

    /** 操作人姓名 */
    @Schema(description = "操作人姓名")
    private String operatorName;

    /** 操作人 IP */
    @Schema(description = "操作人IP")
    private String operatorIp;

    /** 操作模块 */
    @Schema(description = "操作模块")
    private String module;

    /** 操作类型 */
    @Schema(description = "操作类型")
    private String operationType;

    /** 操作描述 */
    @Schema(description = "操作描述")
    private String description;

    /** 请求方法 */
    @Schema(description = "请求方法")
    private String method;

    /** 请求 URL */
    @Schema(description = "请求URL")
    private String requestUrl;

    /** 请求参数（JSON） */
    @Schema(description = "请求参数（JSON）")
    private String requestParams;

    /** 响应结果（JSON） */
    @Schema(description = "响应结果（JSON）")
    private String responseResult;

    /** 耗时（毫秒） */
    @Schema(description = "耗时（毫秒）")
    private Long duration;

    /** 状态（SUCCESS/FAILED） */
    @Schema(description = "状态（SUCCESS/FAILED）")
    private String status;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /** 无参构造（JSON 反序列化） */
    public AuditLogDTO() {}

    /**
     * 全字段构造。
     *
     * @param id             主键 ID
     * @param operatorId     操作人 ID
     * @param operatorName   操作人姓名
     * @param operatorIp     操作人 IP
     * @param module         操作模块
     * @param operationType  操作类型
     * @param description    操作描述
     * @param method         请求方法
     * @param requestUrl     请求 URL
     * @param requestParams  请求参数
     * @param responseResult 响应结果
     * @param duration       耗时（毫秒）
     * @param status         状态
     * @param createTime     创建时间
     */
    public AuditLogDTO(
            Long id,
            String operatorId,
            String operatorName,
            String operatorIp,
            String module,
            String operationType,
            String description,
            String method,
            String requestUrl,
            String requestParams,
            String responseResult,
            Long duration,
            String status,
            LocalDateTime createTime) {
        this.id = id;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.operatorIp = operatorIp;
        this.module = module;
        this.operationType = operationType;
        this.description = description;
        this.method = method;
        this.requestUrl = requestUrl;
        this.requestParams = requestParams;
        this.responseResult = responseResult;
        this.duration = duration;
        this.status = status;
        this.createTime = createTime;
    }

    /** @return 主键 ID */
    public Long getId() {
        return id;
    }

    /** @return 操作人 ID */
    public String getOperatorId() {
        return operatorId;
    }

    /** @return 操作人姓名 */
    public String getOperatorName() {
        return operatorName;
    }

    /** @return 操作人 IP */
    public String getOperatorIp() {
        return operatorIp;
    }

    /** @return 操作模块 */
    public String getModule() {
        return module;
    }

    /** @return 操作类型 */
    public String getOperationType() {
        return operationType;
    }

    /** @return 操作描述 */
    public String getDescription() {
        return description;
    }

    /** @return 请求方法 */
    public String getMethod() {
        return method;
    }

    /** @return 请求 URL */
    public String getRequestUrl() {
        return requestUrl;
    }

    /** @return 请求参数 */
    public String getRequestParams() {
        return requestParams;
    }

    /** @return 响应结果 */
    public String getResponseResult() {
        return responseResult;
    }

    /** @return 耗时（毫秒） */
    public Long getDuration() {
        return duration;
    }

    /** @return 状态 */
    public String getStatus() {
        return status;
    }

    /** @return 创建时间 */
    public LocalDateTime getCreateTime() {
        return createTime;
    }
}
