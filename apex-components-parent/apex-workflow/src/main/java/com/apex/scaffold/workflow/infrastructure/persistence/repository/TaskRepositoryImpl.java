package com.apex.scaffold.workflow.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.converter.TaskPoConverter;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 使用显式租户 SQL 条件的任务仓储。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class TaskRepositoryImpl implements TaskRepository {
    private final TaskMapper taskMapper;
    private final TaskPoConverter taskPoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<Task> findById(Long id) {
        return WorkflowTenantAccess.execute(tenant -> findByIdSync(tenant, id));
    }

    /**
     * 同步按主键查询任务，供响应式边界与事务回调复用。
     *
     * <p>查询条件同时限定主键与租户，避免跨租户读取。
     *
     * @param tenant 租户标识，由受信任上下文传入
     * @param id 任务主键
     * @return 任务实体；不存在时返回 {@code null}
     */
    public Task findByIdSync(String tenant, Long id) {
        return taskPoConverter.toEntity(taskMapper.selectOne(
                ConditionUtil.lambda(TaskPO.class).eq(TaskPO::getId, id).eq(TaskPO::getTenantId, tenant)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<Task>> findByProcessInstanceId(Long id) {
        return WorkflowTenantAccess.execute(
                tenant -> taskPoConverter.toEntityList(taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getTenantId, tenant)
                        .eq(TaskPO::getProcessInstanceId, id)
                        .orderByDesc(TaskPO::getCreateTime))));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<List<Task>> findByAssignee(Long id) {
        return WorkflowTenantAccess.execute(
                tenant -> taskPoConverter.toEntityList(taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                        .eq(TaskPO::getTenantId, tenant)
                        .eq(TaskPO::getAssigneeId, id)
                        .eq(TaskPO::getStatus, "PENDING")
                        .orderByDesc(TaskPO::getCreateTime))));
    }

    /** {@inheritDoc} */
    @Override
    public Flux<Task> findPendingTasks() {
        return WorkflowTenantAccess.execute(
                        tenant -> taskPoConverter.toEntityList(taskMapper.selectList(ConditionUtil.lambda(TaskPO.class)
                                .eq(TaskPO::getTenantId, tenant)
                                .eq(TaskPO::getStatus, "PENDING")
                                .isNotNull(TaskPO::getTimeoutAt)
                                .orderByAsc(TaskPO::getTimeoutAt))))
                .flatMapMany(Flux::fromIterable);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> insert(Task entity) {
        return WorkflowTenantAccess.execute(tenant -> insertSync(tenant, entity));
    }

    /**
     * 同步新增任务，复用相同租户与默认字段规则。
     *
     * <p>写入时补齐租户、创建/更新时间与初始版本号，并把数据库生成的主键回填到实体。
     *
     * @param tenant 租户标识，写入 {@code tenant_id} 列
     * @param entity 待新增的任务；调用后其 {@code id} 被回填
     * @return 受影响行数
     */
    public int insertSync(String tenant, Task entity) {
        TaskPO po = taskPoConverter.toPo(entity);
        po.setTenantId(tenant);
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        po.setCreateTime(po.getCreateTime() == null ? now : po.getCreateTime());
        po.setUpdateTime(po.getUpdateTime() == null ? now : po.getUpdateTime());
        po.setVersion(po.getVersion() == null ? 0 : po.getVersion());
        int inserted = taskMapper.insert(po);
        entity.setId(po.getId());
        return inserted;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> updateById(Task entity) {
        return WorkflowTenantAccess.execute(tenant -> updateByIdSync(tenant, entity));
    }

    /**
     * 同步按主键条件更新任务，事务内仍保留完整 CAS 保护。
     *
     * @param tenant 租户标识，作为更新条件的一部分
     * @param entity 待更新的任务，需携带主键与当前版本号
     * @return 受影响行数；版本号缺失或 CAS 失败时返回 {@code 0}
     */
    public int updateByIdSync(String tenant, Task entity) {
        return updateTaskSync(tenant, entity, null);
    }

    /**
     * 同步超时办理更新，追加扫描时快照条件。
     *
     * <p>除主键与版本外，还比较快照中的状态、处理人与超时配置，避免重读后到写入前
     * 发生配置或办理人变化。
     *
     * @param tenant 租户标识，作为更新条件的一部分
     * @param entity 更新后的任务
     * @param expected 扫描时读取的任务快照，用于附加 CAS 条件
     * @return 受影响行数；快照不匹配时返回 {@code 0}
     */
    public int updateTimeoutByIdSync(String tenant, Task entity, Task expected) {
        return updateTaskSync(tenant, entity, expected);
    }

    private int updateTaskSync(String tenant, Task entity, Task expected) {
        if (entity.getVersion() == null) {
            return 0;
        }
        TaskPO po = taskPoConverter.toPo(entity);
        po.setTenantId(null);
        po.setVersion(entity.getVersion() + 1);
        po.setUpdateTime(java.time.LocalDateTime.now());
        var condition = ConditionUtil.lambda(TaskPO.class)
                .eq(TaskPO::getId, entity.getId())
                .eq(TaskPO::getTenantId, tenant)
                .eq(TaskPO::getStatus, "PENDING")
                .eq(TaskPO::getVersion, entity.getVersion());
        if (expected != null) {
            // 扫描快照条件：重读到写入之间若办理人/截止时间/策略被改动，则不再按旧策略处置。
            condition
                    .eq(TaskPO::getAssigneeId, expected.getAssigneeId())
                    .eq(TaskPO::getTimeoutAt, expected.getTimeoutAt())
                    .eq(TaskPO::getTimeoutStrategy, expected.getTimeoutStrategy());
        }
        return taskMapper.update(po, condition);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return WorkflowTenantAccess.execute(tenant -> taskMapper.delete(
                ConditionUtil.lambda(TaskPO.class).eq(TaskPO::getId, id).eq(TaskPO::getTenantId, tenant)));
    }
}
