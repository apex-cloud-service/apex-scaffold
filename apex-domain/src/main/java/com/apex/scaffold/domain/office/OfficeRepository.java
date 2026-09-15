package com.apex.scaffold.domain.office;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 办公平台领域仓储接口
 *
 * <p>提供办公平台应用配置和身份绑定的数据访问端口，所有操作均限定在租户范围内，确保多租户隔离。
 *
 * <p>实现要求：
 * <ul>
 *   <li>所有查询、更新和删除操作必须校验租户标识</li>
 *   <li>使用 {@link com.apex.scaffold.infrastructure.scheduler.BlockingIoSchedulers#jdbc()} 隔离阻塞 I/O</li>
 *   <li>更新和删除操作必须检查乐观锁版本号，防止并发冲突</li>
 *   <li>删除操作为逻辑删除，不物理删除数据</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
public interface OfficeRepository {
    /**
     * 查询租户的平台应用配置
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，不能为空
     * @return 平台应用配置，不存在时返回空 Mono
     */
    Mono<OfficePlatformApp> findApp(String tenantId, OfficePlatform platform);

    /**
     * 保存平台应用配置
     *
     * <p>新增时 id 为 null，更新时 id 不为 null。
     * 更新操作会自动递增 version 并校验乐观锁。
     *
     * @param app 平台应用配置，不能为空
     * @return 保存后的配置（包含生成的 id 和递增后的 version）
     */
    Mono<OfficePlatformApp> saveApp(OfficePlatformApp app);

    /**
     * 查询租户的所有平台应用配置列表
     *
     * @param tenantId 租户标识，不能为空
     * @return 应用配置列表，可能为空列表
     */
    Mono<List<OfficePlatformApp>> listApps(String tenantId);

    /**
     * 逻辑删除平台应用配置
     *
     * <p>删除前会校验租户、平台和乐观锁版本号。
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，不能为空
     * @param version 乐观锁版本号，不能为空
     * @return true-删除成功，false-记录不存在或版本冲突
     */
    Mono<Boolean> deleteApp(String tenantId, OfficePlatform platform, Integer version);

    /**
     * 按外部用户标识查询绑定关系
     *
     * <p>用于 OAuth 回调时根据平台返回的外部用户标识查找已审批的绑定。
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，不能为空
     * @param externalUserId 外部平台用户标识，不能为空
     * @return 绑定关系，不存在时返回空 Mono
     */
    Mono<OfficeIdentityBinding> findBinding(String tenantId, OfficePlatform platform, String externalUserId);

    /**
     * 按主键 ID 查询绑定关系
     *
     * @param tenantId 租户标识，不能为空
     * @param id 绑定记录主键 ID，不能为空
     * @return 绑定关系，不存在时返回空 Mono
     */
    Mono<OfficeIdentityBinding> findBindingById(String tenantId, Long id);

    /**
     * 分页查询租户的身份绑定列表
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，为 null 时查询所有平台
     * @param page 页码，从 0 开始
     * @param size 每页大小
     * @return 绑定关系列表，可能为空列表
     */
    Mono<List<OfficeIdentityBinding>> listBindings(String tenantId, OfficePlatform platform, int page, int size);

    /**
     * 原子修改绑定状态
     *
     * <p>使用乐观锁确保并发安全，并校验当前状态是否为 expectedStatus。
     * 只有状态匹配且版本号正确时才会更新。
     *
     * @param tenantId 租户标识，不能为空
     * @param id 绑定记录主键 ID，不能为空
     * @param version 乐观锁版本号，不能为空
     * @param expectedStatus 期望的当前状态（如 "PENDING"），不能为空
     * @param status 目标状态（如 "APPROVED"），不能为空
     * @return true-更新成功，false-记录不存在、版本冲突或状态不匹配
     */
    Mono<Boolean> changeBindingStatus(String tenantId, Long id, Integer version, String expectedStatus, String status);

    /**
     * 保存组织映射
     *
     * <p>新增或更新外部平台组织与 Apex 组织的映射关系。
     * 新增时 id 为 null，更新时 id 不为 null。
     *
     * @param mapping 组织映射，不能为空
     * @return 保存后的映射（包含生成的 id 和递增后的 version）
     */
    Mono<OfficeOrganizationMapping> saveOrganizationMapping(OfficeOrganizationMapping mapping);

    /**
     * 批量保存组织映射
     *
     * <p>用于批量导入外部平台的组织映射关系。
     *
     * @param mappings 组织映射列表，不能为空
     * @return 保存成功的映射数量
     */
    Mono<Integer> batchSaveOrganizationMappings(List<OfficeOrganizationMapping> mappings);

    /**
     * 查询外部部门的映射关系
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，不能为空
     * @param externalOrgId 外部部门标识，不能为空
     * @return 组织映射，不存在时返回空 Mono
     */
    Mono<OfficeOrganizationMapping> findDepartmentMapping(String tenantId, OfficePlatform platform, String externalOrgId);

    /**
     * 查询外部用户的映射关系
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，不能为空
     * @param externalUserId 外部用户标识，不能为空
     * @return 组织映射，不存在时返回空 Mono
     */
    Mono<OfficeOrganizationMapping> findMemberMapping(String tenantId, OfficePlatform platform, String externalUserId);

    /**
     * 查询租户的所有组织映射列表
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，为 null 时查询所有平台
     * @return 组织映射列表，可能为空列表
     */
    Mono<List<OfficeOrganizationMapping>> listOrganizationMappings(String tenantId, OfficePlatform platform);

    /**
     * 保存同步任务
     *
     * <p>新增或更新同步任务状态。
     * 新增时 id 为 null，更新时 id 不为 null。
     *
     * @param task 同步任务，不能为空
     * @return 保存后的任务（包含生成的 id 和递增后的 version）
     */
    Mono<OfficeSyncTask> saveSyncTask(OfficeSyncTask task);

    /**
     * 查询租户的最新同步任务
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，不能为空
     * @return 同步任务，不存在时返回空 Mono
     */
    Mono<OfficeSyncTask> findLatestSyncTask(String tenantId, OfficePlatform platform);

    /**
     * 查询租户的所有同步任务列表
     *
     * @param tenantId 租户标识，不能为空
     * @param platform 平台类型，为 null 时查询所有平台
     * @param page 页码，从 0 开始
     * @param size 每页大小
     * @return 同步任务列表，可能为空列表
     */
    Mono<List<OfficeSyncTask>> listSyncTasks(String tenantId, OfficePlatform platform, int page, int size);
}
