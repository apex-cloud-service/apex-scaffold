package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 参数配置聚合根实体。
 *
 * <p>表示系统级参数配置项，例如系统名称、默认分页大小、上传大小限制等。
 * 参数采用键值对（{@code configKey} / {@code configValue}）模型，
 * 支持内置（系统预设，禁止删除）与用户自定义两种类型。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>参数键名全局唯一，用于代码中按键读取配置</li>
 *   <li>内置参数（{@code configType=0}）禁止删除，防止系统功能失效</li>
 *   <li>参数可被启用或停用，停用后按键读取时不可用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@lombok.Setter
@lombok.NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class ConfigEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 参数名称。
     *
     * <p>参数的显示名称，用于管理界面展示。</p>
     */
    private String configName;

    /**
     * 参数键名。
     *
     * <p>参数的唯一标识，例如 {@code sys.site.name}，代码通过该键读取配置值。</p>
     */
    private String configKey;

    /**
     * 参数键值。
     *
     * <p>参数的实际取值，以字符串形式存储。</p>
     */
    private String configValue;

    /**
     * 参数类型。
     *
     * <p>取值说明：0-内置（系统预设，禁止删除），1-用户自定义。</p>
     */
    private Integer configType;

    /**
     * 参数状态。
     *
     * <p>取值说明：0-停用，1-正常。</p>
     */
    private Integer status;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 静态工厂方法：创建参数配置。
     *
     * @param configName 参数名称
     * @param configKey 参数键名
     * @param configValue 参数键值
     * @return 新创建的参数配置实体，默认启用且为用户自定义类型
     */
    public static ConfigEntity create(String configName, String configKey, String configValue) {
        ConfigEntity entity = new ConfigEntity();
        entity.configName = configName;
        entity.configKey = configKey;
        entity.configValue = configValue;
        entity.configType = ConfigType.USER.getCode();
        entity.status = ConfigState.ENABLED.getCode();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：更新参数配置核心信息。
     *
     * @param configName 参数名称
     * @param configValue 参数键值
     * @param remark 备注
     */
    public void updateInfo(String configName, String configValue, String remark) {
        this.configName = configName;
        this.configValue = configValue;
        this.remark = remark;
        touch();
    }

    /**
     * 领域行为：启用参数配置。
     */
    public void enable() {
        this.status = ConfigState.ENABLED.getCode();
        touch();
    }

    /**
     * 领域行为：停用参数配置。
     */
    public void disable() {
        this.status = ConfigState.DISABLED.getCode();
        touch();
    }

    /**
     * 领域行为：检查参数配置是否已启用。
     *
     * @return true-已启用，false-已停用
     */
    public boolean isEnabled() {
        return status != null && status.equals(ConfigState.ENABLED.getCode());
    }

    /**
     * 领域行为：检查是否为内置参数。
     *
     * @return true-内置参数，false-用户自定义参数
     */
    public boolean isBuiltin() {
        return configType != null && configType.equals(ConfigType.BUILTIN.getCode());
    }

    /**
     * 参数类型枚举。
     */
    @Getter
    @lombok.RequiredArgsConstructor
    public enum ConfigType {
        /** 内置：系统预设，禁止删除 */
        BUILTIN(0),
        /** 用户自定义 */
        USER(1);

        private final Integer code;
    }

    /**
     * 参数状态枚举。
     */
    @Getter
    @lombok.RequiredArgsConstructor
    public enum ConfigState {
        /** 停用 */
        DISABLED(0),
        /** 正常 */
        ENABLED(1);

        private final Integer code;
    }
}
