package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 字典类型聚合根实体。
 *
 * <p>表示一类字典数据的类型定义，例如性别（{@code sys_user_sex}）、
 * 任务状态等。字典类型编码全局唯一，是该类型下所有字典数据的
 * 归属标识。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>字典类型编码（{@code dictType}）全局唯一，用于代码中引用字典</li>
 *   <li>字典类型可被启用或停用，停用后其数据项不可用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DictDataEntity
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DictTypeEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 字典名称。
     *
     * <p>字典类型的显示名称，用于管理界面展示。</p>
     */
    private String dictName;

    /**
     * 字典类型编码。
     *
     * <p>字典类型的系统标识，全局唯一，例如 {@code sys_user_sex}。
     * 编码使用小写下划线格式，业务代码通过该编码读取字典数据。</p>
     */
    private String dictType;

    /**
     * 字典状态。
     *
     * <p>取值说明：0-停用，1-正常。停用的字典类型不可用。</p>
     */
    private Integer status;

    /**
     * 备注。
     *
     * <p>字典类型的补充说明。</p>
     */
    private String remark;

    /** Sets dictName. For persistence/construction use only. */
    public void setDictName(String value) {
        this.dictName = value;
    }

    /** Sets dictType. For persistence/construction use only. */
    public void setDictType(String value) {
        this.dictType = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /** Sets remark. For persistence/construction use only. */
    public void setRemark(String value) {
        this.remark = value;
    }

    /**
     * 默认构造函数。
     */
    public DictTypeEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建字典类型。
     *
     * @param dictName 字典名称
     * @param dictType 字典类型编码
     * @param remark 备注
     * @return 新创建的字典类型实体，默认启用
     */
    public static DictTypeEntity create(String dictName, String dictType, String remark) {
        DictTypeEntity entity = new DictTypeEntity();
        entity.dictName = dictName;
        entity.dictType = dictType;
        entity.remark = remark;
        entity.status = DictState.ENABLED.getCode();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：更新字典类型核心信息。
     *
     * @param dictName 字典名称
     * @param remark 备注
     */
    public void updateInfo(String dictName, String remark) {
        this.dictName = dictName;
        this.remark = remark;
        touch();
    }

    /**
     * 领域行为：启用字典类型。
     */
    public void enable() {
        this.status = DictState.ENABLED.getCode();
        touch();
    }

    /**
     * 领域行为：停用字典类型。
     */
    public void disable() {
        this.status = DictState.DISABLED.getCode();
        touch();
    }

    /**
     * 领域行为：检查字典类型是否已启用。
     *
     * @return true-已启用，false-已停用
     */
    public boolean isEnabled() {
        return status != null && status.equals(DictState.ENABLED.getCode());
    }

    /**
     * 字典状态枚举。
     */
    public enum DictState {
        /** 停用 */
        DISABLED(0),
        /** 正常 */
        ENABLED(1);

        private final Integer code;

        /**
         * 创建 {@code DictState} 实例。
         *
         * @param code 枚举编码
         */
        DictState(Integer code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         *
         * @return 枚举编码
         */
        public Integer getCode() {
            return code;
        }
    }
}
