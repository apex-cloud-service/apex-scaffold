package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 积分流水领域实体
 *
 * <p>记录用户积分算力的每一次变动，包括增减方向、变动数量、变动原因和关联业务。
 * 作为积分账户的审计追踪和对账依据。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>方向：IN（增加积分）/ OUT（消耗积分）</li>
 *   <li>关联类型和关联ID指向积分变动的业务来源</li>
 *   <li>积分流水不可修改，只能新增</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CreditTxEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户 ID。 */
    private Long userId;

    /** 方向（IN/OUT）。 */
    private String direction;

    /** 变动数量。 */
    private Long amount;

    /** 变动原因。 */
    private String reason;

    /** 关联类型。 */
    private String refType;

    /** 关联 ID。 */
    private Long refId;
}
