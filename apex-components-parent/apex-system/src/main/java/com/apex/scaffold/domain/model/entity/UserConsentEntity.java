package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserConsentEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

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
}
