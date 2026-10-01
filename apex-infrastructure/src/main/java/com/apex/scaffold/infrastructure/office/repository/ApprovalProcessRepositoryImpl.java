package com.apex.scaffold.infrastructure.office.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.office.approval.ApprovalProcess;
import com.apex.scaffold.domain.office.approval.ApprovalProcessRepository;
import com.apex.scaffold.domain.office.approval.ApprovalStatus;
import com.apex.scaffold.infrastructure.office.converter.ApprovalProcessConverter;
import com.apex.scaffold.infrastructure.office.mapper.ApprovalProcessMapper;
import com.apex.scaffold.infrastructure.office.po.ApprovalProcessPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
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

    private final ApprovalProcessMapper mapper;
    private final ApprovalProcessConverter converter;
    private final BlockingIoSchedulers blockingIoSchedulers;

    @Override
    public Mono<ApprovalProcess> save(ApprovalProcess process) {
        return Mono.fromCallable(() -> {
                    ApprovalProcessPO po = converter.toPO(process);
                    mapper.insert(po);
                    return converter.toEntity(po);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(saved -> log.debug("审批流程已保存，ID：{}", saved.getId()));
    }

    @Override
    public Mono<ApprovalProcess> findById(Long id) {
        return Mono.fromCallable(() -> {
                    ApprovalProcessPO po = mapper.selectById(id);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("查询审批流程成功，ID：{}", id);
                    }
                });
    }

    @Override
    public Mono<ApprovalProcess> findByPlatformAndInstanceId(String platform, String platformInstanceId) {
        return Mono.fromCallable(() -> {
                    Long tenantId = TenantContext.currentTenantId()
                            .map(Long::valueOf)
                            .orElseThrow(() -> new IllegalStateException("租户上下文未设置"));
                    ApprovalProcessPO po = mapper.selectByPlatformInstanceId(platformInstanceId, tenantId);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("根据平台实例ID查询成功：{}", platformInstanceId);
                    }
                });
    }

    @Override
    public Mono<ApprovalProcess> findByProcessInstanceId(String processInstanceId) {
        return Mono.fromCallable(() -> {
                    Long tenantId = TenantContext.currentTenantId()
                            .map(Long::valueOf)
                            .orElseThrow(() -> new IllegalStateException("租户上下文未设置"));
                    ApprovalProcessPO po = mapper.selectByProcessInstanceId(processInstanceId, tenantId);
                    return po != null ? converter.toEntity(po) : null;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(found -> {
                    if (found != null) {
                        log.debug("根据流程实例ID查询成功：{}", processInstanceId);
                    }
                });
    }

    @Override
    public Mono<List<ApprovalProcess>> findByConditions(
            Long tenantId, String platform, ApprovalStatus status, int page, int size) {
        return Mono.fromCallable(() -> {
                    Page<ApprovalProcessPO> pageParam = new Page<>(page, size);
                    LambdaQueryWrapper<ApprovalProcessPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ApprovalProcessPO::getTenantId, tenantId);
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
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("条件查询审批流程完成，租户：{}，数量：{}", tenantId, list.size()));
    }

    @Override
    public Mono<Long> countByConditions(Long tenantId, String platform, ApprovalStatus status) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<ApprovalProcessPO> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(ApprovalProcessPO::getTenantId, tenantId);
                    if (platform != null) {
                        wrapper.eq(ApprovalProcessPO::getPlatform, platform);
                    }
                    if (status != null) {
                        wrapper.eq(ApprovalProcessPO::getStatus, status.name());
                    }
                    return mapper.selectCount(wrapper);
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(count -> log.debug("统计审批流程数量完成，租户：{}，数量：{}", tenantId, count));
    }

    @Override
    public Mono<List<ApprovalProcess>> findByInitiator(String initiatorId) {
        return Mono.fromCallable(() -> {
                    Long tenantId = TenantContext.currentTenantId()
                            .map(Long::valueOf)
                            .orElseThrow(() -> new IllegalStateException("租户上下文未设置"));
                    List<ApprovalProcessPO> poList = mapper.selectByInitiator(initiatorId, tenantId);
                    return poList.stream().map(converter::toEntity).toList();
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("查询发起人审批列表完成，发起人：{}，数量：{}", initiatorId, list.size()));
    }

    @Override
    public Mono<List<ApprovalProcess>> findPendingByApprover(String approverId) {
        return Mono.fromCallable(() -> {
                    Long tenantId = TenantContext.currentTenantId()
                            .map(Long::valueOf)
                            .orElseThrow(() -> new IllegalStateException("租户上下文未设置"));
                    List<ApprovalProcessPO> poList = mapper.selectPendingByApprover(approverId, tenantId);
                    return poList.stream().map(converter::toEntity).toList();
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(list -> log.debug("查询待审批列表完成，审批人：{}，数量：{}", approverId, list.size()));
    }

    @Override
    public Mono<ApprovalProcess> update(ApprovalProcess process) {
        return Mono.fromCallable(() -> {
                    ApprovalProcessPO po = converter.toPO(process);
                    mapper.updateById(po);
                    return converter.toEntity(mapper.selectById(po.getId()));
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(updated -> log.debug("审批流程已更新，ID：{}，状态：{}", updated.getId(), updated.getStatus()));
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return Mono.fromCallable(() -> {
                    int rows = mapper.deleteById(id);
                    return rows > 0;
                })
                .subscribeOn(blockingIoSchedulers.jdbc())
                .doOnSuccess(deleted -> log.debug("审批流程删除{}，ID：{}", deleted ? "成功" : "失败", id));
    }
}
