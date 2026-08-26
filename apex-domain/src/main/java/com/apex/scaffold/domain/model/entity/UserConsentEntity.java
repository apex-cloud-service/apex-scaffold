package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * UserConsent领域实体
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
public class UserConsentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建用户同意记录实体对象。 */
    public UserConsentEntity() {}

    /** 用户 ID。 */
    private Long userId;

    /** 策略类型。 */
    private String policyType;

    /** 同意版本。 */
    private String version;

    /** 是否同意（0/1）。 */
    private Integer agreed;

    /** 同意时间。 */
    private LocalDateTime agreedAt;

    /** 渠道。 */
    private String channel;

    /** 设备 ID 摘要。 */
    private String deviceId;

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets policyType. For persistence/construction use only. */
    public void setPolicyType(String value) {
        this.policyType = value;
    }

    /** Sets version. For persistence/construction use only. */
    public void setVersion(String value) {
        this.version = value;
    }

    /** Sets agreed. For persistence/construction use only. */
    public void setAgreed(Integer value) {
        this.agreed = value;
    }

    /** Sets agreedAt. For persistence/construction use only. */
    public void setAgreedAt(LocalDateTime value) {
        this.agreedAt = value;
    }

    /** Sets channel. For persistence/construction use only. */
    public void setChannel(String value) {
        this.channel = value;
    }

    /** Sets deviceId. For persistence/construction use only. */
    public void setDeviceId(String value) {
        this.deviceId = value;
    }
}
