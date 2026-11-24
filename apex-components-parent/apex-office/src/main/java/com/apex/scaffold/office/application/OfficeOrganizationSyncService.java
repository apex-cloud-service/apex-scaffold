package com.apex.scaffold.office.application;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.application.port.OfficeOrgUserQueryPort;
import com.apex.scaffold.office.application.port.OfficeOrgUserRef;
import com.apex.scaffold.office.application.port.OfficeOrgUserSnapshot;
import com.apex.scaffold.office.domain.*;
import com.apex.scaffold.office.off.domain.entity.OffDepartmentEntity;
import com.apex.scaffold.office.off.domain.entity.OffEmployeeEntity;
import com.apex.scaffold.office.off.domain.repository.OffDepartmentRepository;
import com.apex.scaffold.office.off.domain.repository.OffEmployeeRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 *   <li>办公服务使用阻塞式 MyBatis/JDBC，{@code TransactionalOperator} 为 no-op 降级实现，
 *       不提供跨步骤回滚保证；同步流程依靠检查点续传与幂等 upsert 保证可重试</li>
 *   <li>单条映射写入需长期保持原子性时，应在仓储层使用同步 {@code TransactionTemplate}，
 *       不能依赖 {@code .as(transactionalOperator::transactional)}</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeOrganizationSyncService {

    /** 部门同步类型 */
    public static final String SYNC_TYPE_DEPARTMENT = "DEPARTMENT";

    /** 成员同步类型 */
    public static final String SYNC_TYPE_MEMBER = "MEMBER";

    private static final String SYNC_STATE_FAILED = "FAILED";

    private final OfficeRepository officeRepository;
    private final OfficeOrganizationService officeOrganizationService;
    private final OffDepartmentRepository offDepartmentRepository;
    private final OffEmployeeRepository offEmployeeRepository;
    private final OfficeOrgUserQueryPort orgUserQueryPort;
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
        return executeSync(
                tenantId,
                platform,
                SYNC_TYPE_DEPARTMENT,
                fullSync,
                createOrGetSyncTask(tenantId, platform, SYNC_TYPE_DEPARTMENT, fullSync));
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
        return executeSync(
                tenantId,
                platform,
                SYNC_TYPE_MEMBER,
                fullSync,
                createOrGetSyncTask(tenantId, platform, SYNC_TYPE_MEMBER, fullSync));
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

    /**
     * 同步预检：校验平台应用配置是否就绪。
     *
     * <p>检查项：应用已配置、已启用、secretReference 指向的环境变量非空。
     * 预检不调用外部平台 API，仅基于本地配置与环境变量。
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @return 预检结果（ready 与缺失项列表）
     */
    public Mono<PreflightResult> preflight(String tenantId, OfficePlatform platform) {
        return officeRepository
                .findApp(tenantId, platform)
                .map(app -> {
                    List<String> missing = new ArrayList<>(4);
                    if (!app.isEnabled()) {
                        missing.add("平台应用未启用");
                    }
                    if (app.getSecretReference() == null
                            || app.getSecretReference().isBlank()) {
                        missing.add("未配置 secretReference 环境变量引用");
                    } else {
                        String secret = System.getenv(app.getSecretReference());
                        if (secret == null || secret.isBlank()) {
                            missing.add("环境变量 " + app.getSecretReference() + " 未设置或为空");
                        }
                    }
                    return new PreflightResult(missing.isEmpty(), missing);
                })
                .defaultIfEmpty(new PreflightResult(false, List.of("未配置该平台应用")));
    }

    /**
     * 同步预览：只读拉取首页数据并与已有映射对比，返回差异估算。
     *
     * <p>不写入任何数据，用于向导确认同步范围。
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param syncType 同步类型（DEPARTMENT/MEMBER）
     * @return 预览结果（首页样本数、是否有更多、已映射数、样本新增数）
     */
    public Mono<SyncPreviewResult> previewSync(String tenantId, OfficePlatform platform, String syncType) {
        boolean department = SYNC_TYPE_DEPARTMENT.equals(syncType);
        Mono<? extends OfficeOrganizationService.OrganizationSyncResult<?>> fetch = department
                ? officeOrganizationService.fetchDepartments(tenantId, platform, null)
                : officeOrganizationService.fetchMembers(tenantId, platform, null);
        return fetch.flatMap(result -> {
            List<String> externalIds = department
                    ? result.data().stream()
                            .map(item -> ((OfficeDepartment) item).getExternalDeptId())
                            .toList()
                    : result.data().stream()
                            .map(item -> ((OfficeMember) item).getExternalUserId())
                            .toList();
            return Flux.fromIterable(externalIds)
                    .flatMap(externalId -> department
                            ? officeRepository.findDepartmentMapping(tenantId, platform, externalId)
                            : officeRepository.findMemberMapping(tenantId, platform, externalId))
                    .collectList()
                    .map(existing -> {
                        int mappedCount = existing.size();
                        return new SyncPreviewResult(
                                result.data().size(),
                                result.hasMore(),
                                mappedCount,
                                result.data().size() - mappedCount);
                    });
        });
    }

    /**
     * 按 ID 查询同步任务（租户隔离）。
     *
     * @param tenantId 租户标识
     * @param taskId 任务 ID
     * @return 同步任务，不存在时返回 empty
     */
    public Mono<OfficeSyncTask> getSyncTask(String tenantId, Long taskId) {
        return officeRepository.findSyncTaskById(tenantId, taskId);
    }

    /**
     * 重试失败的同步任务。
     *
     * <p>仅 FAILED 状态可重试；直接恢复指定任务行（保持任务 ID 与检查点 cursor），
     * 从上次检查点增量续传，映射幂等保证不重复创建。
     *
     * @param tenantId 租户标识
     * @param taskId 任务 ID
     * @return 同步任务结果
     */
    public Mono<SyncTaskResult> retrySyncTask(String tenantId, Long taskId) {
        return officeRepository
                .findSyncTaskById(tenantId, taskId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "同步任务不存在")))
                .flatMap(task -> {
                    if (!SYNC_STATE_FAILED.equals(task.getState())) {
                        return Mono.error(new BusinessException(
                                ResultCode.BUSINESS_ERROR, "仅失败状态的同步任务可重试，当前状态：" + task.getState()));
                    }
                    boolean fullSync = task.getCursor() == null;
                    return executeSync(
                            tenantId, task.getPlatform(), task.getSyncType(), fullSync, resetFailedTaskToRunning(task));
                });
    }

    // ========== 私有方法 ==========

    /**
     * 统一同步执行骨架：获取/复用任务 → 逐页同步（含检查点） → 终态落库。
     *
     * <p>续传或增量同步时，起始游标计入已访问游标集合，防止平台回吐相同游标导致死循环。
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @param syncType 同步类型（DEPARTMENT/MEMBER）
     * @param fullSync 是否全量同步
     * @param taskSource 任务来源（复用既有行或新建）
     * @return 同步任务结果
     */
    private Mono<SyncTaskResult> executeSync(
            String tenantId,
            OfficePlatform platform,
            String syncType,
            boolean fullSync,
            Mono<OfficeSyncTask> taskSource) {
        boolean member = SYNC_TYPE_MEMBER.equals(syncType);
        return taskSource
                .flatMap(task -> {
                    String cursor = fullSync ? null : task.getCursor();
                    AtomicReference<OfficeSyncTask> taskRef = new AtomicReference<>(task);
                    List<String> visitedCursors = new ArrayList<>(16);
                    if (cursor != null) {
                        visitedCursors.add(cursor);
                    }
                    List<OfficeDepartment> collectedDepartments = new ArrayList<>();
                    List<OfficeMember> collectedMembers = new ArrayList<>();
                    Mono<SyncBatchResult> pages = member
                            ? syncMembersRecursive(
                                    tenantId, platform, cursor, 0, visitedCursors, taskRef, collectedMembers)
                            : syncDepartmentsRecursive(
                                    tenantId, platform, cursor, 0, visitedCursors, taskRef, collectedDepartments);
                    return pages.flatMap(result -> {
                                Mono<Map<String, Long>> bindings = member
                                        ? resolveMemberBindings(tenantId, collectedMembers)
                                        : Mono.just(Map.of());
                                return bindings.defaultIfEmpty(Map.of()).flatMap(resolved -> finishSync(
                                                tenantId,
                                                platform,
                                                member,
                                                collectedDepartments,
                                                resolved,
                                                taskRef.get(),
                                                result)
                                        .map(updatedTask -> SyncTaskResult.success(
                                                updatedTask.getId(),
                                                result.syncedCount(),
                                                member ? "成员同步完成" : "部门同步完成")));
                            })
                            .onErrorResume(error -> {
                                log.error("同步失败，租户：{}，平台：{}，类型：{}", tenantId, platform, syncType, error);
                                return updateSyncTaskFailure(taskRef.get(), error.getMessage())
                                        .then(Mono.error(new BusinessException(
                                                ResultCode.BUSINESS_ERROR,
                                                (member ? "同步成员失败：" : "同步部门失败：") + error.getMessage())));
                            });
                })
                .as(transactionalOperator::transactional);
    }

    /**
     * 获取或复用同步任务。
     *
     * <p>uk_office_sync 唯一键（tenant_id, platform, sync_type）约束下同一维度只允许一行任务，
     * 无论全量还是增量都复用既有任务行：重置为 RUNNING，全量清空 cursor，增量保留上次游标。
     */
    private Mono<OfficeSyncTask> createOrGetSyncTask(
            String tenantId, OfficePlatform platform, String syncType, boolean fullSync) {
        return officeRepository
                .findLatestSyncTask(tenantId, platform, syncType)
                .flatMap(existing -> resetSyncTask(existing, fullSync))
                .switchIfEmpty(Mono.defer(() -> createNewSyncTask(tenantId, platform, syncType)));
    }

    /** 复用既有任务行：保持任务 ID，重置为 RUNNING 并按需清空游标，避免重复插入违反唯一键。 */
    private Mono<OfficeSyncTask> resetSyncTask(OfficeSyncTask existing, boolean fullSync) {
        OfficeSyncTask reset = OfficeSyncTask.builder()
                .id(existing.getId())
                .tenantId(existing.getTenantId())
                .platform(existing.getPlatform())
                .syncType(existing.getSyncType())
                .state("RUNNING")
                .cursor(fullSync ? null : existing.getCursor())
                .failureMessage(null)
                .version(existing.getVersion())
                .deleted(existing.getDeleted())
                .build();
        return officeRepository.saveSyncTask(reset);
    }

    /** 重试复用原 FAILED 任务行：保留检查点 cursor，仅重置状态为 RUNNING。 */
    private Mono<OfficeSyncTask> resetFailedTaskToRunning(OfficeSyncTask task) {
        OfficeSyncTask reset = OfficeSyncTask.builder()
                .id(task.getId())
                .tenantId(task.getTenantId())
                .platform(task.getPlatform())
                .syncType(task.getSyncType())
                .state("RUNNING")
                .cursor(task.getCursor())
                .failureMessage(null)
                .version(task.getVersion())
                .deleted(task.getDeleted())
                .build();
        return officeRepository.saveSyncTask(reset);
    }

    private Mono<OfficeSyncTask> createNewSyncTask(String tenantId, OfficePlatform platform, String syncType) {
        OfficeSyncTask task = OfficeSyncTask.builder()
                .tenantId(tenantId)
                .platform(platform)
                .syncType(syncType)
                .state("RUNNING")
                .cursor(null)
                .failureMessage(null)
                .version(0)
                .deleted(0)
                .build();
        return officeRepository.saveSyncTask(task);
    }

    /** 逐页检查点：页内数据写完后持久化最新游标，失败重试可从检查点恢复。 */
    private Mono<OfficeSyncTask> saveSyncTaskCheckpoint(OfficeSyncTask task, String cursor) {
        OfficeSyncTask checkpoint = OfficeSyncTask.builder()
                .id(task.getId())
                .tenantId(task.getTenantId())
                .platform(task.getPlatform())
                .syncType(task.getSyncType())
                .state("RUNNING")
                .cursor(cursor)
                .failureMessage(null)
                .version(task.getVersion())
                .deleted(task.getDeleted())
                .build();
        return officeRepository.saveSyncTask(checkpoint);
    }

    private Mono<OfficeSyncTask> updateSyncTaskSuccess(OfficeSyncTask task, String nextCursor) {
        OfficeSyncTask updatedTask = OfficeSyncTask.builder()
                .id(task.getId())
                .tenantId(task.getTenantId())
                .platform(task.getPlatform())
                .syncType(task.getSyncType())
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
                .syncType(task.getSyncType())
                .state("FAILED")
                .cursor(task.getCursor())
                .failureMessage(errorMessage)
                .version(task.getVersion())
                .deleted(task.getDeleted())
                .build();
        return officeRepository.saveSyncTask(updatedTask);
    }

    /**
     * 同步收尾：部门同步重算层级，成员同步回填真实系统用户，随后把任务置为成功。
     */
    private Mono<OfficeSyncTask> finishSync(
            String tenantId,
            OfficePlatform platform,
            boolean member,
            List<OfficeDepartment> collectedDepartments,
            Map<String, Long> bindings,
            OfficeSyncTask task,
            SyncBatchResult result) {
        Mono<Void> prepare = member
                ? rebuildEmployeeBindings(tenantId, platform, bindings)
                : rebuildDepartmentHierarchy(platform, collectedDepartments);
        return prepare.then(updateSyncTaskSuccess(task, result.nextCursor()));
    }

    /**
     * 事务外批量解析成员对应的真实系统用户。
     *
     * <p>解析依赖 system 内部接口，属于远程调用，必须在数据库事务之外完成，
     * 因此在成员分页完成后、收尾写库前单独执行；解析只读，失败降级为空绑定。</p>
     *
     * @param tenantId       租户标识
     * @param collectedMembers 本次同步拉取到的全部成员
     * @return 外部用户标识到系统用户主键的绑定，未命中不出现
     */
    private Mono<Map<String, Long>> resolveMemberBindings(String tenantId, List<OfficeMember> collectedMembers) {
        return Mono.defer(() -> {
            List<OfficeOrgUserRef> refs = collectedMembers.stream()
                    .filter(member -> member.getExternalUserId() != null)
                    .map(member -> OfficeOrgUserRef.builder()
                            .externalUserId(member.getExternalUserId())
                            .mobile(member.getMobile())
                            .email(member.getEmail())
                            .username(null)
                            .userId(null)
                            .build())
                    .toList();
            if (refs.isEmpty()) {
                return Mono.just(Map.of());
            }
            return orgUserQueryPort
                    .resolveUsers(tenantId, refs)
                    .subscribeOn(BlockingIoSchedulers.remote())
                    .map(OfficeOrganizationSyncService::toBindings)
                    .defaultIfEmpty(Map.of());
        });
    }

    /** 把解析结果转换为外部用户标识到系统用户主键的映射，空值条目直接丢弃。 */
    private static Map<String, Long> toBindings(List<OfficeOrgUserSnapshot> snapshots) {
        Map<String, Long> bindings = new HashMap<>(snapshots.size());
        for (OfficeOrgUserSnapshot snapshot : snapshots) {
            if (snapshot != null && snapshot.externalUserId() != null && snapshot.userId() != null) {
                bindings.putIfAbsent(snapshot.externalUserId(), snapshot.userId());
            }
        }
        return bindings;
    }

    /**
     * 把解析到的系统用户主键回填到员工镜像与成员映射。
     *
     * <p>成员分页阶段写入镜像和映射时还没有真实用户绑定，解析在分页完成后、事务外完成，
     * 因此在本阶段按外部标识补写 userId 与映射的 apexUserId，保持三者一致。</p>
     */
    private Mono<Void> rebuildEmployeeBindings(String tenantId, OfficePlatform platform, Map<String, Long> bindings) {
        return Flux.fromIterable(bindings.entrySet())
                .concatMap(entry -> offEmployeeRepository
                        .findByExternalId(platform.name(), entry.getKey())
                        .next()
                        .flatMap(employee -> {
                            if (Objects.equals(employee.getUserId(), entry.getValue())) {
                                return Mono.just(employee);
                            }
                            employee.setUserId(entry.getValue());
                            return offEmployeeRepository.update(employee);
                        })
                        .then(backfillMemberMapping(tenantId, platform, entry.getKey(), entry.getValue())))
                .then();
    }

    /** 成员映射补写 apexUserId；映射不存在时跳过，不做额外创建。 */
    private Mono<Void> backfillMemberMapping(
            String tenantId, OfficePlatform platform, String externalUserId, Long userId) {
        return officeRepository
                .findMemberMapping(tenantId, platform, externalUserId)
                .flatMap(mapping -> {
                    String apexUserId = String.valueOf(userId);
                    if (Objects.equals(mapping.getApexUserId(), apexUserId)) {
                        return Mono.just(mapping);
                    }
                    OfficeOrganizationMapping updated = OfficeOrganizationMapping.builder()
                            .id(mapping.getId())
                            .tenantId(mapping.getTenantId())
                            .platform(mapping.getPlatform())
                            .externalOrgId(mapping.getExternalOrgId())
                            .externalUserId(mapping.getExternalUserId())
                            .apexDeptId(mapping.getApexDeptId())
                            .apexUserId(apexUserId)
                            .version(mapping.getVersion())
                            .deleted(mapping.getDeleted())
                            .build();
                    return officeRepository.saveOrganizationMapping(updated);
                })
                .then();
    }

    private Mono<SyncBatchResult> syncDepartmentsRecursive(
            String tenantId,
            OfficePlatform platform,
            String cursor,
            int syncedCount,
            List<String> allCursors,
            AtomicReference<OfficeSyncTask> taskRef,
            List<OfficeDepartment> collectedDepartments) {
        return officeOrganizationService
                .fetchDepartments(tenantId, platform, cursor)
                .flatMap(result -> {
                    Mono<Integer> persistedCount = result.data().isEmpty()
                            ? Mono.just(syncedCount)
                            : Flux.fromIterable(result.data())
                                    .flatMap(dept -> saveDepartmentMapping(tenantId, platform, dept))
                                    .collectList()
                                    .map(mappings -> syncedCount + mappings.size());
                    collectedDepartments.addAll(result.data());
                    return persistedCount.flatMap(newSyncedCount -> advancePagination(
                            result,
                            newSyncedCount,
                            allCursors,
                            taskRef,
                            nextCursor -> syncDepartmentsRecursive(
                                    tenantId,
                                    platform,
                                    nextCursor,
                                    newSyncedCount,
                                    allCursors,
                                    taskRef,
                                    collectedDepartments)));
                });
    }

    private Mono<SyncBatchResult> syncMembersRecursive(
            String tenantId,
            OfficePlatform platform,
            String cursor,
            int syncedCount,
            List<String> allCursors,
            AtomicReference<OfficeSyncTask> taskRef,
            List<OfficeMember> collectedMembers) {
        return officeOrganizationService
                .fetchMembers(tenantId, platform, cursor)
                .flatMap(result -> {
                    Mono<Integer> persistedCount = result.data().isEmpty()
                            ? Mono.just(syncedCount)
                            : Flux.fromIterable(result.data())
                                    .flatMap(member -> saveMemberMapping(tenantId, platform, member))
                                    .collectList()
                                    .map(mappings -> syncedCount + mappings.size());
                    collectedMembers.addAll(result.data());
                    return persistedCount.flatMap(newSyncedCount -> advancePagination(
                            result,
                            newSyncedCount,
                            allCursors,
                            taskRef,
                            nextCursor -> syncMembersRecursive(
                                    tenantId,
                                    platform,
                                    nextCursor,
                                    newSyncedCount,
                                    allCursors,
                                    taskRef,
                                    collectedMembers)));
                });
    }

    /**
     * 统一分页推进。
     *
     * <p>平台返回 hasMore=true 时必须提供 nextCursor，否则显式失败而不是静默当作同步完成；
     * 空页但 hasMore=true 同样写入检查点并继续推进；重复游标判定包含续传起始游标，
     * 防止平台分页异常导致的死循环。
     */
    private Mono<SyncBatchResult> advancePagination(
            OfficeOrganizationService.OrganizationSyncResult<?> result,
            int syncedCount,
            List<String> allCursors,
            AtomicReference<OfficeSyncTask> taskRef,
            Function<String, Mono<SyncBatchResult>> next) {
        if (result.hasMore()) {
            if (result.nextCursor() == null) {
                return Mono.error(
                        new BusinessException(ResultCode.BUSINESS_ERROR, "平台返回 hasMore=true 但未提供 nextCursor，同步终止"));
            }
            if (allCursors.contains(result.nextCursor())) {
                return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "检测到重复分页游标，同步终止"));
            }
            allCursors.add(result.nextCursor());
            return saveSyncTaskCheckpoint(taskRef.get(), result.nextCursor())
                    .doOnNext(taskRef::set)
                    .flatMap(checkpoint -> next.apply(result.nextCursor()));
        }
        return Mono.just(new SyncBatchResult(syncedCount, result.nextCursor()));
    }

    /**
     * 保存部门映射。
     *
     * <p>先在 off_department 镜像表按 (syncSource, externalId) 幂等 upsert，得到真实内部部门 ID
     * 后再写入映射。已存在映射时直接复用，保证重试与断点续传不产生重复记录。
     */
    private Mono<OfficeOrganizationMapping> saveDepartmentMapping(
            String tenantId, OfficePlatform platform, OfficeDepartment dept) {
        return officeRepository
                .findDepartmentMapping(tenantId, platform, dept.getExternalDeptId())
                .switchIfEmpty(Mono.defer(
                        () -> upsertDepartment(tenantId, platform, dept).flatMap(entity -> {
                            OfficeOrganizationMapping mapping = OfficeOrganizationMapping.builder()
                                    .tenantId(tenantId)
                                    .platform(platform)
                                    .externalOrgId(dept.getExternalDeptId())
                                    .externalUserId(null)
                                    .apexDeptId(String.valueOf(entity.getId()))
                                    .apexUserId(null)
                                    .version(0)
                                    .deleted(0)
                                    .build();
                            return officeRepository.saveOrganizationMapping(mapping);
                        })));
    }

    /**
     * off_department 镜像幂等 upsert：命中则更新最新名称/排序/状态，否则新建并回填真实内部 ID。
     *
     * <p>父子层级不在此处直接落库：外部平台按页返回部门，父部门可能出现在后续页，
     * 写入时无法保证父镜像已存在。此处只保存基础字段，层级由本页写完后统一重算。</p>
     */
    private Mono<OffDepartmentEntity> upsertDepartment(
            String tenantId, OfficePlatform platform, OfficeDepartment dept) {
        return offDepartmentRepository
                .findByExternalId(platform.name(), dept.getExternalDeptId())
                .next()
                .flatMap(existing -> {
                    existing.setDeptName(dept.getName());
                    existing.setSortOrder(dept.getOrder());
                    existing.setStatus("ACTIVE");
                    existing.setSyncSource(platform.name());
                    existing.setExternalId(dept.getExternalDeptId());
                    existing.setTenantId(tenantId);
                    return offDepartmentRepository.update(existing).thenReturn(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    OffDepartmentEntity entity = new OffDepartmentEntity();
                    // deptCode 需满足 uk_off_department_code_tenant 唯一约束，用平台前缀隔离外部编码冲突
                    entity.setDeptCode(platform.name() + "-" + dept.getExternalDeptId());
                    entity.setDeptName(dept.getName());
                    entity.setDeptLevel(1);
                    entity.setSortOrder(dept.getOrder());
                    entity.setStatus("ACTIVE");
                    entity.setSyncSource(platform.name());
                    entity.setExternalId(dept.getExternalDeptId());
                    entity.setTenantId(tenantId);
                    return offDepartmentRepository.insert(entity).thenReturn(entity);
                }));
    }

    /**
     * 按同步来源重算部门镜像的父子层级、层级深度与层级路径。
     *
     * <p>外部平台分页返回部门，父部门可能晚于子部门写入，逐条 upsert 时无法解析父镜像。
     * 因此在本次同步收尾时用本次拉取到的全部外部部门做一次重算：子部门按外部 parentId 找到
     * 同来源父镜像的主键，根部门或父镜像缺失的部门视为根（parentId 为空、层级为 1）。
     * 只有层级字段实际变化的镜像才落库，重复执行不产生额外写。</p>
     *
     * @param platform           平台类型，对应镜像的同步来源
     * @param externalDepartments 本次同步拉取到的全部外部部门，按外部部门 ID 去重保留首个
     */
    private Mono<Void> rebuildDepartmentHierarchy(OfficePlatform platform, List<OfficeDepartment> externalDepartments) {
        Map<String, String> externalParentIds = new HashMap<>(externalDepartments.size());
        for (OfficeDepartment dept : externalDepartments) {
            if (dept.getExternalDeptId() != null) {
                externalParentIds.putIfAbsent(dept.getExternalDeptId(), dept.isRoot() ? null : dept.getParentId());
            }
        }
        if (externalParentIds.isEmpty()) {
            return Mono.empty();
        }
        return offDepartmentRepository
                .findBySyncSource(platform.name())
                .collectList()
                .flatMap(mirrors -> {
                    if (mirrors.isEmpty()) {
                        return Mono.empty();
                    }
                    Map<String, OffDepartmentEntity> byExternalId = new HashMap<>(mirrors.size());
                    Map<Long, OffDepartmentEntity> byId = new HashMap<>(mirrors.size());
                    for (OffDepartmentEntity mirror : mirrors) {
                        if (mirror.getExternalId() != null) {
                            byExternalId.putIfAbsent(mirror.getExternalId(), mirror);
                        }
                        if (mirror.getId() != null) {
                            byId.putIfAbsent(mirror.getId(), mirror);
                        }
                    }
                    List<OffDepartmentEntity> changed = new ArrayList<>();
                    for (String externalId : externalParentIds.keySet()) {
                        OffDepartmentEntity mirror = byExternalId.get(externalId);
                        if (mirror == null) {
                            continue;
                        }
                        Long parentId = resolveParentId(externalId, externalParentIds.get(externalId), byExternalId);
                        if (!applyHierarchy(mirror, parentId, byId)) {
                            continue;
                        }
                        changed.add(mirror);
                    }
                    return Flux.fromIterable(changed)
                            .concatMap(offDepartmentRepository::update)
                            .then();
                });
    }

    /**
     * 解析镜像的父部门主键。
     *
     * <p>外部父标识指向自身或父镜像缺失（含跨来源）时按根部门处理，避免脏数据形成自环。</p>
     *
     * @param externalId       当前部门的外部标识
     * @param externalParentId 外部父部门标识，根部门为 null
     * @param byExternalId     同来源镜像按外部部门标识的索引
     * @return 父部门主键；根部门或父镜像缺失时为 null
     */
    private static Long resolveParentId(
            String externalId, String externalParentId, Map<String, OffDepartmentEntity> byExternalId) {
        if (externalParentId == null || externalParentId.equals(externalId)) {
            return null;
        }
        OffDepartmentEntity parent = byExternalId.get(externalParentId);
        if (parent == null
                || parent.getId() == null
                || parent.getId().equals(byExternalId.get(externalId).getId())) {
            return null;
        }
        return parent.getId();
    }

    /**
     * 把父部门主键转换为层级深度与层级路径，仅在变化时写入镜像。
     *
     * <p>路径按 {@code /祖先主键/.../自身主键/} 生成，由已落库主键组成；父链出现环时截断，
     * 保证递归可终止。</p>
     *
     * @param mirror   待更新的部门镜像
     * @param parentId 父部门主键，根部门为 null
     * @param byId     同来源镜像按主键的索引
     * @return true-层级字段发生变化并已写入镜像；false-无变化
     */
    private static boolean applyHierarchy(
            OffDepartmentEntity mirror, Long parentId, Map<Long, OffDepartmentEntity> byId) {
        Set<Long> chain = new LinkedHashSet<>();
        Long cursor = parentId;
        while (cursor != null && chain.add(cursor)) {
            OffDepartmentEntity parent = byId.get(cursor);
            cursor = parent == null ? null : parent.getParentId();
        }
        List<Long> path = new ArrayList<>(chain);
        String deptPath = path.stream().map(id -> "/" + id).collect(Collectors.joining()) + "/" + mirror.getId() + "/";
        int deptLevel = path.size() + 1;
        boolean unchanged = Objects.equals(mirror.getParentId(), parentId)
                && Objects.equals(mirror.getDeptPath(), deptPath)
                && Objects.equals(mirror.getDeptLevel(), deptLevel);
        if (unchanged) {
            return false;
        }
        mirror.setParentId(parentId);
        mirror.setDeptPath(deptPath);
        mirror.setDeptLevel(deptLevel);
        return true;
    }

    /**
     * 保存成员映射，内部用户 ID 通过 off_employee 镜像表幂等 upsert 获取。
     */
    private Mono<OfficeOrganizationMapping> saveMemberMapping(
            String tenantId, OfficePlatform platform, OfficeMember member) {
        return officeRepository
                .findMemberMapping(tenantId, platform, member.getExternalUserId())
                .switchIfEmpty(Mono.defer(
                        () -> upsertEmployee(tenantId, platform, member).flatMap(entity -> {
                            OfficeOrganizationMapping mapping = OfficeOrganizationMapping.builder()
                                    .tenantId(tenantId)
                                    .platform(platform)
                                    .externalOrgId(null)
                                    .externalUserId(member.getExternalUserId())
                                    .apexDeptId(entity.getDeptId() == null ? null : String.valueOf(entity.getDeptId()))
                                    .apexUserId(entity.getUserId() == null ? null : String.valueOf(entity.getUserId()))
                                    .version(0)
                                    .deleted(0)
                                    .build();
                            return officeRepository.saveOrganizationMapping(mapping);
                        })));
    }

    /**
     * off_employee 镜像幂等 upsert：命中则更新最新资料/状态，否则新建并回填真实内部 ID。
     *
     * <p>成员归属部门取外部成员的主部门，按 (syncSource, 外部部门标识) 解析到部门镜像主键。
     * {@code off_employee.dept_id} 为必填列，主部门及备选部门都尚无镜像时跳过该成员并告警，
     * 由后续部门同步完成后的成员同步补齐，避免写入无归属的脏数据。</p>
     */
    private Mono<OffEmployeeEntity> upsertEmployee(String tenantId, OfficePlatform platform, OfficeMember member) {
        return resolveMemberDepartment(platform, member).flatMap(department -> {
            if (department == null) {
                log.warn("跳过成员镜像：外部成员 {} 的所属部门尚未同步，平台：{}，租户：{}", member.getExternalUserId(), platform, tenantId);
                return Mono.empty();
            }
            return offEmployeeRepository
                    .findByExternalId(platform.name(), member.getExternalUserId())
                    .next()
                    .flatMap(existing -> {
                        existing.setEmployeeName(member.getName());
                        existing.setMobile(member.getMobile());
                        existing.setEmail(member.getEmail());
                        existing.setDeptId(department.getId());
                        existing.setDeptName(department.getDeptName());
                        existing.setPosition(member.getPosition());
                        existing.setStatus("ACTIVE");
                        existing.setSyncSource(platform.name());
                        existing.setExternalId(member.getExternalUserId());
                        existing.setTenantId(tenantId);
                        return offEmployeeRepository.update(existing).thenReturn(existing);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        OffEmployeeEntity entity = new OffEmployeeEntity();
                        entity.setEmployeeCode(platform.name() + "-" + member.getExternalUserId());
                        entity.setEmployeeName(member.getName());
                        entity.setMobile(member.getMobile());
                        entity.setEmail(member.getEmail());
                        entity.setDeptId(department.getId());
                        entity.setDeptName(department.getDeptName());
                        entity.setPosition(member.getPosition());
                        entity.setStatus("ACTIVE");
                        entity.setSyncSource(platform.name());
                        entity.setExternalId(member.getExternalUserId());
                        entity.setTenantId(tenantId);
                        return offEmployeeRepository.insert(entity).thenReturn(entity);
                    }));
        });
    }

    /**
     * 解析成员归属的部门镜像。
     *
     * <p>优先使用外部成员的主部门，主部门未同步时按声明顺序尝试其余部门，
     * 全部未命中返回 empty，由调用方决定跳过。</p>
     */
    private Mono<OffDepartmentEntity> resolveMemberDepartment(OfficePlatform platform, OfficeMember member) {
        List<String> externalDeptIds = member.getDeptIds() == null ? List.of() : member.getDeptIds();
        if (externalDeptIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(externalDeptIds)
                .concatMap(externalDeptId -> offDepartmentRepository
                        .findByExternalId(platform.name(), externalDeptId)
                        .next())
                .next();
    }

    /**
     * 单批组织同步结果。
     *
     * @param syncedCount 本批同步成功的成员数量
     * @param nextCursor 下一页游标，{@code null} 表示已到末页
     */
    private record SyncBatchResult(int syncedCount, String nextCursor) {}

    /**
     * 同步预检结果
     *
     * @param ready 是否就绪
     * @param missingItems 缺失项描述列表
     */
    public record PreflightResult(boolean ready, List<String> missingItems) {}

    /**
     * 同步预览结果
     *
     * @param sampleCount 首页样本数量
     * @param hasMore 是否还有更多数据
     * @param mappedCount 样本中已存在映射的数量
     * @param newCount 样本中待新增的数量
     */
    public record SyncPreviewResult(int sampleCount, boolean hasMore, int mappedCount, int newCount) {}

    /**
     * 同步任务结果
     *
     * @param taskId 同步任务标识
     * @param syncedCount 本次同步成功的成员数量
     * @param message 结果说明
     * @param success 是否成功
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
