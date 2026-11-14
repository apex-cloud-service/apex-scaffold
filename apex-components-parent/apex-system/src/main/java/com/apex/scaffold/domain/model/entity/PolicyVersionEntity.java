package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class PolicyVersionEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 协议/政策类型（USER_AGREEMENT/PRIVACY_POLICY）。 */
    private String policyType;

    /** 版本号。 */
    private String version;

    /** 生效日期。 */
    private LocalDateTime effectiveAt;

    /** 内容 URL。 */
    private String contentUrl;
}
