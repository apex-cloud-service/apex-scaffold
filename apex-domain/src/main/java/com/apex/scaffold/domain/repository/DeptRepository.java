package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.DeptEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 部门仓储接口。
 *
 * <p>定义部门实体的持久化契约，由基础设施层实现（MyBatis-Plus）。
 * 树形查询按 {@code parentId} 全量拉取后在应用层组装。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public interface DeptRepository {

    /**
     * 查询全部未删除部门（按显示顺序升序）。
     *
     * @return 部门实体列表
     */
    Mono<List<DeptEntity>> findAll();

    /**
     * 根据 ID 查询部门。
     *
     * @param id 部门 ID
     * @return 部门实体，不存在时返回 {@code Mono.empty()}
     */
    Mono<DeptEntity> findById(Long id);

    /**
     * 保存部门（新增或更新）。
     *
     * @param dept 部门实体
     * @return 保存后的部门实体
     */
    Mono<DeptEntity> save(DeptEntity dept);

    /**
     * 逻辑删除部门。
     *
     * @param id 部门 ID
     * @return 是否删除成功
     */
    Mono<Boolean> deleteById(Long id);
}
