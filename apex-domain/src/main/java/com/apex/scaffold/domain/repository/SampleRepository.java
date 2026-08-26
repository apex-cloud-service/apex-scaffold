package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.SampleEntity;
import reactor.core.publisher.Mono;

/**
 * 样本仓储接口（领域层）
 *
 * <p>定义用户照片样本（Sample）的持久化操作规范，
 * 由基础设施层的 {@code SampleRepositoryImpl} 提供具体实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface SampleRepository {
    /**
     * 按ID查询样本。
     *
     * <p>根据主键ID查询单个样本，若不存在则返回空信号。
     *
     * @param id 样本ID
     * @return 样本实体的 Mono 包装，可能为空
     */
    Mono<SampleEntity> findById(Long id);

    /**
     * 创建或更新样本。
     *
     * <p>若实体未分配 id 则执行插入，否则按 id 更新已有记录，
     * 操作完成后返回数据库中最新状态的实体。
     *
     * @param entity 样本实体
     * @return 保存后最新状态的样本实体
     */
    Mono<SampleEntity> save(SampleEntity entity);
}
