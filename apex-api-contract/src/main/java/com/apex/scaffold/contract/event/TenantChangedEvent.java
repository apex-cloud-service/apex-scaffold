package com.apex.scaffold.contract.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 租户主数据变更事件（跨服务契约，方案 D5）.
 *
 * <p>由 system 在租户创建、状态迁移或配额变更成功后经既有领域事件通道（Outbox + MQ）发布，
 * 用于通知各订阅方"某租户主数据已发生变化"。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>发布方：apex-system-service（{@code TenantApplicationService} 创建/状态迁移/配额更新）</li>
 *   <li>订阅方：需要本地缓存失效或主数据投影的业务服务（file、office 等）</li>
 *   <li>Topic：{@code apex-domain-event}（由 {@code apex.mq.domain-event-topic} 配置）</li>
 *   <li>负载以租户编码唯一标识被变更租户：平台管理操作中信封 {@code tenantId} 为发布者
 *       上下文租户（可能不是被变更租户），且编码是租户跨服务的唯一业务标识；
 *       负载不含配额数值、备注等配置内容</li>
 * </ul>
 *
 * <p>事件信封字段由 {@link AbstractDomainEvent} 基类提供（eventId、createdAt、traceId、tenantId 等），
 * 本类仅携带变更动作的业务负载。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 * @see AbstractDomainEvent
 */
@Getter
public class TenantChangedEvent extends AbstractDomainEvent {

    /** 动作：租户创建。 */
    public static final String ACTION_CREATED = "CREATED";

    /** 动作：租户更新（状态迁移、配额调整等）。 */
    public static final String ACTION_UPDATED = "UPDATED";

    /** 事件类型：租户主数据变更。 */
    private static final String EVENT_TYPE = "TENANT_CHANGED";

    /** 租户编码（全局唯一业务标识，订阅方据此回查租户主键）。 */
    private final String tenantCode;

    /** 变更动作：{@link #ACTION_CREATED} / {@link #ACTION_UPDATED}。 */
    private final String action;

    /**
     * 创建租户主数据变更事件。
     *
     * @param tenantCode 租户编码
     * @param action     变更动作
     */
    @JsonCreator
    public TenantChangedEvent(@JsonProperty("tenantCode") String tenantCode, @JsonProperty("action") String action) {
        super(EVENT_TYPE, null);
        this.tenantCode = tenantCode;
        this.action = action;
    }

    /**
     * 获取事件源 ID（聚合根 ID）。
     *
     * <p>返回租户编码：编码全局唯一，是租户跨服务的一致业务标识。
     *
     * @return 租户编码；编码为空时返回 {@code null}
     */
    @Override
    public String getSourceId() {
        return tenantCode;
    }
}
