package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.DictTypeEntity;
import com.apex.scaffold.domain.repository.DictTypeRepository;
import com.apex.scaffold.infrastructure.persistence.converter.DictTypePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.DictTypeMapper;
import com.apex.scaffold.infrastructure.persistence.po.DictTypePO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link DictTypeRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link DictTypeEntity} 的持久化读写与编码唯一性校验，通过
 * {@link DictTypePoConverter} 在领域实体与 {@link DictTypePO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DictTypeRepository
 */
@Repository
@RequiredArgsConstructor
public class DictTypeRepositoryImpl implements DictTypeRepository {

    /**
     * 字典类型 Mapper。
     */
    private final DictTypeMapper dictTypeMapper;

    /**
     * 字典类型实体与持久化对象转换器。
     */
    private final DictTypePoConverter dictTypePoConverter;

    /**
     * 根据 ID 查询字典类型。
     *
     * @param id 字典类型 ID
     * @return 字典类型实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<DictTypeEntity> findById(Long id) {
        return Mono.fromCallable(() -> dictTypeMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(dictTypePoConverter.toEntity(po)));
    }

    /**
     * 根据编码查询字典类型。
     *
     * @param dictType 字典类型编码
     * @return 字典类型实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<DictTypeEntity> findByType(String dictType) {
        return Mono.fromCallable(() -> dictTypeMapper.selectOne(
                        ConditionUtil.lambda(DictTypePO.class).eq(DictTypePO::getDictType, dictType)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(dictTypePoConverter::toEntity);
    }

    /**
     * 查询全部字典类型。
     *
     * @return 字典类型实体列表
     */
    @Override
    public Mono<List<DictTypeEntity>> findAll() {
        return Mono.fromCallable(() -> dictTypeMapper.selectList(
                        ConditionUtil.lambda(DictTypePO.class).orderByAsc(DictTypePO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(dictTypePoConverter::toEntityList);
    }

    /**
     * 检查字典类型编码是否已存在。
     *
     * @param dictType 字典类型编码
     * @param excludeId 需要排除的字典类型 ID（更新场景），可为 {@code null}
     * @return true-已存在，false-不存在
     */
    @Override
    public Mono<Boolean> existsByType(String dictType, Long excludeId) {
        return Mono.fromCallable(() -> {
                    Long count = dictTypeMapper.selectCount(ConditionUtil.lambda(DictTypePO.class)
                            .eqIfPresent(DictTypePO::getDictType, dictType)
                            .neIfPresent(DictTypePO::getId, excludeId));
                    return count != null && count > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 新增字典类型。
     *
     * @param entity 字典类型实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(DictTypeEntity entity) {
        return Mono.fromCallable(() -> dictTypeMapper.insert(dictTypePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新字典类型。
     *
     * @param entity 字典类型实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(DictTypeEntity entity) {
        return Mono.fromCallable(() -> dictTypeMapper.updateById(dictTypePoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除字典类型。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 字典类型 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> dictTypeMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
