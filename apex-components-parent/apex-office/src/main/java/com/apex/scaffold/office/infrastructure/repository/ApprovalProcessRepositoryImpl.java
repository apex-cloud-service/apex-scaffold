package com.apex.scaffold.office.infrastructure.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.office.domain.approval.ApprovalAction;
import com.apex.scaffold.office.domain.approval.ApprovalProcess;
import com.apex.scaffold.office.domain.approval.ApprovalProcessRepository;
import com.apex.scaffold.office.domain.approval.ApprovalStatus;
import com.apex.scaffold.office.infrastructure.converter.ApprovalProcessConverter;
import com.apex.scaffold.office.infrastructure.mapper.ApprovalProcessMapper;
import com.apex.scaffold.office.infrastructure.po.ApprovalProcessPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 审批流程仓储实现
 *
 * <p>实现审批流程的持久化操作，使用MyBatis-Plus进行数据库访问。
 * 所有阻塞I/O操作通过{@link BlockingIoSchedulers}隔离到专用线程池。
 *
 * <p>关键实现：
 * <ul>
 *   <li>租户隔离：所有查询自动添加租户ID条件</li>
 *   <li>阻塞隔离：JDBC操作在blocking-io-scheduler执行</li>
 *   <li>对象转换：使用MapStruct在实体和PO之间转换</li>
 *   <li>乐观锁：更新操作使用version字段防止并发冲突</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ApprovalProcessRepositoryImpl implements ApprovalProcessRepository {

    /** {@code current_approver_ids} 的 JSON 字符串数组反序列化引用（静态常量避免每次重建）。 */
    private static final com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>> LIST_OF_STRING =
            new com.fasterxml.jackson.core.type.TypeReference<>() {};

    /** 共享 JSON 解析器（仓储层方言规避解析用）。 */
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private final ApprovalProcessMapper mapper;
    private final ApprovalProcessConverter converter;
    private final BlockingIoSchedulers blockingIoSchedulers;

    /**
     * 在 JDBC callable 内安装可信租户，完成或异常时恢复原线程上下文。
     *
     * @param operation 阻塞操作
     * @param <T> 结果类型
     * @return 租户范围内的操作结果；无租户时拒绝
     */
    private <T> Mono<T> withTenantScoped(Function<Long, T> operation) {
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new IllegalStateException("租户上下文未设置")))
                .flatMap(trusted -> {
                    Long tenantId = Long.valueOf(trusted);
                    return Mono.fromCallable(
                                    () -> TenantContext.callWithTenant(trusted, () -> operation.apply(tenantId)))
                            .subscribeOn(blockingIoSchedulers.jdbc());
                });
    }

    /**
     * 校验显式租户与可信上下文一致后执行 JDBC 操作。
     *
     * @param expectedTenant 显式租户
     * @param operation 阻塞操作
     * @param <T> 结果类型
     * @return 租户范围内的操作结果
     */
    private <T> Mono<T> withTenantScoped(Long expectedTenant, Function<Long, T> operation) {
        return withTenantScoped(tenantId -> {
            if (!Objects.equals(expectedTenant, tenantId)) {
                throw new IllegalStateException("审批流程不属于当前租户");
            }
            return operation.apply(tenantId);
        });
    }

    @Override
    public Mono<ApprovalProcess> save(ApprovalProcess process) {
        return Mono.defer(() -> withTenantScoped(process.getTenantId(), tenantId -> {
                    ApprovalProcessPO po = converter.toPO(process);
                    mapper.insert(po);
                    return converter.toEntity(po);
                }))
                .doOnSuccess(saved -> log.debug("审批流程已保存，ID：{}", saved.getId()));
    }

    @Override
    public Mono<ApprovalProcess> findById(Long id) {
        return withTenantScoped(tenantId -> {
                    QueryWrapper<ApprovalProcessPO> wrapper = new QueryWrapper<>();
                    wrapper.eq("tenant_id", tenantId).eq("id", id);
                    ApprovalProcessPO po = mapper.selectOne(wrapper);
                    return po != null ? converter.toEntity(po) : null;
                })
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("查询审批流程成功，ID：{}", id);
                    }
                });
    }

    @Override
    public Mono<ApprovalProcess> findByPlatformAndInstanceId(String platform, String platformInstanceId) {
        return withTenantScoped(tenantId -> {
                    ApprovalProcessPO po = mapper.selectByPlatformInstanceId(platformInstanceId, tenantId);
                    return po != null ? converter.toEntity(po) : null;
                })
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("根据平台实例ID查询成功：{}", platformInstanceId);
                    }
                });
    }

    @Override
    public Mono<ApprovalProcess> findByProcessInstanceId(String processInstanceId) {
        return withTenantScoped(tenantId -> {
                    ApprovalProcessPO po = mapper.selectByProcessInstanceId(processInstanceId, tenantId);
                    return po != null ? converter.toEntity(po) : null;
                })
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("查询审批流程成功，ID：{}", found.getId());
                    }
                });
    }

    @Override
    public Mono<List<ApprovalProcess>> findByConditions(
            Long tenantId, String platform, ApprovalStatus status, int page, int size) {
        return withTenantScoped(tenantId, scopedTenant -> {
                    Page<ApprovalProcessPO> pageParam = new Page<>(page, size);
                    LambdaQueryWrapper<ApprovalProcessPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ApprovalProcessPO::getTenantId, scopedTenant);
                    if (platform != null) {
                        wrapper.eq(ApprovalProcessPO::getPlatform, platform);
                    }
                    if (status != null) {
                        wrapper.eq(ApprovalProcessPO::getStatus, status.name());
                    }
                    wrapper.orderByDesc(ApprovalProcessPO::getCreatedAt);
                    Page<ApprovalProcessPO> result = mapper.selectPage(pageParam, wrapper);
                    return result.getRecords().stream().map(converter::toEntity).toList();
                })
                .doOnSuccess(list -> log.debug("条件查询审批流程完成，租户：{}，数量：{}", tenantId, list.size()));
    }

    @Override
    public Mono<Long> countByConditions(Long tenantId, String platform, ApprovalStatus status) {
        return withTenantScoped(tenantId, scopedTenant -> {
                    LambdaQueryWrapper<ApprovalProcessPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ApprovalProcessPO::getTenantId, scopedTenant);
                    if (platform != null) {
                        wrapper.eq(ApprovalProcessPO::getPlatform, platform);
                    }
                    if (status != null) {
                        wrapper.eq(ApprovalProcessPO::getStatus, status.name());
                    }
                    return mapper.selectCount(wrapper);
                })
                .doOnSuccess(count -> log.debug("统计审批流程数量完成，租户：{}，数量：{}", tenantId, count));
    }

    @Override
    public Mono<List<ApprovalProcess>> findByInitiator(String initiatorId) {
        return withTenantScoped(tenantId -> {
                    List<ApprovalProcessPO> poList = mapper.selectByInitiator(initiatorId, tenantId);
                    return poList.stream().map(converter::toEntity).toList();
                })
                .doOnSuccess(list -> log.debug("查询发起人审批列表完成，发起人：{}，数量：{}", initiatorId, list.size()));
    }

    @Override
    public Mono<List<ApprovalProcess>> findPendingByApprover(String approverId) {
        return withTenantScoped(tenantId -> {
                    // 审批人归属在仓储层解析 JSON 数组：JSON_CONTAINS 为 MySQL 专有函数，
                    // H2 basic 与 PostgreSQL 不支持（与 selectAllByTenant 的方言规避口径一致）
                    List<ApprovalProcessPO> poList = mapper.selectPendingByTenant(tenantId).stream()
                            .filter(po -> containsApprover(po.getCurrentApproverIds(), approverId))
                            .toList();
                    return poList.stream().map(converter::toEntity).toList();
                })
                .doOnSuccess(list -> log.debug("查询待审批列表完成，审批人：{}，数量：{}", approverId, list.size()));
    }

    /**
     * 判断审批人 ID 是否在 {@code current_approver_ids} JSON 字符串数组中。
     *
     * <p>字段为空、非合法 JSON 数组或元素类型不符时返回 false，不抛出：该字段由
     * 办公平台侧写入，本地发起阶段可能为空数组或占位文本。
     *
     * @param currentApproverIds JSON 数组字符串（元素为审批人 ID）
     * @param approverId         待匹配的审批人 ID
     * @return 数组包含该 ID 时返回 true
     */
    private boolean containsApprover(String currentApproverIds, String approverId) {
        if (currentApproverIds == null || currentApproverIds.isBlank()) {
            return false;
        }
        try {
            return OBJECT_MAPPER.readValue(currentApproverIds, LIST_OF_STRING).contains(approverId);
        } catch (Exception parseFailure) {
            return false;
        }
    }

    @Override
    public Mono<List<ApprovalProcess>> findCompletedByApprover(String approverId) {
        return withTenantScoped(tenantId -> {
                    List<ApprovalProcessPO> poList = mapper.selectAllByTenant(tenantId);
                    return poList.stream()
                            .map(converter::toEntity)
                            .filter(process -> process.getRecords() != null
                                    && process.getRecords().stream()
                                            .anyMatch(record -> approverId.equals(record.getApproverId())
                                                    && (record.getAction() == ApprovalAction.APPROVE
                                                            || record.getAction() == ApprovalAction.REJECT)))
                            .toList();
                })
                .doOnSuccess(list -> log.debug("查询已办审批列表完成，审批人：{}，数量：{}", approverId, list.size()));
    }

    @Override
    public Mono<ApprovalProcess> update(ApprovalProcess process) {
        return Mono.defer(() -> withTenantScoped(process.getTenantId(), tenantId -> {
                    ApprovalProcessPO po = converter.toPO(process);
                    QueryWrapper<ApprovalProcessPO> wrapper = new QueryWrapper<>();
                    wrapper.eq("tenant_id", tenantId).eq("id", po.getId());
                    mapper.update(po, wrapper);
                    QueryWrapper<ApprovalProcessPO> query = new QueryWrapper<>();
                    query.eq("tenant_id", tenantId).eq("id", po.getId());
                    return converter.toEntity(mapper.selectOne(query));
                }))
                .doOnSuccess(updated -> log.debug("审批流程已更新，ID：{}，状态：{}", updated.getId(), updated.getStatus()));
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return withTenantScoped(tenantId -> {
                    QueryWrapper<ApprovalProcessPO> wrapper = new QueryWrapper<>();
                    wrapper.eq("tenant_id", tenantId).eq("id", id);
                    int rows = mapper.delete(wrapper);
                    return rows > 0;
                })
                .doOnSuccess(deleted -> log.debug("审批流程删除{}，ID：{}", deleted ? "成功" : "失败", id));
    }
}
