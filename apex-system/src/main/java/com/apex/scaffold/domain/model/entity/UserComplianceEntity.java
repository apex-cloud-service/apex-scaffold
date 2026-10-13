package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 用户合规配置聚合根实体
 *
 * <p>
 * 存储用户的合规相关配置信息，包括青少年模式等安全设置。
 * 这些设置影响内容的可见性和交互权限。
 * </p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>青少年模式开启后将屏蔽成人内容</li>
 *   <li>合规设置变更需记录时间戳用于审计</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserComplianceEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     *
     * <p>
     * 关联的{@link UserEntity}的主键ID。
     * </p>
     */
    private Long userId;

    /**
     * 青少年模式标识
     *
     * <p>
     * 标识用户是否开启了青少年模式。
     * <ul>
     *   <li>0 - 未开启（默认）</li>
     *   <li>1 - 已开启</li>
     * </ul>
     * </p>
     *
     * @see #isTeenModeEnabled()
     */
    private Integer teenMode;

    /**
     * 青少年模式设置时间
     *
     * <p>
     * 记录用户开启或关闭青少年模式的时间戳。
     * 用于合规审计和统计。
     * </p>
     */
    private LocalDateTime teenModeSetAt;

    /**
     * 影子封禁阶段
     *
     * <p>
     * 记录用户的影子封禁状态，用于反作弊系统对作弊用户进行分级处理。
     * <ul>
     *   <li>NONE - 未被封禁（默认）</li>
     *   <li>STAGE_1 - 第一阶段封禁（轻度限制）</li>
     *   <li>STAGE_2 - 第二阶段封禁（中度限制）</li>
     *   <li>STAGE_3 - 第三阶段封禁（重度限制/账号冻结）</li>
     * </ul>
     * </p>
     *
     * @see ShadowBanStage
     */
    private String shadowBanStage;

    /**
     * 影子封禁时间
     *
     * <p>
     * 记录用户进入影子封禁状态的时间戳。
     * 用于封禁时长计算和自动解封。
     * </p>
     */
    private LocalDateTime shadowBanAt;

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets teenMode. For persistence/construction use only. */
    public void setTeenMode(Integer value) {
        this.teenMode = value;
    }

    /** Sets teenModeSetAt. For persistence/construction use only. */
    public void setTeenModeSetAt(LocalDateTime value) {
        this.teenModeSetAt = value;
    }

    /** Sets shadowBanStage. For persistence/construction use only. */
    public void setShadowBanStage(String value) {
        this.shadowBanStage = value;
    }

    /** Sets shadowBanAt. For persistence/construction use only. */
    public void setShadowBanAt(LocalDateTime value) {
        this.shadowBanAt = value;
    }

    /**
     * 默认构造函数
     */
    public UserComplianceEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建新的合规配置
     *
     * @param userId 用户ID
     * @return 新创建的用户合规配置实体
     */
    public static UserComplianceEntity create(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        UserComplianceEntity entity = new UserComplianceEntity();
        entity.setUserId(userId);
        entity.setTeenMode(TeenModeStatus.DISABLED.getCode());
        entity.setShadowBanStage(ShadowBanStage.NONE.getCode());
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
        return entity;
    }

    /**
     * 领域行为：检查青少年模式是否开启
     *
     * @return true-已开启，false-未开启
     */
    public boolean isTeenModeEnabled() {
        return teenMode != null && teenMode.equals(TeenModeStatus.ENABLED.getCode());
    }

    /**
     * 领域行为：开启青少年模式
     *
     * <p>
     * 开启青少年模式并记录设置时间。
     * 开启后将屏蔽成人内容。
     * </p>
     */
    public void enableTeenMode() {
        this.teenMode = TeenModeStatus.ENABLED.getCode();
        this.teenModeSetAt = LocalDateTime.now();
        touch();
    }

    /**
     * 领域行为：关闭青少年模式
     *
     * <p>
     * 关闭青少年模式并记录设置时间。
     * </p>
     */
    public void disableTeenMode() {
        this.teenMode = TeenModeStatus.DISABLED.getCode();
        this.teenModeSetAt = LocalDateTime.now();
        touch();
    }

    /**
     * 领域行为：检查用户是否处于影子封禁状态
     *
     * @return true-处于封禁状态，false-未被封禁
     */
    public boolean isShadowBanned() {
        return shadowBanStage != null && !ShadowBanStage.NONE.getCode().equals(shadowBanStage);
    }

    /**
     * 领域行为：升级影子封禁阶段
     *
     * <p>
     * 将用户的影子封禁阶段升级到下一级。
     * 封禁阶段依次为：NONE -> STAGE_1 -> STAGE_2 -> STAGE_3
     * </p>
     */
    public void escalateShadowBan() {
        ShadowBanStage current = ShadowBanStage.fromCode(this.shadowBanStage);
        ShadowBanStage next = current != null ? current.next() : ShadowBanStage.STAGE_1;
        this.shadowBanStage = next.getCode();
        this.shadowBanAt = LocalDateTime.now();
        touch();
    }

    /**
     * 领域行为：解除影子封禁
     *
     * <p>
     * 将用户的影子封禁状态恢复为 NONE。
     * </p>
     */
    public void liftShadowBan() {
        this.shadowBanStage = ShadowBanStage.NONE.getCode();
        this.shadowBanAt = null;
        touch();
    }

    /**
     * 青少年模式状态枚举
     */
    public enum TeenModeStatus {
        /** 未开启 */
        DISABLED(0),
        /** 已开启 */
        ENABLED(1);

        private final Integer code;

        /**
         * 创建 {@code TeenModeStatus} 实例。
         *
         * @param code 枚举编码
         */
        TeenModeStatus(Integer code) {
            this.code = code;
        }

        /**
         * 获取枚举编码。
         * @return 枚举编码
         */
        public Integer getCode() {
            return code;
        }

        /**
         * 根据枚举编码解析青少年模式状态。
         *
         * @param code 青少年模式状态编码
         * @return 匹配的青少年模式状态；编码为空或未匹配时返回 {@code null}
         */
        public static TeenModeStatus fromCode(Integer code) {
            if (code == null) {
                return null;
            }
            for (TeenModeStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * 影子封禁阶段枚举
     *
     * <p>定义影子封禁的四个等级，用于反作弊系统对作弊用户进行分级处理：
     * <ul>
     *   <li>{@link #NONE} - 未被封禁（默认状态）</li>
     *   <li>{@link #STAGE_1} - 第一阶段封禁（轻度限制，如减少匹配机会）</li>
     *   <li>{@link #STAGE_2} - 第二阶段封禁（中度限制，如限制投票频率）</li>
     *   <li>{@link #STAGE_3} - 第三阶段封禁（重度限制，账号冻结）</li>
     * </ul>
     * </p>
     *
     * @author Apex Team
     */
    public enum ShadowBanStage {
        /** 未被封禁（默认状态） */
        NONE("NONE"),
        /** 第一阶段封禁（轻度限制） */
        STAGE_1("STAGE_1"),
        /** 第二阶段封禁（中度限制） */
        STAGE_2("STAGE_2"),
        /** 第三阶段封禁（重度限制/账号冻结） */
        STAGE_3("STAGE_3");

        private final String code;

        /**
         * 创建 {@code ShadowBanStage} 实例。
         *
         * @param code 枚举编码
         */
        ShadowBanStage(String code) {
            this.code = code;
        }

        /**
         * 获取阶段编码
         *
         * @return 阶段编码字符串
         */
        public String getCode() {
            return code;
        }

        /**
         * 根据编码获取对应的影子封禁阶段
         *
         * @param code 阶段编码
         * @return 对应的枚举值，若编码无效返回 {@link #NONE}
         */
        public static ShadowBanStage fromCode(String code) {
            if (code == null) {
                return NONE;
            }
            for (ShadowBanStage stage : values()) {
                if (stage.code.equals(code)) {
                    return stage;
                }
            }
            return NONE;
        }

        /**
         * 获取下一级封禁阶段
         *
         * @return 下一级阶段，若已是最高级则返回当前阶段
         */
        public ShadowBanStage next() {
            switch (this) {
                case NONE:
                    return STAGE_1;
                case STAGE_1:
                    return STAGE_2;
                case STAGE_2:
                    return STAGE_3;
                case STAGE_3:
                    return STAGE_3;
                default:
                    return STAGE_1;
            }
        }
    }
}
