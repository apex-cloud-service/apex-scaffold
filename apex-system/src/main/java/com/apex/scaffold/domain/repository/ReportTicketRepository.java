package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.ReportTicketEntity;
import reactor.core.publisher.Mono;

/**
 * 举报工单仓储接口（领域层）
 *
 * <p>定义举报工单的持久化操作规范，由基础设施层的
 * {@code ReportTicketRepositoryImpl} 提供具体实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ReportTicketRepository {
    /**
     * 创建举报工单。
     *
     * <p>将举报工单实体持久化到数据库，自动设置创建/更新时间戳，
     * 操作完成后返回数据库中最新状态的实体。
     *
     * @param entity 举报工单实体
     * @return 保存后最新状态的举报工单实体
     */
    Mono<ReportTicketEntity> save(ReportTicketEntity entity);
}
