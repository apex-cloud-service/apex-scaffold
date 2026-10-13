package com.apex.scaffold.office.domain;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * 办公平台组织映射聚合根
 *
 * <p>表示外部办公平台组织架构与 Apex 内部组织架构之间的映射关系。
 * 用于同步组织结构、成员信息，以及在 Apex 系统中关联外部平台的部门和用户。
 *
 * <p>映射类型：
 * <ul>
 *   <li>部门映射：externalOrgId → apexDeptId</li>
 *   <li>成员映射：externalUserId → apexUserId</li>
 * </ul>
 *
 * <p>设计要点：
 * <ul>
 *   <li>支持多租户，每个租户维护独立的映射关系</li>
 *   <li>同一外部组织/用户在不同租户中可映射到不同的 Apex 实体</li>
 *   <li>映射失效时不自动删除，通过逻辑删除保留历史记录</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Value
@Builder
public class OfficeOrganizationMapping {
    /** 主键 ID */
    Long id;

    /** 租户标识 */
    String tenantId;

    /** 平台类型 */
    OfficePlatform platform;

    /** 外部组织标识（部门 ID） */
    String externalOrgId;

    /** 外部用户标识 */
    String externalUserId;

    /** Apex 部门标识 */
    String apexDeptId;

    /** Apex 用户标识 */
    String apexUserId;

    /** 乐观锁版本号 */
    Integer version;

    /** 逻辑删除标记（0-未删除，1-已删除） */
    Integer deleted;

    /** 创建时间 */
    LocalDateTime createdAt;

    /** 更新时间 */
    LocalDateTime updatedAt;
}
