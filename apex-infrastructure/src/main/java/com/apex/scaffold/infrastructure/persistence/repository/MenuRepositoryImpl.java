package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.MenuEntity;
import com.apex.scaffold.domain.repository.MenuRepository;
import com.apex.scaffold.infrastructure.persistence.converter.MenuPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.MenuMapper;
import com.apex.scaffold.infrastructure.persistence.po.MenuPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link MenuRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link MenuEntity} 的持久化读写与父子关系查询，通过
 * {@link MenuPoConverter} 在领域实体与 {@link MenuPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MenuRepository
 */
@Repository
@RequiredArgsConstructor
public class MenuRepositoryImpl implements MenuRepository {

    /**
     * 系统菜单 Mapper。
     */
    private final MenuMapper menuMapper;

    /**
     * 菜单实体与持久化对象转换器。
     */
    private final MenuPoConverter menuPoConverter;

    /**
     * 根据 ID 查询菜单。
     *
     * @param id 菜单 ID
     * @return 菜单实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<MenuEntity> findById(Long id) {
        return Mono.fromCallable(() -> menuMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(menuPoConverter.toEntity(po)));
    }

    /**
     * 查询全部菜单。
     *
     * <p>按父菜单 ID 升序、显示顺序升序排列，便于应用层组装树形结构。</p>
     *
     * @return 菜单实体列表
     */
    @Override
    public Mono<List<MenuEntity>> findAll() {
        return Mono.fromCallable(() -> menuMapper.selectList(ConditionUtil.lambda(MenuPO.class)
                        .orderByAsc(MenuPO::getParentId)
                        .orderByAsc(MenuPO::getSort)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(menuPoConverter::toEntityList);
    }

    /**
     * 根据父菜单 ID 查询直接子菜单。
     *
     * @param parentId 父菜单 ID
     * @return 子菜单列表
     */
    @Override
    public Mono<List<MenuEntity>> findByParentId(Long parentId) {
        return Mono.fromCallable(() -> menuMapper.selectList(ConditionUtil.lambda(MenuPO.class)
                        .eq(MenuPO::getParentId, parentId)
                        .orderByAsc(MenuPO::getSort)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(menuPoConverter::toEntityList);
    }

    /**
     * 检查指定父菜单下是否存在子菜单。
     *
     * @param parentId 父菜单 ID
     * @return true-存在子菜单，false-不存在
     */
    @Override
    public Mono<Boolean> existsByParentId(Long parentId) {
        return Mono.fromCallable(() -> {
                    Long count = menuMapper.selectCount(
                            ConditionUtil.lambda(MenuPO.class).eq(MenuPO::getParentId, parentId));
                    return count != null && count > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 新增菜单。
     *
     * @param entity 菜单实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(MenuEntity entity) {
        return Mono.fromCallable(() -> menuMapper.insert(menuPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新菜单。
     *
     * @param entity 菜单实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(MenuEntity entity) {
        return Mono.fromCallable(() -> menuMapper.updateById(menuPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除菜单。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 菜单 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> menuMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
