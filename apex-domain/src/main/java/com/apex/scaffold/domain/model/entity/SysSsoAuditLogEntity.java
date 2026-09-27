package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * SSO审计日志实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SysSsoAuditLogEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long providerId;
    private Long userId;
    private String externalId;
    private String operation;
    private String status;
    private String ipAddress;
    private String userAgent;
    private String errorMessage;
    private String requestData;
    private String tenantId;

    public SysSsoAuditLogEntity() {
        super();
    }

    /**
     * 设置 SSO 身份提供方 ID，并刷新实体更新时间。
     *
     * @param providerId 关联的 SSO 提供方配置主键 ID
     */
    public void setProviderId(Long providerId) {
        this.providerId = providerId;
        touch();
    }

    /**
     * 设置用户 ID，并刷新实体更新时间。
     *
     * @param userId 关联的本地用户 ID，用户未匹配时为空
     */
    public void setUserId(Long userId) {
        this.userId = userId;
        touch();
    }

    /**
     * 设置外部系统用户标识，并刷新实体更新时间。
     *
     * @param externalId 身份提供方侧的用户唯一标识
     */
    public void setExternalId(String externalId) {
        this.externalId = externalId;
        touch();
    }

    /**
     * 设置审计操作类型，并刷新实体更新时间。
     *
     * @param operation 操作类型标识（如登录、绑定、解绑）
     */
    public void setOperation(String operation) {
        this.operation = operation;
        touch();
    }

    /**
     * 设置操作结果状态，并刷新实体更新时间。
     *
     * @param status 操作的执行结果标识（成功或失败）
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    /**
     * 设置客户端 IP 地址，并刷新实体更新时间。
     *
     * @param ipAddress 发起该 SSO 操作的客户端 IP
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        touch();
    }

    /**
     * 设置客户端 User-Agent 信息，并刷新实体更新时间。
     *
     * @param userAgent 发起该 SSO 操作的浏览器 User-Agent 字符串
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        touch();
    }

    /**
     * 设置操作失败的错误信息，并刷新实体更新时间。
     *
     * @param errorMessage 操作异常的描述信息，成功时为空
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        touch();
    }

    /**
     * 设置请求数据，并刷新实体更新时间。
     *
     * @param requestData 该次 SSO 操作的请求数据（JSON 格式，敏感字段需脱敏）
     */
    public void setRequestData(String requestData) {
        this.requestData = requestData;
        touch();
    }

    /**
     * 设置租户 ID，并刷新实体更新时间。
     *
     * @param tenantId 审计日志所属租户的唯一标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
