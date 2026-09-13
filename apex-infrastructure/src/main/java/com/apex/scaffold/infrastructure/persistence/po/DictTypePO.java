package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典类型持久化对象。
 *
 * <p>对应数据库中的 {@code sys_dict_type} 表，存储字典类型定义。
 * 字典类型编码全局唯一，是字典数据归属的标识。</p>
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
@TableName("sys_dict_type")
/**n * DictTypePO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DictTypePO extends BasePO {

    /**
     * 构造字典类型持久化对象。
     */

    /**
     * 字典名称。
     */
    private String dictName;

    /**
     * 字典类型编码。
     */
    private String dictType;

    /**
     * 字典状态（0-停用，1-正常）。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}
