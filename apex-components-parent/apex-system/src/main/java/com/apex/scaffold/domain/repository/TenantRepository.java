package com.apex.scaffold.domain.repository;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 租户仓储接口。
 *
 * <p>定义租户聚合根的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 租户表为平台级元数据，不参与租户行级隔离，由平台管理员统一维护。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>按租户编码查询（编码全局唯一，作为 TenantContext 的租户 ID）</li>
 *   <li>租户编码存在性校验（创建租户时防重）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
public interface TenantRepository {

    /**
     * 根据 ID 查询租户。
     *
     * @param id 租户 ID
     * @return 租户实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<TenantEntity> findById(Long id);

    /**
     * 根据租户编码查询租户。
     *
     * @param tenantCode 租户编码
     * @return 租户实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<TenantEntity> findByCode(String tenantCode);

    /**
     * 查询全部租户（不含已归档，除非显式包含）。
     *
     * @return 租户实体列表
     */
    Mono<List<TenantEntity>> findAll();

    /**
     * 分页查询租户目录。
     *
     * <p>平台管理员的租户列表入口，支持按编码与名称模糊匹配、状态精确匹配，
     * 结果按租户编码升序排列。</p>
     *
     * @param tenantCode 租户编码（模糊匹配），可为 {@code null}
     * @param tenantName 租户名称（模糊匹配），可为 {@code null}
     * @param status 租户状态（精确匹配），可为 {@code null}
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数
     * @return 租户分页结果
     */
    Mono<PageResponse<TenantEntity>> search(
            String tenantCode, String tenantName, String status, int pageNum, int pageSize);

    /**
     * 检查租户编码是否已存在。
     *
     * @param tenantCode 租户编码
     * @param excludeId 需要排除的租户 ID（更新场景），可为 {@code null}
     * @return true-已存在，false-不存在
     */
    Mono<Boolean> existsByCode(String tenantCode, Long excludeId);

    /**
     * 新增租户。
     *
     * @param entity 租户实体
     * @return 影响行数
     */
    Mono<Integer> insert(TenantEntity entity);

    /**
     * 更新租户。
     *
     * @param entity 租户实体
     * @return 影响行数
     */
    Mono<Integer> updateById(TenantEntity entity);

    /**
     * 删除租户（逻辑删除）。
     *
     * @param id 租户 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
