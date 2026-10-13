package com.apex.scaffold.office.application.port;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * 办公模块用户快照（端口进站值对象）。
 *
 * <p>办公应用服务对系统域用户档案的只读依赖，在此以轻量值对象承载，替代对共享
 * 单体库 {@code apex-domain} 中 {@code com.apex.scaffold.domain.model.entity.UserEntity}
 * 的编译期引用，从而为 Phase 6 将该领域实体物理迁移至 {@code apex-system}
 * 扫清断裂点。
 *
 * <p>本快照仅暴露办公登录/审批所需的最小字段子集，由出站端口
 * {@link OfficeUserQueryPort} 的实现方（基础设施适配层）负责从系统服务内部
 * API 或共享领域实体映射而来。</p>
 *
 * <h2>与领域实体的一致性约束</h2>
 * <ul>
 *   <li>{@link #isEnabled()}：对应系统用户 {@code status == ENABLED(1)}</li>
 *   <li>{@link #getDeleted()}：逻辑删除标记（0-未删除，1-已删除）</li>
 *   <li>{@link #getTenantId()}：用户所属租户，用于跨租户校验</li>
 *   <li>{@link #getOpenId()}：办公平台 OpenID，作为办公登录的 JWT subject</li>
 * </ul>
 *
 * @see OfficeUserQueryPort
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Getter
@Builder
public class OfficeUserSnapshot {

    /** 用户 ID。 */
    private final Long id;

    /** 用户所属租户标识。 */
    private final String tenantId;

    /** 办公平台 OpenID。 */
    private final String openId;

    /** 办公平台 UnionID（可选）。 */
    private final String unionId;

    /** 用户昵称。 */
    private final String nickName;

    /** 用户头像 URL。 */
    private final String avatarUrl;

    /** 用户性别（0-未知，1-男，2-女）。 */
    private final Integer gender;

    /** 用户手机号（可选）。 */
    private final String phone;

    /** 生日，格式为 YYYY-MM-DD。 */
    private final LocalDate birthday;

    /** 国家。 */
    private final String country;

    /** 省份。 */
    private final String province;

    /** 城市。 */
    private final String city;

    /** 逻辑删除标记：0-未删除，1-已删除。 */
    private final Integer deleted;

    /** 账号状态：1-启用（ENABLED），0-禁用。 */
    private final Integer status;

    /**
     * 用户是否启用。
     *
     * @return true-账号启用（status == 1），false-禁用
     */
    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
