package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.generator.GenArtifactEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 代码生成产物仓储接口
 *
 * <p>定义向导执行产物与生成历史的分页查询契约，由基础设施层用 MyBatis-Plus 实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenArtifactEntity
 */
public interface GenArtifactRepository {

    /**
     * 批量新增产物。
     *
     * @param entities 产物实体列表
     * @return 已回写主键的产物实体列表
     */
    Mono<List<GenArtifactEntity>> insertBatch(List<GenArtifactEntity> entities);

    /**
     * 根据 ID 查询产物。
     *
     * @param id 产物 ID
     * @return 产物实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<GenArtifactEntity> findById(Long id);

    /**
     * 按任务 ID 查询产物。
     *
     * @param taskId 生成任务 ID
     * @return 产物列表
     */
    Mono<List<GenArtifactEntity>> findByTaskId(Long taskId);
}
