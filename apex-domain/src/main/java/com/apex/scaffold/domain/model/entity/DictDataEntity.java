package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 字典数据实体。
 *
 * <p>表示一条具体的字典项，例如性别字典类型下的"男 / 女"。
 * 字典数据归属于某个字典类型（通过 {@code dictType} 编码关联），
 * 是下拉选项、枚举值翻译等场景的数据来源。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>字典数据通过 {@code dictType} 归属字典类型，同一类型内标签可重复、值建议唯一</li>
 *   <li>{@code isDefault} 标记默认选中项，同一类型内通常只有一个默认项</li>
 *   <li>字典数据可被启用或停用，停用后不在前端展示</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DictTypeEntity
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class DictDataEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 字典排序。
     *
     * <p>同一类型内数据项的排序值，数值越小越靠前。</p>
     */
    private Integer dictSort;

    /**
     * 字典标签。
     *
     * <p>数据项的显示文本，例如"男"。</p>
     */
    private String dictLabel;

    /**
     * 字典键值。
     *
     * <p>数据项的存储值，例如 {@code 1}，业务代码存储与匹配时使用该值。</p>
     */
    private String dictValue;

    /**
     * 所属字典类型编码。
     *
     * <p>关联 {@link DictTypeEntity#getDictType()}，例如 {@code sys_user_sex}。</p>
     */
    private String dictType;

    /**
     * 是否默认。
     *
     * <p>取值说明：0-否，1-是。默认项在未显式选择时自动选中。</p>
     */
    private Integer isDefault;

    /**
     * 字典状态。
     *
     * <p>取值说明：0-停用，1-正常。停用的数据项不在前端展示。</p>
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /** Sets dictSort. For persistence/construction use only. */
    public void setDictSort(Integer value) {
        this.dictSort = value;
    }

    /** Sets dictLabel. For persistence/construction use only. */
    public void setDictLabel(String value) {
        this.dictLabel = value;
    }

    /** Sets dictValue. For persistence/construction use only. */
    public void setDictValue(String value) {
        this.dictValue = value;
    }

    /** Sets dictType. For persistence/construction use only. */
    public void setDictType(String value) {
        this.dictType = value;
    }

    /** Sets isDefault. For persistence/construction use only. */
    public void setIsDefault(Integer value) {
        this.isDefault = value;
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
    public DictDataEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建字典数据。
     *
     * @param dictSort 字典排序
     * @param dictLabel 字典标签
     * @param dictValue 字典键值
     * @param dictType 所属字典类型编码
     * @return 新创建的字典数据实体，默认启用且非默认项
     */
    public static DictDataEntity create(Integer dictSort, String dictLabel, String dictValue, String dictType) {
        DictDataEntity entity = new DictDataEntity();
        entity.dictSort = dictSort == null ? 0 : dictSort;
        entity.dictLabel = dictLabel;
        entity.dictValue = dictValue;
        entity.dictType = dictType;
        entity.isDefault = DictDefault.NOT_DEFAULT.getCode();
        entity.status = DictState.ENABLED.getCode();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：更新字典数据核心信息。
     *
     * @param dictSort 字典排序
     * @param dictLabel 字典标签
     * @param dictValue 字典键值
     * @param remark 备注
     */
    public void updateInfo(Integer dictSort, String dictLabel, String dictValue, String remark) {
        this.dictSort = dictSort == null ? 0 : dictSort;
        this.dictLabel = dictLabel;
        this.dictValue = dictValue;
        this.remark = remark;
        touch();
    }

    /**
     * 领域行为：启用字典数据。
     */
    public void enable() {
        this.status = DictState.ENABLED.getCode();
        touch();
    }

    /**
     * 领域行为：停用字典数据。
     */
    public void disable() {
        this.status = DictState.DISABLED.getCode();
        touch();
    }

    /**
     * 领域行为：设为默认项。
     */
    public void markAsDefault() {
        this.isDefault = DictDefault.DEFAULT.getCode();
        touch();
    }

    /**
     * 领域行为：取消默认项标记。
     */
    public void unmarkDefault() {
        this.isDefault = DictDefault.NOT_DEFAULT.getCode();
        touch();
    }

    /**
     * 领域行为：检查是否为默认项。
     *
     * @return true-默认项，false-非默认项
     */
    public boolean isDefaultItem() {
        return isDefault != null && isDefault.equals(DictDefault.DEFAULT.getCode());
    }

    /**
     * 字典数据状态枚举。
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

    /**
     * 默认标记枚举。
     */
    public enum DictDefault {
        /** 非默认 */
        NOT_DEFAULT(0),
        /** 默认 */
        DEFAULT(1);

        private final Integer code;

        /**
         * 创建 {@code DictDefault} 实例。
         *
         * @param code 枚举编码
         */
        DictDefault(Integer code) {
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
