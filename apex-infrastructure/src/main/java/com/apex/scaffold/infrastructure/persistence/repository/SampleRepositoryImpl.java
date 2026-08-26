package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.SampleEntity;
import com.apex.scaffold.domain.repository.SampleRepository;
import com.apex.scaffold.infrastructure.persistence.converter.SamplePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.SampleMapper;
import com.apex.scaffold.infrastructure.persistence.po.SamplePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 样本仓库实现（基础设施层）。
 *
 * <p>负责 {@link SampleEntity} 的持久化读写，并通过 {@link SamplePoConverter}
 * 完成领域实体与持久化对象之间的转换。
 * 阻塞型数据库访问封装在 {@link Mono#fromCallable(java.util.concurrent.Callable)} 中并调度到
 * 专用阻塞 I/O 调度器执行。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SampleRepository
 */
@Repository
@RequiredArgsConstructor
public class SampleRepositoryImpl implements SampleRepository {

    private final SampleMapper mapper;
    private final SamplePoConverter samplePoConverter;

    /**
     * 根据样本 ID 查询样本详情。
     *
     * @param id 样本 ID
     * @return 样本实体；若不存在则返回空 Mono
     */
    @Override
    /**
     * 查找ById
     *
     * @param id 参数
     *
     * @return 结果
     */
    public Mono<SampleEntity> findById(Long id) {
        return Mono.fromCallable(() -> mapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(samplePoConverter::toEntity);
    }

    /**
     * 保存样本（新增或更新）。
     *
     * <p>当 id 为空时执行 insert；否则执行 updateById。
     *
     * @param entity 样本实体
     * @return 保存后的样本实体
     */
    @Override
    /**
     * 保存
     *
     * @param entity 参数
     *
     * @return 结果
     */
    public Mono<SampleEntity> save(SampleEntity entity) {
        return Mono.fromCallable(() -> {
                    SamplePO po = samplePoConverter.toPo(entity);
                    if (po.getId() == null) {
                        mapper.insert(po);
                    } else {
                        mapper.updateById(po);
                    }
                    return mapper.selectById(po.getId());
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(samplePoConverter::toEntity);
    }
}
