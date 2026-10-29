package com.apex.scaffold.office.off.domain.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 消息渠道投递记录实体。
 *
 * <p>以稳定的消息标识关联站内消息与各渠道投递结果：站内已读属于接收人记录
 * （{@code off_message_recipient}），第三方送达/失败属于本投递记录，两者互不覆盖。
 * 平台无已读回执能力时状态保持 UNKNOWN，不得伪装为已送达。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Getter
@lombok.NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class OffMessageDeliveryEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 站内消息通道。 */
    public static final String CHANNEL_INBOX = "INBOX";
    /** 飞书通道。 */
    public static final String CHANNEL_FEISHU = "FEISHU";
    /** 钉钉通道。 */
    public static final String CHANNEL_DINGTALK = "DINGTALK";
    /** 企业微信通道。 */
    public static final String CHANNEL_WECOM = "WECOM";

    /** 等待投递。 */
    public static final String STATUS_PENDING = "PENDING";
    /** 投递成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 投递失败。 */
    public static final String STATUS_FAILED = "FAILED";
    /** 平台无回执能力，投递结果未知。 */
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    private Long messageId;
    private Long recipientId;
    private String channel;
    private String deliveryStatus;
    private String platformMessageId;
    private String failureReason;
    private LocalDateTime deliveredAt;
    private String tenantId;

    /**
     * 判断渠道标识是否受支持。
     *
     * @param channel 渠道标识
     * @return 受支持时返回 true
     */
    public static boolean isSupportedChannel(String channel) {
        return CHANNEL_INBOX.equals(channel)
                || CHANNEL_FEISHU.equals(channel)
                || CHANNEL_DINGTALK.equals(channel)
                || CHANNEL_WECOM.equals(channel);
    }

    /**
     * 判断投递状态是否受支持。
     *
     * @param status 投递状态
     * @return 受支持时返回 true
     */
    public static boolean isSupportedStatus(String status) {
        return STATUS_PENDING.equals(status)
                || STATUS_SUCCESS.equals(status)
                || STATUS_FAILED.equals(status)
                || STATUS_UNKNOWN.equals(status);
    }

    /**
     * 设置所属消息 ID，赋值后刷新更新时间。
     *
     * @param messageId 关联的办公消息 ID
     */
    public void setMessageId(Long messageId) {
        this.messageId = messageId;
        touch();
    }

    /**
     * 设置接收人 ID，赋值后刷新更新时间。
     *
     * @param recipientId 消息接收人用户 ID
     */
    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
        touch();
    }

    /**
     * 设置投递渠道，赋值后刷新更新时间。
     *
     * @param channel 渠道标识（INBOX/FEISHU/DINGTALK/WECOM）
     */
    public void setChannel(String channel) {
        this.channel = channel;
        touch();
    }

    /**
     * 设置投递状态，赋值后刷新更新时间。
     *
     * @param deliveryStatus 投递状态（PENDING/SUCCESS/FAILED/UNKNOWN）
     */
    public void setDeliveryStatus(String deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
        touch();
    }

    /**
     * 设置平台侧消息标识，赋值后刷新更新时间。
     *
     * @param platformMessageId 第三方平台返回的消息 ID，未知时为 null
     */
    public void setPlatformMessageId(String platformMessageId) {
        this.platformMessageId = platformMessageId;
        touch();
    }

    /**
     * 设置失败原因，赋值后刷新更新时间。
     *
     * @param failureReason 投递失败原因，成功或未知时为 null
     */
    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
        touch();
    }

    /**
     * 设置投递完成时间，赋值后刷新更新时间。
     *
     * @param deliveredAt 投递完成时间，未完结时为 null
     */
    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
        touch();
    }

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 租户标识，用于多租户数据隔离
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
