package com.apex.scaffold.knowledge.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.knowledge.infrastructure.persistence.mapper.KnowledgeSpaceMapper;
import com.apex.scaffold.knowledge.infrastructure.persistence.po.KnowledgeSpacePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 知识空间仓储实现：MyBatis-Plus 访问统一隔离到 jdbc 调度器。
 *
 * <p>写入前从 {@link TenantContext} 显式装配租户（拦截器关闭时仍正确落库）；
 * 开启拦截器时若上下文缺失，SQL 会被 {@code DefaultTenantLineHandler} 拒绝（fail-closed）。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeSpaceRepositoryImpl implements KnowledgeSpaceRepository {

    private final KnowledgeSpaceMapper mapper;

    @Override
    public Mono<KnowledgeSpace> insert(KnowledgeSpace space) {
        return Mono.fromCallable(() -> {
                    KnowledgeSpacePO po = KnowledgeSpacePO.builder()
                            .name(space.getName())
                            .description(space.getDescription())
                            .status(space.getStatus())
                            .tenantId(TenantContext.currentTenantId().orElse(null))
                            .build();
                    mapper.insert(po);
                    return toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<KnowledgeSpace> update(KnowledgeSpace space) {
        return Mono.fromCallable(() -> {
                    KnowledgeSpacePO po = mapper.selectById(space.getId());
                    if (po == null) {
                        throw new IllegalStateException("知识空间不存在: " + space.getId());
                    }
                    po.setName(space.getName());
                    po.setDescription(space.getDescription());
                    po.setStatus(space.getStatus());
                    mapper.updateById(po);
                    return toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<KnowledgeSpace> findById(Long id) {
        return Mono.fromCallable(() -> {
                    KnowledgeSpacePO po = mapper.selectById(id);
                    return po == null ? null : toModel(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<PageResponse<KnowledgeSpace>> findPage(String keyword, int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<KnowledgeSpacePO> wrapper = new LambdaQueryWrapper<KnowledgeSpacePO>()
                            .like(keyword != null && !keyword.isBlank(), KnowledgeSpacePO::getName, keyword)
                            .orderByDesc(KnowledgeSpacePO::getId);
                    Page<KnowledgeSpacePO> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
                    List<KnowledgeSpace> items =
                            page.getRecords().stream().map(this::toModel).toList();
                    return PageResponse.of(items, page.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<List<KnowledgeSpace>> findAllActive() {
        return Mono.fromCallable(() -> mapper
                        .selectList(new LambdaQueryWrapper<KnowledgeSpacePO>()
                                .eq(KnowledgeSpacePO::getStatus, 1)
                                .orderByDesc(KnowledgeSpacePO::getId))
                        .stream()
                        .map(this::toModel)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> deleteById(Long id) {
        return Mono.fromCallable(() -> mapper.deleteById(id) > 0).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    @Override
    public Mono<Boolean> existsByName(String name, Long excludeId) {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<KnowledgeSpacePO> wrapper = new LambdaQueryWrapper<KnowledgeSpacePO>()
                            .eq(KnowledgeSpacePO::getName, name)
                            .ne(excludeId != null, KnowledgeSpacePO::getId, excludeId);
                    return mapper.selectCount(wrapper) > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    private KnowledgeSpace toModel(KnowledgeSpacePO po) {
        return KnowledgeSpace.builder()
                .id(po.getId())
                .name(po.getName())
                .description(po.getDescription())
                .status(po.getStatus())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
