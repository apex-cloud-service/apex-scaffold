package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 参数配置持久化对象。
 *
 * <p>对应数据库中的 {@code sys_config} 表，存储系统级参数配置项。
 * 参数采用键值对模型，支持内置与用户自定义两种类型。</p>
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
@TableName("sys_config")
public class ConfigPO extends BasePO {

    /**
     * 构造参数配置持久化对象。
     */

    /**
     * 参数名称。
     */
    private String configName;

    /**
     * 参数键名。
     */
    private String configKey;

    /**
     * 参数键值。
     */
    private String configValue;

    /**
     * 参数类型（0-内置，1-用户自定义）。
     */
    private Integer configType;

    /**
     * 参数状态（0-停用，1-正常）。
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;
}
