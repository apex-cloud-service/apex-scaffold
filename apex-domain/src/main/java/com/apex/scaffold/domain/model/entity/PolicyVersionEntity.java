package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * PolicyVersion领域实体
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
public class PolicyVersionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建协议版本实体对象。 */
    public PolicyVersionEntity() {}

    /** 协议/政策类型（USER_AGREEMENT/PRIVACY_POLICY）。 */
    private String policyType;

    /** 版本号。 */
    private String version;

    /** 生效日期。 */
    private LocalDateTime effectiveAt;

    /** 内容 URL。 */
    private String contentUrl;

    /** Sets policyType. For persistence/construction use only. */
    public void setPolicyType(String value) {
        this.policyType = value;
    }

    /** Sets version. For persistence/construction use only. */
    public void setVersion(String value) {
        this.version = value;
    }

    /** Sets effectiveAt. For persistence/construction use only. */
    public void setEffectiveAt(LocalDateTime value) {
        this.effectiveAt = value;
    }

    /** Sets contentUrl. For persistence/construction use only. */
    public void setContentUrl(String value) {
        this.contentUrl = value;
    }
}
