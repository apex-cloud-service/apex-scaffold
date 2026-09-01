package com.apex.scaffold.domain.model.record;

import java.time.LocalDateTime;

/**
 * 审计日志记录（领域层查询模型）。
 *
 * <p>操作审计日志（{@code sys_operation_log} 表）的领域查询载体，由基础设施层
 * 从 PO 转换而来，供应用层查询服务转换为展示 DTO，避免 PO 跨层暴露。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public class AuditLogRecord {

    /** 主键 ID */
    private Long id;

    /** 操作人 ID */
    private String operatorId;

    /** 操作人姓名 */
    private String operatorName;

    /** 操作人 IP */
    private String operatorIp;

    /** 操作模块 */
    private String module;

    /** 操作类型 */
    private String operationType;

    /** 操作描述 */
    private String description;

    /** 请求方法 */
    private String method;

    /** 请求 URL */
    private String requestUrl;

    /** 请求参数（JSON） */
    private String requestParams;

    /** 响应结果（JSON） */
    private String responseResult;

    /** 耗时（毫秒） */
    private Long duration;

    /** 状态（SUCCESS/FAILED） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** @return 主键 ID */
    public Long getId() {
        return id;
    }

    /** @param id 主键 ID */
    public void setId(Long id) {
        this.id = id;
    }

    /** @return 操作人 ID */
    public String getOperatorId() {
        return operatorId;
    }

    /** @param operatorId 操作人 ID */
    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    /** @return 操作人姓名 */
    public String getOperatorName() {
        return operatorName;
    }

    /** @param operatorName 操作人姓名 */
    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    /** @return 操作人 IP */
    public String getOperatorIp() {
        return operatorIp;
    }

    /** @param operatorIp 操作人 IP */
    public void setOperatorIp(String operatorIp) {
        this.operatorIp = operatorIp;
    }

    /** @return 操作模块 */
    public String getModule() {
        return module;
    }

    /** @param module 操作模块 */
    public void setModule(String module) {
        this.module = module;
    }

    /** @return 操作类型 */
    public String getOperationType() {
        return operationType;
    }

    /** @param operationType 操作类型 */
    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    /** @return 操作描述 */
    public String getDescription() {
        return description;
    }

    /** @param description 操作描述 */
    public void setDescription(String description) {
        this.description = description;
    }

    /** @return 请求方法 */
    public String getMethod() {
        return method;
    }

    /** @param method 请求方法 */
    public void setMethod(String method) {
        this.method = method;
    }

    /** @return 请求 URL */
    public String getRequestUrl() {
        return requestUrl;
    }

    /** @param requestUrl 请求 URL */
    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    /** @return 请求参数 */
    public String getRequestParams() {
        return requestParams;
    }

    /** @param requestParams 请求参数 */
    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams;
    }

    /** @return 响应结果 */
    public String getResponseResult() {
        return responseResult;
    }

    /** @param responseResult 响应结果 */
    public void setResponseResult(String responseResult) {
        this.responseResult = responseResult;
    }

    /** @return 耗时（毫秒） */
    public Long getDuration() {
        return duration;
    }

    /** @param duration 耗时（毫秒） */
    public void setDuration(Long duration) {
        this.duration = duration;
    }

    /** @return 状态 */
    public String getStatus() {
        return status;
    }

    /** @param status 状态 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** @return 创建时间 */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /** @param createTime 创建时间 */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
