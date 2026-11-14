package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.ReportEntity;
import reactor.core.publisher.Mono;

/**
 * 举报仓储接口
 *
 * <p>举报领域的仓储接口，定义举报相关的持久化操作方法
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ReportRepository {
    /**
     * 根据ID查询举报信息
     *
     * @param id 举报ID
     * @return 举报信息，若不存在则返回Mono.empty()
     */
    Mono<ReportEntity> findById(Long id);

    /**
     * 根据用户ID和举报类型查询最新的举报记录
     *
     * @param userId 用户ID
     * @param reportType 举报类型
     * @return 最新的举报记录，若不存在则返回Mono.empty()
     */
    Mono<ReportEntity> findLatestByUserAndType(Long userId, String reportType);

    /**
     * 保存举报信息
     *
     * <p>如果举报ID为空，则执行创建操作；否则执行更新操作
     *
     * @param entity 举报信息
     * @return 保存后的举报信息
     */
    Mono<ReportEntity> save(ReportEntity entity);
}
