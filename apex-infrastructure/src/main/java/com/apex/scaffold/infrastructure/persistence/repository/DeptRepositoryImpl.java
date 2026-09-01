package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import com.apex.scaffold.domain.repository.DeptRepository;
import com.apex.scaffold.infrastructure.persistence.mapper.DeptMapper;
import com.apex.scaffold.infrastructure.persistence.po.DeptPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 部门仓储实现（MyBatis-Plus）。
 *
 * <p>实现 {@link DeptRepository} 契约，PO/Entity 手工转换，
 * JDBC 阻塞调用隔离到 boundedElastic 调度器。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@Repository
public class DeptRepositoryImpl implements DeptRepository {

    /** 部门 Mapper */
    private final DeptMapper deptMapper;

    /**
     * 构造部门仓储实现。
     *
     * @param deptMapper 部门 Mapper
     */
    public DeptRepositoryImpl(DeptMapper deptMapper) {
        this.deptMapper = deptMapper;
    }

    /** 查询全部未删除部门（按显示顺序升序）。 */
    @Override
    public Mono<List<DeptEntity>> findAll() {
        return Mono.fromCallable(() ->
                        deptMapper
                                .selectList(ConditionUtil.lambda(DeptPO.class).orderByAsc(DeptPO::getOrderNum))
                                .stream()
                                .map(this::toEntity)
                                .collect(Collectors.toList()))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** 根据 ID 查询部门。 */
    @Override
    public Mono<DeptEntity> findById(Long id) {
        return Mono.fromCallable(() -> deptMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    /** 保存部门（新增或更新）。 */
    @Override
    public Mono<DeptEntity> save(DeptEntity dept) {
        return Mono.fromCallable(() -> {
                    DeptPO po = toPo(dept);
                    TenantContext.currentTenantId().ifPresent(po::setTenantId);
                    if (po.getId() == null) {
                        if (deptMapper.insert(po) != 1) {
                            throw new IllegalStateException("创建部门失败");
                        }
                    } else if (deptMapper.updateById(po) != 1) {
                        throw new OptimisticLockingFailureException("部门并发更新冲突, deptId=" + po.getId());
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(this::toEntity);
    }

    /** 逻辑删除部门。 */
    @Override
    public Mono<Boolean> deleteById(Long id) {
        return Mono.fromCallable(() -> deptMapper.deleteById(id) > 0).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** PO → 实体转换。 */
    private DeptEntity toEntity(DeptPO po) {
        if (po == null) {
            return null;
        }
        DeptEntity e = new DeptEntity();
        e.setId(po.getId());
        e.setTenantId(po.getTenantId());
        e.setParentId(po.getParentId());
        e.setAncestors(po.getAncestors());
        e.setName(po.getName());
        e.setOrderNum(po.getOrderNum());
        e.setLeader(po.getLeader());
        e.setPhone(po.getPhone());
        e.setEmail(po.getEmail());
        e.setStatus(po.getStatus());
        e.setCreateTime(po.getCreateTime());
        e.setUpdateTime(po.getUpdateTime());
        e.setLockVersion(po.getVersion());
        e.setDeleted(po.getDeleted());
        return e;
    }

    /** 实体 → PO 转换。 */
    private DeptPO toPo(DeptEntity e) {
        DeptPO po = new DeptPO();
        po.setId(e.getId());
        po.setTenantId(e.getTenantId());
        po.setParentId(e.getParentId());
        po.setAncestors(e.getAncestors());
        po.setName(e.getName());
        po.setOrderNum(e.getOrderNum());
        po.setLeader(e.getLeader());
        po.setPhone(e.getPhone());
        po.setEmail(e.getEmail());
        po.setStatus(e.getStatus());
        po.setCreateTime(e.getCreateTime());
        po.setUpdateTime(e.getUpdateTime());
        po.setVersion(e.getLockVersion());
        po.setDeleted(e.getDeleted());
        return po;
    }
}
