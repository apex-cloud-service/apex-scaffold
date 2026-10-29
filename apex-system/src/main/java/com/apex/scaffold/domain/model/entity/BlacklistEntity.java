package com.apex.scaffold.domain.model.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 黑名单聚合根实体
 *
 * <p>
 * 表示用户之间的拉黑关系。
 * 被拉黑的用户之间无法互相发送消息、查看资料等。
 * </p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户不能拉黑自己</li>
 *   <li>拉黑关系是单向的（A拉黑B不代表B拉黑A）</li>
 *   <li>拉黑后可解除</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class BlacklistEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 拉黑者用户ID
     *
     * <p>
     * 执行拉黑操作的用户ID。
     * </p>
     */
    private Long userId;

    /**
     * 被拉黑用户ID
     *
     * <p>
     * 被拉黑的目标用户ID。
     * </p>
     */
    private Long targetUserId;

    /**
     * 是否有效标识
     *
     * <p>
     * 标识拉黑关系是否有效。
     * <ul>
     *   <li>0 - 无效（已解除拉黑）</li>
     *   <li>1 - 有效（正常拉黑中）</li>
     * </ul>
     * </p>
     *
     * @see #isEnabled()
     */
    private Integer enabled;

    /**
     * 静态工厂方法：创建拉黑记录
     *
     * @param userId 拉黑者用户ID
     * @param targetUserId 被拉黑用户ID
     * @return 新创建的黑名单实体
     */
    public static BlacklistEntity create(Long userId, Long targetUserId) {
        BlacklistEntity entity = new BlacklistEntity();
        entity.userId = userId;
        entity.targetUserId = targetUserId;
        entity.enabled = BlacklistStatus.ENABLED.getCode();
        entity.setTimestamps();
        return entity;
    }

    /**
     * 设置时间戳
     */
    private void setTimestamps() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        this.setCreateTime(now);
        this.setUpdateTime(now);
        this.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
    }

    /**
     * 领域行为：检查拉黑是否有效
     *
     * @return true-有效，false-无效（已解除）
     */
    public boolean isEnabled() {
        return enabled != null && enabled.equals(BlacklistStatus.ENABLED.getCode());
    }

    /**
     * 领域行为：解除拉黑
     *
     * <p>
     * 将拉黑标记为无效，解除双方限制。
     * </p>
     */
    public void unblock() {
        this.enabled = BlacklistStatus.DISABLED.getCode();
        touch();
    }

    /**
     * 领域行为：重新拉黑
     *
     * <p>
     * 将已解除的拉黑重新标记为有效。
     * </p>
     */
    public void reblock() {
        this.enabled = BlacklistStatus.ENABLED.getCode();
        touch();
    }

    /**
     * 黑名单状态枚举
     */
    @Getter
    @RequiredArgsConstructor
    private enum BlacklistStatus {
        /** 无效（已解除） */
        DISABLED(0),
        /** 有效 */
        ENABLED(1);

        private final Integer code;
    }
}
