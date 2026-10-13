package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 积分流水领域实体
 *
 * <p>记录用户积分算力的每一次变动，包括增减方向、变动数量、变动原因和关联业务。
 * 作为积分账户的审计追踪和对账依据。</p>
 *
 * <h2>核心业务规则</h2>
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
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CreditTxEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建积分流水实体对象。 */
    public CreditTxEntity() {}

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

    /**
     * 设置用户ID（仅供持久层和构造器使用）。
     *
     * @param value 用户ID
     */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /**
     * 设置积分方向（仅供持久层和构造器使用）。
     *
     * @param value 积分方向（IN/OUT）
     */
    public void setDirection(String value) {
        this.direction = value;
    }

    /**
     * 设置变动数量（仅供持久层和构造器使用）。
     *
     * @param value 变动数量
     */
    public void setAmount(Long value) {
        this.amount = value;
    }

    /**
     * 设置变动原因（仅供持久层和构造器使用）。
     *
     * @param value 变动原因
     */
    public void setReason(String value) {
        this.reason = value;
    }

    /**
     * 设置关联类型（仅供持久层和构造器使用）。
     *
     * @param value 关联类型
     */
    public void setRefType(String value) {
        this.refType = value;
    }

    /**
     * 设置关联ID（仅供持久层和构造器使用）。
     *
     * @param value 关联ID
     */
    public void setRefId(Long value) {
        this.refId = value;
    }
}
