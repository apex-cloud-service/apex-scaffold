package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class DictTypePO extends BasePO {

    /**
     * 构造字典类型持久化对象。
     */
    public DictTypePO() {}

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
