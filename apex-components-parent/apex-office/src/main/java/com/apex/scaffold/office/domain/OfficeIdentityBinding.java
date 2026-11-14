package com.apex.scaffold.office.domain;

import lombok.Builder;
import lombok.Value;

/**
 * 办公平台身份绑定聚合根
 *
 * <p>表示 Apex 用户与外部办公平台用户之间的身份绑定关系。
 * 绑定需要管理员审批，防止未经授权的自动关联和跨租户数据访问。
 *
 * <p>状态流转：
 * <ul>
 *   <li>PENDING：待审批（用户发起绑定请求后的初始状态）</li>
 *   <li>APPROVED：已批准（管理员审批通过，可用于 OAuth 登录）</li>
 *   <li>REJECTED：已拒绝（管理员拒绝绑定）</li>
 *   <li>REVOKED：已撤销（管理员主动解绑或用户删除）</li>
 * </ul>
 *
 * <p>安全约束：
 * <ul>
 *   <li>只有 APPROVED 状态的绑定才能用于 OAuth 登录</li>
 *   <li>绑定的 userId 必须属于同一 tenantId</li>
 *   <li>同一用户在同一平台只能有一个有效绑定</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Value
@Builder
public class OfficeIdentityBinding {
    /** 主键 ID */
    Long id;

    /** 租户标识 */
    String tenantId;

    /** Apex 用户 ID */
    Long userId;

    /** 平台类型 */
    OfficePlatform platform;

    /** 外部平台用户标识（OpenId/UnionId/UserId 等） */
    String externalUserId;

    /** 审批状态（PENDING/APPROVED/REJECTED/REVOKED） */
    String status;

    /** 是否启用 */
    boolean enabled;

    /** 乐观锁版本号 */
    Integer version;

    /** 逻辑删除标记（0-未删除，1-已删除） */
    Integer deleted;
}
