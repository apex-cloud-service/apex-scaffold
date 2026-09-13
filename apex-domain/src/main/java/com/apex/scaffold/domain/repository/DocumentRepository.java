package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.DocumentEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文档仓储接口。
 *
 * <p>定义文档领域实体的持久化操作契约，遵循 DDD 仓储模式，
 * 由基础设施层（infrastructure）提供具体实现。所有方法均为响应式，
 * 基于 Project Reactor 的 Mono/Flux 返回类型。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>文档实体的创建与更新（save）</li>
 *   <li>按 ID 查询文档实体（findById）</li>
 *   <li>按租户查询所有文档（findAll）</li>
 *   <li>按 ID 删除文档（deleteById）</li>
 *   <li>按租户统计文档数量（count）</li>
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>仓储接口定义在领域层，实现在基础设施层，保持领域层对基础设施的独立性</li>
 *   <li>所有操作支持多租户隔离，通过 tenantId 参数实现租户数据隔离</li>
 *   <li>使用响应式编程模型，避免阻塞 I/O</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DocumentEntity
 */
public interface DocumentRepository {

    Mono<DocumentEntity> save(DocumentEntity entity);

    Mono<DocumentEntity> findById(Long id);

    Flux<DocumentEntity> findAll(String tenantId);

    Mono<Void> deleteById(Long id, String tenantId);

    Mono<Long> count(String tenantId);
}
