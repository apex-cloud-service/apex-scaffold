package com.apex.scaffold.office.notify.domain;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 投递请求留痕实体（统一消息门面 / 批次 D1）。
 *
 * <p>对应 {@code off_notify_request} 表：门面每次按模板投递后按次落库，
 * 记录实际通道、模板编码、目标与渲染参数（JSON 文本）及投递结果。
 * 投递失败不抛出到调用链（由门面收敛为失败结果），但必须留痕以便排障与补偿。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Getter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class NotifyRequestRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 等待投递。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 投递成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 投递失败。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 判断投递状态是否受支持。 */
    public static boolean isSupportedStatus(String status) {
        return STATUS_PENDING.equals(status) || STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status);
    }

    private String tenantId;
    private String channel;
    private String templateCode;
    /** 接收目标（JSON 数组文本）。 */
    private String recipient;
    /** 渲染参数（JSON 对象文本）。 */
    private String params;

    private String status;
    private String errorMessage;
    /** 关联的 Outbox 事件 ID（事件驱动投递时回填，可空）。 */
    private Long outboxEventId;

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }

    /**
     * 设置实际投递通道，赋值后刷新更新时间。
     *
     * @param channel 通道标识
     */
    public void setChannel(String channel) {
        this.channel = channel;
        touch();
    }

    /**
     * 设置模板编码，赋值后刷新更新时间。
     *
     * @param templateCode 模板编码
     */
    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
        touch();
    }

    /**
     * 设置接收目标 JSON 文本，赋值后刷新更新时间。
     *
     * @param recipient 接收目标（JSON 数组字符串）
     */
    public void setRecipient(String recipient) {
        this.recipient = recipient;
        touch();
    }

    /**
     * 设置渲染参数 JSON 文本，赋值后刷新更新时间。
     *
     * @param params 渲染参数（JSON 对象字符串）
     */
    public void setParams(String params) {
        this.params = params;
        touch();
    }

    /**
     * 设置投递状态，赋值后刷新更新时间。
     *
     * @param status {@link #STATUS_PENDING} / {@link #STATUS_SUCCESS} / {@link #STATUS_FAILED}
     */
    public void setStatus(String status) {
        this.status = status;
        touch();
    }

    /**
     * 设置失败原因，赋值后刷新更新时间。
     *
     * @param errorMessage 失败原因，成功时为 null
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        touch();
    }

    /**
     * 设置关联的 Outbox 事件 ID，赋值后刷新更新时间。
     *
     * @param outboxEventId Outbox 事件 ID，可空
     */
    public void setOutboxEventId(Long outboxEventId) {
        this.outboxEventId = outboxEventId;
        touch();
    }
}
