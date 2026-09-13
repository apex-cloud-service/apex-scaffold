package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 字典数据持久化对象。
 *
 * <p>对应数据库中的 {@code sys_dict_data} 表，存储字典数据项。
 * 通过 {@code dictType} 编码归属字典类型，提供标签/值映射能力。</p>
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
@TableName("sys_dict_data")
/**n * DictDataPO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class DictDataPO extends BasePO {

    /**
     * 构造字典数据持久化对象。
     */

    /**
     * 字典排序。
     */
    private Integer dictSort;

    /**
     * 字典标签。
     */
    private String dictLabel;

    /**
     * 字典键值。
     */
    private String dictValue;

    /**
     * 所属字典类型编码。
     */
    private String dictType;

    /**
     * 是否默认（0-否，1-是）。
     */
    private Integer isDefault;

    /**
     * 字典状态（0-停用，1-正常）。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}
