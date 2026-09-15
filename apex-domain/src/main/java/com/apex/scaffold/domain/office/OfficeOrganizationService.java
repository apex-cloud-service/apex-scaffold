package com.apex.scaffold.domain.office;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 办公平台组织服务领域端口
 *
 * <p>定义组织通讯录同步的核心能力，由基础设施层实现。
 * 用于从外部办公平台拉取部门和成员信息。
 *
 * <p>SPI 接口设计要点：
 * <ul>
 *   <li>声明式接口，不包含实现逻辑</li>
 *   <li>返回响应式 Mono，支持 WebFlux 非阻塞调用</li>
 *   <li>每个平台的实现类注册为 Spring Bean</li>
 *   <li>支持增量同步（通过 cursor）和全量同步</li>
 * </ul>
 *
 * <p>实现类需要：
 * <ul>
 *   <li>处理平台 API 的分页逻辑</li>
 *   <li>处理 Token 刷新和重试</li>
 *   <li>统一异常处理，转换为 BusinessException</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface OfficeOrganizationService {

    /**
     * 拉取部门列表
     *
     * <p>从外部办公平台拉取部门信息，支持增量同步。
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param cursor   同步游标（用于增量同步，首次传 null）
     * @return 部门列表和新游标
     */
    Mono<OrganizationSyncResult<OfficeDepartment>> fetchDepartments(
        String tenantId,
        OfficePlatform platform,
        String cursor
    );

    /**
     * 拉取成员列表
     *
     * <p>从外部办公平台拉取成员信息，支持增量同步。
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param cursor   同步游标（用于增量同步，首次传 null）
     * @return 成员列表和新游标
     */
    Mono<OrganizationSyncResult<OfficeMember>> fetchMembers(
        String tenantId,
        OfficePlatform platform,
        String cursor
    );

    /**
     * 拉取指定部门的成员列表
     *
     * <p>从外部办公平台拉取指定部门的成员信息。
     *
     * @param tenantId       租户标识
     * @param platform       平台类型
     * @param externalDeptId 外部部门标识
     * @param cursor         同步游标（用于分页，首次传 null）
     * @return 成员列表和新游标
     */
    Mono<OrganizationSyncResult<OfficeMember>> fetchDepartmentMembers(
        String tenantId,
        OfficePlatform platform,
        String externalDeptId,
        String cursor
    );

    /**
     * 组织同步结果
     *
     * <p>包含同步的数据列表和下一次同步的游标。
     *
     * @param <T> 数据类型（OfficeDepartment 或 OfficeMember）
     */
    record OrganizationSyncResult<T>(
        List<T> data,
        String nextCursor,
        boolean hasMore
    ) {
        /**
         * 创建空结果
         */
        public static <T> OrganizationSyncResult<T> empty() {
            return new OrganizationSyncResult<>(List.of(), null, false);
        }

        /**
         * 创建完整结果
         */
        public static <T> OrganizationSyncResult<T> of(List<T> data, String nextCursor, boolean hasMore) {
            return new OrganizationSyncResult<>(data, nextCursor, hasMore);
        }

        /**
         * 创建最终结果（无更多数据）
         */
        public static <T> OrganizationSyncResult<T> ofFinal(List<T> data) {
            return new OrganizationSyncResult<>(data, null, false);
        }
    }
}
