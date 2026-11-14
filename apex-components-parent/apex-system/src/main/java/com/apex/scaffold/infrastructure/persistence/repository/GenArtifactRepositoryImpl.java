package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenArtifactEntity;
import com.apex.scaffold.domain.repository.GenArtifactRepository;
import com.apex.scaffold.infrastructure.persistence.converter.GenArtifactPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.GenArtifactMapper;
import com.apex.scaffold.infrastructure.persistence.po.GenArtifactPO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link GenArtifactRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>批量写入放在 jdbc 调度器执行的单次回调内，避免逐条往返；查询按任务 ID 定位，
 * 租户过滤由持久化拦截器统一注入。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenArtifactRepository
 */
@Repository
@RequiredArgsConstructor
public class GenArtifactRepositoryImpl implements GenArtifactRepository {

    private final GenArtifactMapper artifactMapper;

    private final GenArtifactPoConverter artifactPoConverter;

    /**
     * 批量新增产物。
     *
     * <p>插入前由 MyBatis-Plus 分配雪花主键，逐条写回实体，
     * 使调用方能直接返回带产物 ID 的下载入口。
     *
     * @param entities 产物实体列表
     * @return 已回写主键的产物实体列表
     */
    @Override
    public Mono<List<GenArtifactEntity>> insertBatch(List<GenArtifactEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Mono.just(List.of());
        }
        return Mono.fromCallable(() -> {
                    for (GenArtifactEntity entity : entities) {
                        GenArtifactPO po = artifactPoConverter.toPo(entity);
                        artifactMapper.insert(po);
                        entity.setId(po.getId());
                    }
                    return entities;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据 ID 查询产物。
     *
     * @param id 产物 ID
     * @return 产物实体，不存在时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<GenArtifactEntity> findById(Long id) {
        return Mono.fromCallable(() -> artifactMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(artifactPoConverter.toEntity(po)));
    }

    /**
     * 按任务 ID 查询产物。
     *
     * @param taskId 生成任务 ID
     * @return 产物列表
     */
    @Override
    public Mono<List<GenArtifactEntity>> findByTaskId(Long taskId) {
        return Mono.fromCallable(() -> artifactMapper.selectList(ConditionUtil.lambda(GenArtifactPO.class)
                        .eq(GenArtifactPO::getTaskId, taskId)
                        .orderByAsc(GenArtifactPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(artifactPoConverter::toEntityList);
    }
}
