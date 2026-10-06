package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.DictDataEntity;
import com.apex.scaffold.domain.repository.DictDataRepository;
import com.apex.scaffold.infrastructure.persistence.converter.DictDataPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.DictDataMapper;
import com.apex.scaffold.infrastructure.persistence.po.DictDataPO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link DictDataRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link DictDataEntity} 的持久化读写与默认项管理，通过
 * {@link DictDataPoConverter} 在领域实体与 {@link DictDataPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DictDataRepository
 */
@Repository
@RequiredArgsConstructor
public class DictDataRepositoryImpl implements DictDataRepository {

    /**
     * 字典数据 Mapper。
     */
    private final DictDataMapper dictDataMapper;

    /**
     * 字典数据实体与持久化对象转换器。
     */
    private final DictDataPoConverter dictDataPoConverter;

    /**
     * 根据 ID 查询字典数据。
     *
     * @param id 字典数据 ID
     * @return 字典数据实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<DictDataEntity> findById(Long id) {
        return Mono.fromCallable(() -> dictDataMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(dictDataPoConverter.toEntity(po)));
    }

    /**
     * 根据字典类型编码查询全部数据项。
     *
     * <p>仅返回启用状态的数据项，按字典排序升序排列。</p>
     *
     * @param dictType 字典类型编码
     * @return 字典数据实体列表
     */
    @Override
    public Mono<List<DictDataEntity>> findByType(String dictType) {
        return Mono.fromCallable(() -> dictDataMapper.selectList(ConditionUtil.lambda(DictDataPO.class)
                        .eq(DictDataPO::getDictType, dictType)
                        .eq(DictDataPO::getStatus, DictDataEntity.DictState.ENABLED.getCode())
                        .orderByAsc(DictDataPO::getDictSort)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(dictDataPoConverter::toEntityList);
    }

    /**
     * 检查字典类型下是否存在数据项。
     *
     * @param dictType 字典类型编码
     * @return true-存在数据项，false-不存在
     */
    @Override
    public Mono<Boolean> existsByType(String dictType) {
        return Mono.fromCallable(() -> {
                    Long count = dictDataMapper.selectCount(
                            ConditionUtil.lambda(DictDataPO.class).eq(DictDataPO::getDictType, dictType));
                    return count != null && count > 0;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 新增字典数据。
     *
     * @param entity 字典数据实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(DictDataEntity entity) {
        return Mono.fromCallable(() -> dictDataMapper.insert(dictDataPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新字典数据。
     *
     * @param entity 字典数据实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(DictDataEntity entity) {
        return Mono.fromCallable(() -> dictDataMapper.updateById(dictDataPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除字典数据。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 字典数据 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> dictDataMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 将指定类型下的全部数据项取消默认标记。
     *
     * <p>通过更新语句将 {@code is_default} 置 0，保证同一类型内默认项唯一。</p>
     *
     * @param dictType 字典类型编码
     * @return 影响行数
     */
    @Override
    public Mono<Integer> clearDefaultByType(String dictType) {
        return Mono.fromCallable(() -> dictDataMapper.update(
                        null,
                        new LambdaUpdateWrapper<DictDataPO>()
                                .eq(DictDataPO::getDictType, dictType)
                                .set(DictDataPO::getIsDefault, DictDataEntity.DictDefault.NOT_DEFAULT.getCode())))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
