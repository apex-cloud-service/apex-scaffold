package com.apex.scaffold.application.office;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.*;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 办公平台组织同步应用服务
 *
 * <p>负责从外部办公平台（飞书、钉钉、企业微信）同步组织架构和成员信息到 Apex 系统。
 * 提供增量同步和全量同步能力，支持断点续传。
 *
 * <p>主要功能：
 * <ul>
 *   <li>同步部门：拉取外部平台部门，创建或更新 Apex 部门，建立映射关系</li>
 *   <li>同步成员：拉取外部平台成员，创建或更新 Apex 用户，建立映射关系</li>
 *   <li>任务管理：记录同步任务状态，支持断点续传和增量同步</li>
 *   <li>映射查询：提供组织映射关系的查询能力</li>
 * </ul>
 *
 * <p>事务边界：
 * <ul>
 *   <li>同步操作在事务内执行，确保映射关系的一致性</li>
 *   <li>使用 TransactionalOperator 支持 WebFlux 响应式事务</li>
 *   <li>失败时回滚映射关系和任务状态</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeOrganizationSyncService {

    private final OfficeRepository officeRepository;
    private final OfficeOrganizationService officeOrganizationService;
    private final TransactionalOperator transactionalOperator;

    /**
     * 同步部门
     *
     * <p>从外部平台拉取部门信息，并建立与 Apex 部门的映射关系。
     * 支持增量同步，使用上次同步的 cursor 继续拉取。
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param fullSync 是否全量同步（true-从头开始，false-增量同步）
     * @return 同步任务结果
     */
    public Mono<SyncTaskResult> syncDepartments(String tenantId, OfficePlatform platform, boolean fullSync) {
        log.info("开始同步部门，租户：{}，平台：{}，全量：{}", tenantId, platform, fullSync);

        return createOrGetSyncTask(tenantId, platform, fullSync)
                .flatMap(task -> {
                    String cursor = fullSync ? null : task.getCursor();
                    return syncDepartmentsRecursive(tenantId, platform, cursor, 0, new ArrayList<>(16))
                            .flatMap(result -> updateSyncTaskSuccess(task, result.nextCursor())
                                    .map(updatedTask -> SyncTaskResult.success(
                                            updatedTask.getId(), result.syncedCount(), "部门同步完成")))
                            .onErrorResume(error -> {
                                log.error("同步部门失败，租户：{}，平台：{}", tenantId, platform, error);
                                return updateSyncTaskFailure(task, error.getMessage())
                                        .then(Mono.error(new BusinessException(
                                                ResultCode.BUSINESS_ERROR, "同步部门失败：" + error.getMessage())));
                            });
                })
                .as(transactionalOperator::transactional);
    }

    /**
     * 同步成员
     *
     * <p>从外部平台拉取成员信息，并建立与 Apex 用户的映射关系。
     * 支持增量同步，使用上次同步的 cursor 继续拉取。
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param fullSync 是否全量同步（true-从头开始，false-增量同步）
     * @return 同步任务结果
     */
    public Mono<SyncTaskResult> syncMembers(String tenantId, OfficePlatform platform, boolean fullSync) {
        log.info("开始同步成员，租户：{}，平台：{}，全量：{}", tenantId, platform, fullSync);

        return createOrGetSyncTask(tenantId, platform, fullSync)
                .flatMap(task -> {
                    String cursor = fullSync ? null : task.getCursor();
                    return syncMembersRecursive(tenantId, platform, cursor, 0, new ArrayList<>(16))
                            .flatMap(result -> updateSyncTaskSuccess(task, result.nextCursor())
                                    .map(updatedTask -> SyncTaskResult.success(
                                            updatedTask.getId(), result.syncedCount(), "成员同步完成")))
                            .onErrorResume(error -> {
                                log.error("同步成员失败，租户：{}，平台：{}", tenantId, platform, error);
                                return updateSyncTaskFailure(task, error.getMessage())
                                        .then(Mono.error(new BusinessException(
                                                ResultCode.BUSINESS_ERROR, "同步成员失败：" + error.getMessage())));
                            });
                })
                .as(transactionalOperator::transactional);
    }

    /**
     * 查询部门映射
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param externalOrgId 外部部门标识
     * @return 组织映射
     */
    public Mono<OfficeOrganizationMapping> getDepartmentMapping(
            String tenantId, OfficePlatform platform, String externalOrgId) {
        return officeRepository.findDepartmentMapping(tenantId, platform, externalOrgId);
    }

    /**
     * 查询成员映射
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param externalUserId 外部用户标识
     * @return 组织映射
     */
    public Mono<OfficeOrganizationMapping> getMemberMapping(
            String tenantId, OfficePlatform platform, String externalUserId) {
        return officeRepository.findMemberMapping(tenantId, platform, externalUserId);
    }

    /**
     * 查询组织映射列表
     *
     * @param tenantId 租户标识
     * @param platform 平台类型（可选）
     * @return 组织映射列表
     */
    public Mono<List<OfficeOrganizationMapping>> listOrganizationMappings(String tenantId, OfficePlatform platform) {
        return officeRepository.listOrganizationMappings(tenantId, platform);
    }

    /**
     * 查询同步任务列表
     *
     * @param tenantId 租户标识
     * @param platform 平台类型（可选）
     * @param page 页码
     * @param size 每页大小
     * @return 同步任务列表
     */
    public Mono<List<OfficeSyncTask>> listSyncTasks(String tenantId, OfficePlatform platform, int page, int size) {
        return officeRepository.listSyncTasks(tenantId, platform, page, size);
    }

    // ========== 私有方法 ==========

    private Mono<OfficeSyncTask> createOrGetSyncTask(String tenantId, OfficePlatform platform, boolean fullSync) {
        if (fullSync) {
            return createNewSyncTask(tenantId, platform);
        }
        return officeRepository
                .findLatestSyncTask(tenantId, platform)
                .switchIfEmpty(createNewSyncTask(tenantId, platform));
    }

    private Mono<OfficeSyncTask> createNewSyncTask(String tenantId, OfficePlatform platform) {
        OfficeSyncTask task = OfficeSyncTask.builder()
                .tenantId(tenantId)
                .platform(platform)
                .state("RUNNING")
                .cursor(null)
                .failureMessage(null)
                .version(0)
                .deleted(0)
                .build();
        return officeRepository.saveSyncTask(task);
    }

    private Mono<OfficeSyncTask> updateSyncTaskSuccess(OfficeSyncTask task, String nextCursor) {
        OfficeSyncTask updatedTask = OfficeSyncTask.builder()
                .id(task.getId())
                .tenantId(task.getTenantId())
                .platform(task.getPlatform())
                .state("SUCCESS")
                .cursor(nextCursor)
                .failureMessage(null)
                .version(task.getVersion())
                .deleted(task.getDeleted())
                .build();
        return officeRepository.saveSyncTask(updatedTask);
    }

    private Mono<OfficeSyncTask> updateSyncTaskFailure(OfficeSyncTask task, String errorMessage) {
        OfficeSyncTask updatedTask = OfficeSyncTask.builder()
                .id(task.getId())
                .tenantId(task.getTenantId())
                .platform(task.getPlatform())
                .state("FAILED")
                .cursor(task.getCursor())
                .failureMessage(errorMessage)
                .version(task.getVersion())
                .deleted(task.getDeleted())
                .build();
        return officeRepository.saveSyncTask(updatedTask);
    }

    private Mono<SyncBatchResult> syncDepartmentsRecursive(
            String tenantId, OfficePlatform platform, String cursor, int syncedCount, List<String> allCursors) {
        return officeOrganizationService
                .fetchDepartments(tenantId, platform, cursor)
                .flatMap(result -> {
                    if (result.data().isEmpty()) {
                        return Mono.just(new SyncBatchResult(syncedCount, cursor));
                    }

                    return Flux.fromIterable(result.data())
                            .flatMap(dept -> saveDepartmentMapping(tenantId, platform, dept))
                            .collectList()
                            .flatMap(mappings -> {
                                int newSyncedCount = syncedCount + mappings.size();
                                if (result.hasMore() && result.nextCursor() != null) {
                                    allCursors.add(result.nextCursor());
                                    return syncDepartmentsRecursive(
                                            tenantId, platform, result.nextCursor(), newSyncedCount, allCursors);
                                }
                                return Mono.just(new SyncBatchResult(newSyncedCount, result.nextCursor()));
                            });
                });
    }

    private Mono<SyncBatchResult> syncMembersRecursive(
            String tenantId, OfficePlatform platform, String cursor, int syncedCount, List<String> allCursors) {
        return officeOrganizationService
                .fetchMembers(tenantId, platform, cursor)
                .flatMap(result -> {
                    if (result.data().isEmpty()) {
                        return Mono.just(new SyncBatchResult(syncedCount, cursor));
                    }

                    return Flux.fromIterable(result.data())
                            .flatMap(member -> saveMemberMapping(tenantId, platform, member))
                            .collectList()
                            .flatMap(mappings -> {
                                int newSyncedCount = syncedCount + mappings.size();
                                if (result.hasMore() && result.nextCursor() != null) {
                                    allCursors.add(result.nextCursor());
                                    return syncMembersRecursive(
                                            tenantId, platform, result.nextCursor(), newSyncedCount, allCursors);
                                }
                                return Mono.just(new SyncBatchResult(newSyncedCount, result.nextCursor()));
                            });
                });
    }

    private Mono<OfficeOrganizationMapping> saveDepartmentMapping(
            String tenantId, OfficePlatform platform, OfficeDepartment dept) {
        // 这里简化处理，实际应该调用内部的部门服务创建或查找 Apex 部门
        // 并返回 apexDeptId，这里用 externalOrgId 作为占位
        String apexDeptId = "apex-dept-" + dept.getExternalDeptId();

        return officeRepository
                .findDepartmentMapping(tenantId, platform, dept.getExternalDeptId())
                .switchIfEmpty(Mono.defer(() -> {
                    OfficeOrganizationMapping mapping = OfficeOrganizationMapping.builder()
                            .tenantId(tenantId)
                            .platform(platform)
                            .externalOrgId(dept.getExternalDeptId())
                            .externalUserId(null)
                            .apexDeptId(apexDeptId)
                            .apexUserId(null)
                            .version(0)
                            .deleted(0)
                            .build();
                    return officeRepository.saveOrganizationMapping(mapping);
                }));
    }

    private Mono<OfficeOrganizationMapping> saveMemberMapping(
            String tenantId, OfficePlatform platform, OfficeMember member) {
        // 这里简化处理，实际应该调用内部的用户服务创建或查找 Apex 用户
        // 并返回 apexUserId，这里用 externalUserId 作为占位
        String apexUserId = "apex-user-" + member.getExternalUserId();

        return officeRepository
                .findMemberMapping(tenantId, platform, member.getExternalUserId())
                .switchIfEmpty(Mono.defer(() -> {
                    OfficeOrganizationMapping mapping = OfficeOrganizationMapping.builder()
                            .tenantId(tenantId)
                            .platform(platform)
                            .externalOrgId(null)
                            .externalUserId(member.getExternalUserId())
                            .apexDeptId(null)
                            .apexUserId(apexUserId)
                            .version(0)
                            .deleted(0)
                            .build();
                    return officeRepository.saveOrganizationMapping(mapping);
                }));
    }

    private record SyncBatchResult(int syncedCount, String nextCursor) {}

    /**
     * 同步任务结果
     */
    public record SyncTaskResult(Long taskId, int syncedCount, String message, boolean success) {
        /**
         * 创建表示同步成功的任务结果。
         *
         * @param taskId 同步任务 ID
         * @param syncedCount 同步记录数
         * @param message 结果描述信息
         * @return 同步成功的任务结果
         */
        public static SyncTaskResult success(Long taskId, int syncedCount, String message) {
            return new SyncTaskResult(taskId, syncedCount, message, true);
        }

        /**
         * 创建表示同步失败的任务结果。
         *
         * @param taskId 同步任务 ID
         * @param message 失败原因描述
         * @return 同步失败的任务结果
         */
        public static SyncTaskResult failure(Long taskId, String message) {
            return new SyncTaskResult(taskId, 0, message, false);
        }
    }
}
