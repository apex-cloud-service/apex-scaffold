package com.apex.scaffold.file.domain.model.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 文件分享记录领域实体（限时只读分享）。
 *
 * <p>分享记录只保存令牌的 SHA-256 摘要，明文令牌仅在创建响应中返回一次，数据库、日志与
 * 后续兑换链路都不持有明文。分享兑换必须由服务端记录确定租户与文件，不能信任匿名请求
 * 携带的身份信息，也不放宽原文件访问规则。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 * @see FileEntity
 */
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FileShareEntity extends BaseEntity {

    /** 分享状态：有效。 */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 分享状态：已撤销。 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 被分享的文件编号。 */
    private Long fileId;

    /** 文件所属租户，兑换时作为服务端唯一租户依据。 */
    private String tenantId;

    /** 分享创建者（文件所有者）用户编号。 */
    private Long creatorId;

    /** 分享令牌的 SHA-256 十六进制摘要。 */
    private String tokenHash;

    /** 分享失效时间。 */
    private LocalDateTime expireAt;

    /** 分享状态（ACTIVE/REVOKED）。 */
    private String status;

    /**
     * 判断分享是否已过有效期。
     *
     * @param now 当前时间
     * @return 失效时间缺失或早于当前时间时返回 true
     */
    public boolean isExpired(LocalDateTime now) {
        return expireAt == null || expireAt.isBefore(now);
    }

    /**
     * 判断分享当前是否可兑换：状态有效且未过期。
     *
     * @param now 当前时间
     * @return 可兑换返回 true
     */
    public boolean isRedeemable(LocalDateTime now) {
        return STATUS_ACTIVE.equals(status) && !isExpired(now);
    }
}
