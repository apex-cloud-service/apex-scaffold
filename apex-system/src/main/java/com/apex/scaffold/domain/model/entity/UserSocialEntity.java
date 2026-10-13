package com.apex.scaffold.domain.model.entity;

import com.apex.scaffold.domain.enums.SocialChannel;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 用户社交身份绑定聚合根实体。
 *
 * <p>记录用户在各社交渠道（微信开放平台 App / 小程序 / 公众号）下的渠道身份，
 * 解决同一用户在不同微信应用下 OpenID 不同而无法共存的模型缺陷：
 * 每个渠道的 OpenID 各占一条绑定记录，全部挂接到统一的 {@code sys_user} 账户。
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>{@code (channel, openId)} 渠道内唯一：同一渠道的同一微信身份只能绑定一个用户</li>
 *   <li>{@code unionId} 用于跨渠道自动归一：同一微信开放平台主体下各端 UnionID 相同，
 *       可据此把新渠道的 OpenID 挂到已有账户</li>
 *   <li>绑定记录不承载凭证信息（微信 access_token 等仍保留在 sys_user），只表达身份归属</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 * @see com.apex.scaffold.domain.enums.SocialChannel
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class UserSocialEntity extends BaseEntity {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID（sys_user.id）。
     */
    private Long userId;

    /**
     * 租户 ID（多租户隔离）。
     */
    private String tenantId;

    /**
     * 社交渠道。
     */
    private SocialChannel channel;

    /**
     * 该渠道下的 OpenID。
     */
    private String openId;

    /**
     * 微信 UnionID，跨渠道归一依据。
     */
    private String unionId;

    /**
     * 绑定状态。
     *
     * <p>取值说明：0-解绑，1-正常。</p>
     */
    private Integer status;

    /**
     * 绑定时间。
     */
    private LocalDateTime boundAt;

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets tenantId. For persistence/construction use only. */
    public void setTenantId(String value) {
        this.tenantId = value;
    }

    /** Sets channel. For persistence/construction use only. */
    public void setChannel(SocialChannel value) {
        this.channel = value;
    }

    /** Sets openId. For persistence/construction use only. */
    public void setOpenId(String value) {
        this.openId = value;
    }

    /** Sets unionId. For persistence/construction use only. */
    public void setUnionId(String value) {
        this.unionId = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(Integer value) {
        this.status = value;
    }

    /** Sets boundAt. For persistence/construction use only. */
    public void setBoundAt(LocalDateTime value) {
        this.boundAt = value;
    }

    /**
     * 静态工厂方法：创建绑定记录。
     *
     * @param userId   用户 ID（sys_user.id）
     * @param tenantId 租户 ID，可为 {@code null}
     * @param channel  社交渠道
     * @param openId   该渠道下的 OpenID
     * @param unionId  微信 UnionID，可为 {@code null}
     * @return 新创建的绑定实体
     */
    public static UserSocialEntity bind(
            Long userId, String tenantId, SocialChannel channel, String openId, String unionId) {
        UserSocialEntity entity = new UserSocialEntity();
        entity.userId = userId;
        entity.tenantId = tenantId;
        entity.channel = channel;
        entity.openId = openId;
        entity.unionId = unionId;
        entity.status = BindingStatus.BOUND.getCode();
        entity.boundAt = LocalDateTime.now();
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：同步渠道 UnionID。
     *
     * <p>微信授权返回的 UnionID 与已有绑定不一致时以最新授权为准，
     * 一致或入参为空时不产生变更。</p>
     *
     * @param unionId 微信授权返回的 UnionID
     */
    public void syncUnionId(String unionId) {
        if (unionId != null && !unionId.isBlank() && !unionId.equals(this.unionId)) {
            this.unionId = unionId;
            touch();
        }
    }

    /**
     * 领域行为：检查绑定是否处于正常状态。
     *
     * @return true-正常，false-已解绑或状态未知
     */
    public boolean isBound() {
        return status != null && status.equals(BindingStatus.BOUND.getCode());
    }

    /**
     * 绑定状态枚举。
     */
    public enum BindingStatus {
        /** 解绑 */
        UNBOUND(0),
        /** 正常 */
        BOUND(1);

        private final Integer code;

        /**
         * 创建 {@code BindingStatus} 实例。
         *
         * @param code 枚举编码
         */
        BindingStatus(Integer code) {
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
