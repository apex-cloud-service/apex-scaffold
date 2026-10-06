package com.apex.scaffold.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * 用户社交身份绑定持久化对象（{@code sys_user_social}）。
 *
 * <p>对应多端微信身份绑定表：同一用户在微信开放平台 App / 小程序 / 公众号
 * 下的不同 OpenID 各占一行，以 {@code (channel, open_id)} 唯一约束保证
 * 渠道内身份唯一。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BasePO
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@TableName("sys_user_social")
public class UserSocialPO extends BasePO {

    /** 租户 ID（多租户隔离）。 */
    private String tenantId;

    /** 用户 ID（sys_user.id）。 */
    private Long userId;

    /** 社交渠道（WECHAT_APP/WECHAT_MINI/WECHAT_MP）。 */
    private String channel;

    /** 该渠道下的 OpenID。 */
    private String openId;

    /** 微信 UnionID。 */
    private String unionId;

    /** 状态（0-解绑，1-正常）。 */
    private Integer status;

    /** 绑定时间。 */
    private LocalDateTime boundAt;
}
