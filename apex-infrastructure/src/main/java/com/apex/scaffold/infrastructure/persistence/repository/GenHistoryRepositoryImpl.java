package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenHistoryEntity;
import com.apex.scaffold.domain.repository.GenHistoryRepository;
import com.apex.scaffold.infrastructure.persistence.converter.GenHistoryPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.GenHistoryMapper;
import com.apex.scaffold.infrastructure.persistence.po.GenHistoryPO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link GenHistoryRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责代码生成器生成历史记录的持久化读写与审计追踪，通过
 * {@link GenHistoryPoConverter} 在领域实体与 {@link GenHistoryPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>生成历史记录的增删改查</li>
 *   <li>按配置ID查询关联的历史记录</li>
 *   <li>支持审计追踪和问题排查</li>
 *   <li>JDBC 阻塞操作隔离到专用调度器</li>
 * </ul>
 *
 * <h2>审计作用</h2>
 * <ul>
 *   <li>记录每次代码生成任务的执行结果</li>
 *   <li>追溯生成历史，支持回溯和对比</li>
 *   <li>统计生成成功率和失败原因</li>
 *   <li>提供操作审计和责任追踪</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GenHistoryRepository
 */
@Repository
@RequiredArgsConstructor
public class GenHistoryRepositoryImpl implements GenHistoryRepository {

    /**
     * 生成历史 Mapper。
     */
    private final GenHistoryMapper historyMapper;

    /**
     * 生成历史实体与持久化对象转换器。
     */
    private final GenHistoryPoConverter historyPoConverter;

    /**
     * 根据 ID 查询生成历史。
     *
     * @param id 历史记录 ID
     * @return 历史实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<GenHistoryEntity> findById(Long id) {
        return Mono.fromCallable(() -> historyMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(historyPoConverter.toEntity(po)));
    }

    /**
     * 根据配置ID查询生成历史。
     *
     * <p>查询指定生成配置产生的所有历史记录，按创建时间倒序排列（最新的在前）。
     *
     * @param configId 生成配置ID
     * @return 历史实体列表
     */
    @Override
    public Mono<List<GenHistoryEntity>> findByConfigId(Long configId) {
        return Mono.fromCallable(() -> historyMapper.selectList(ConditionUtil.lambda(GenHistoryPO.class)
                        .eq(GenHistoryPO::getConfigId, configId)
                        .orderByDesc(GenHistoryPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(historyPoConverter::toEntityList);
    }

    /**
     * 查询全部生成历史。
     *
     * <p>查询当前租户下的所有生成历史，按创建时间倒序排列。
     *
     * @return 历史实体列表
     */
    @Override
    public Mono<List<GenHistoryEntity>> findAll() {
        return Mono.fromCallable(() -> historyMapper.selectList(
                        ConditionUtil.lambda(GenHistoryPO.class).orderByDesc(GenHistoryPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(historyPoConverter::toEntityList);
    }

    /**
     * 新增生成历史记录。
     *
     * <p>代码生成任务开始时创建记录，初始状态可能为待处理。
     * 任务完成后通过 {@link #updateById} 更新状态和结果。
     *
     * @param entity 历史实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(GenHistoryEntity entity) {
        return Mono.fromCallable(() -> historyMapper.insert(historyPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新生成历史记录。
     *
     * <p>任务完成后更新状态（SUCCESS/FAILED）、生成文件列表或错误信息。
     *
     * @param entity 历史实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(GenHistoryEntity entity) {
        return Mono.fromCallable(() -> historyMapper.updateById(historyPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除生成历史记录。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。
     * 历史记录通常用于审计，删除操作应谨慎使用。</p>
     *
     * @param id 历史记录 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> historyMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
