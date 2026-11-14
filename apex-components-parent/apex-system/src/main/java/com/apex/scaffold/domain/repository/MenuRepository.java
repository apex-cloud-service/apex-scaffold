package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.MenuEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 系统菜单仓储接口。
 *
 * <p>定义菜单领域的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 菜单以树形结构组织，仓储提供按父子关系查询与全量查询能力。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface MenuRepository {

    /**
     * 根据 ID 查询菜单。
     *
     * @param id 菜单 ID
     * @return 菜单实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<MenuEntity> findById(Long id);

    /**
     * 查询全部菜单。
     *
     * <p>返回未逻辑删除的全部菜单，由应用层负责组装树形结构。</p>
     *
     * @return 菜单实体列表
     */
    Mono<List<MenuEntity>> findAll();

    /**
     * 根据父菜单 ID 查询直接子菜单。
     *
     * @param parentId 父菜单 ID
     * @return 子菜单列表
     */
    Mono<List<MenuEntity>> findByParentId(Long parentId);

    /**
     * 检查指定父菜单下是否存在子菜单。
     *
     * <p>用于删除菜单前的存在性校验，防止删除含子节点的菜单。</p>
     *
     * @param parentId 父菜单 ID
     * @return true-存在子菜单，false-不存在
     */
    Mono<Boolean> existsByParentId(Long parentId);

    /**
     * 新增菜单。
     *
     * @param entity 菜单实体
     * @return 影响行数
     */
    Mono<Integer> insert(MenuEntity entity);

    /**
     * 更新菜单。
     *
     * @param entity 菜单实体
     * @return 影响行数
     */
    Mono<Integer> updateById(MenuEntity entity);

    /**
     * 删除菜单。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 菜单 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
