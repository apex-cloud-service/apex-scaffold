package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 算力流水持久化对象。
 *
 * <p>映射数据库表 biz_credit_tx，记录算力积分的变动流水，包括方向、金额、原因和关联信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("biz_credit_tx")
public class CreditTxPO extends BasePO {

    /**
     * 创建算力流水持久化对象。
     */

    /** 用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 方向（IN/OUT）。 */
    @TableField("direction")
    private String direction;

    /** 变动数量。 */
    @TableField("amount")
    private Long amount;

    /** 变动原因。 */
    @TableField("reason")
    private String reason;

    /** 关联类型。 */
    @TableField("ref_type")
    private String refType;

    /** 关联ID。 */
    @TableField("ref_id")
    private Long refId;
}
