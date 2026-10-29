package com.apex.scaffold.file.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.domain.model.entity.DocumentEntity;
import com.apex.scaffold.file.domain.repository.DocumentRepository;
import com.apex.scaffold.file.infrastructure.persistence.converter.DocumentPoConverter;
import com.apex.scaffold.file.infrastructure.persistence.mapper.DocumentMapper;
import com.apex.scaffold.file.infrastructure.persistence.po.DocumentPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文档仓储实现类。
 *
 * <p>实现 {@link DocumentRepository} 接口，提供文档领域实体的持久化能力。
 * 基于 MyBatis-Plus 访问数据库，所有 JDBC 操作运行在 {@link BlockingIoSchedulers#jdbc()}
 * 专用调度器上，避免阻塞响应式主线程。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>响应式封装：JDBC 阻塞操作通过 {@code Mono.fromCallable} 封装并在专用调度器执行</li>
 *   <li>多租户隔离：自动从 {@link TenantContext} 注入租户 ID，查询时自动过滤</li>
 *   <li>乐观锁：更新操作使用 MyBatis-Plus 乐观锁，冲突时抛出 {@link OptimisticLockingFailureException}</li>
 *   <li>实体转换：通过 {@link DocumentPoConverter} 实现领域实体与持久化对象的转换</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>仓储模式：实现领域仓储接口，封装数据访问细节</li>
 *   <li>适配器模式：适配 MyBatis-Plus 提供的数据库访问能力</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DocumentRepository
 * @see DocumentMapper
 * @see DocumentPoConverter
 */
@Repository
@RequiredArgsConstructor
public class DocumentRepositoryImpl implements DocumentRepository {

    private final DocumentMapper documentMapper;
    private final DocumentPoConverter converter;

    @Override
    public Mono<DocumentEntity> save(DocumentEntity entity) {
        return Mono.fromCallable(() -> {
                    DocumentPO po = converter.toPo(entity);
                    TenantContext.currentTenantId().ifPresent(po::setTenantId);

                    if (po.getId() == null) {
                        if (documentMapper.insert(po) != 1) {
                            throw new IllegalStateException("创建文档失败");
                        }
                        entity.setId(po.getId());
                    } else {
                        if (documentMapper.updateById(po) != 1) {
                            throw new OptimisticLockingFailureException("文档并发更新冲突, id=" + po.getId());
                        }
                    }
                    return entity;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<DocumentEntity> findById(Long id) {
        return Mono.fromCallable(() -> documentMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(converter::toEntity);
    }

    @Override
    public Flux<DocumentEntity> findAll(String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<DocumentPO> wrapper = ConditionUtil.lambda(DocumentPO.class);
                    wrapper.eq(DocumentPO::getTenantId, tenantId);
                    wrapper.orderByDesc(DocumentPO::getCreateTime);

                    return documentMapper.selectList(wrapper).stream()
                            .map(converter::toEntity)
                            .collect(Collectors.toList());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Void> deleteById(Long id, String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<DocumentPO> wrapper = ConditionUtil.lambda(DocumentPO.class);
                    wrapper.eq(DocumentPO::getId, id);
                    wrapper.eq(DocumentPO::getTenantId, tenantId);

                    return documentMapper.delete(wrapper);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    @Override
    public Mono<Long> count(String tenantId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<DocumentPO> wrapper = ConditionUtil.lambda(DocumentPO.class);
                    wrapper.eq(DocumentPO::getTenantId, tenantId);
                    return documentMapper.selectCount(wrapper);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
