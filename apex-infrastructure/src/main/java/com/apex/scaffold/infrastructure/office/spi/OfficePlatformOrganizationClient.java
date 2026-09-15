package com.apex.scaffold.infrastructure.office.spi;

import com.apex.scaffold.domain.office.*;
import reactor.core.publisher.Mono;

/**
 * 办公平台组织服务 SPI
 *
 * <p>定义与外部办公平台交互的组织通讯录操作，每个平台需要实现此接口。
 * 实现类应注册为 Spring Bean，并通过 {@link com.apex.scaffold.domain.office.OfficePlatform} 区分。
 *
 * <p>设计要点：
 * <ul>
 *   <li>所有方法返回 Mono，支持响应式非阻塞调用</li>
 *   <li>处理平台 API 的分页、限流和重试逻辑</li>
 *   <li>统一异常处理，转换为 BusinessException</li>
 *   <li>支持增量同步（通过 cursor）</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface OfficePlatformOrganizationClient {

    /**
     * 获取平台类型
     *
     * @return 平台类型
     */
    OfficePlatform platform();

    /**
     * 拉取部门列表
     *
     * <p>从外部办公平台拉取部门信息，支持增量同步。
     *
     * @param appId  应用标识
     * @param secret 应用密钥
     * @param cursor 同步游标（用于增量同步，首次传 null）
     * @return 部门列表和新游标
     */
    Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeDepartment>> fetchDepartments(
        String appId,
        String secret,
        String cursor
    );

    /**
     * 拉取成员列表
     *
     * <p>从外部办公平台拉取成员信息，支持增量同步。
     *
     * @param appId  应用标识
     * @param secret 应用密钥
     * @param cursor 同步游标（用于增量同步，首次传 null）
     * @return 成员列表和新游标
     */
    Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchMembers(
        String appId,
        String secret,
        String cursor
    );

    /**
     * 拉取指定部门的成员列表
     *
     * <p>从外部办公平台拉取指定部门的成员信息。
     *
     * @param appId          应用标识
     * @param secret         应用密钥
     * @param externalDeptId 外部部门标识
     * @param cursor         同步游标（用于分页，首次传 null）
     * @return 成员列表和新游标
     */
    Mono<OfficeOrganizationService.OrganizationSyncResult<OfficeMember>> fetchDepartmentMembers(
        String appId,
        String secret,
        String externalDeptId,
        String cursor
    );
}
