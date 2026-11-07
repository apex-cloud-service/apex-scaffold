package com.apex.scaffold.contract.dto.internal;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 租户认证期状态快照（内部 API 契约）.
 *
 * <p>由 system-service 的 {@code GET /internal/v1/tenants/{code}/status} 提供，供
 * auth-service 登录前置租户校验等消费方使用。未知租户以 {@code exists=false} 数据化返回
 * （HTTP 200），便于消费方缓存负结果；配额上限字段未配置时为 {@code null}。
 *
 * <p>消费方此前以手写 JSON 解析承载该契约，2026-11-07 模块评审（F3）后收敛为本 DTO，
 * 两端共用同一类型防漂移。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantStatusSnapshotDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户是否存在（未知租户 false，便于消费方缓存负结果） */
    private boolean exists;

    /** 租户状态机取值（如 ACTIVE/DISABLED） */
    private String status;

    /** 是否已过期 */
    private boolean expired;

    /** 用户数配额上限；未配置为 null */
    private Integer maxUsers;

    /** 存储配额上限（MB）；未配置为 null */
    private Integer maxStorageMb;
}
