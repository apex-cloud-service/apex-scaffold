package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.CreditTxEntity;
import com.apex.scaffold.domain.repository.CreditTxRepository;
import com.apex.scaffold.infrastructure.persistence.converter.CreditTxPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.CreditTxMapper;
import com.apex.scaffold.infrastructure.persistence.po.CreditTxPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link CreditTxRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link CreditTxEntity} 的持久化读写，通过 {@link CreditTxPoConverter}
 * 在领域实体与 {@link CreditTxPO} 之间转换，阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see CreditTxRepository
 */
@Repository
@RequiredArgsConstructor
public class CreditTxRepositoryImpl implements CreditTxRepository {

    private final CreditTxMapper mapper;
    private final CreditTxPoConverter creditTxPoConverter;

    /**
     * 根据用户ID分页查询积分交易记录。
     *
     * @param userId 用户ID
     * @param offset 偏移量
     * @param limit 每页大小
     * @return 积分交易记录列表
     */
    @Override
    /**
     * 列表ByUserId
     *
     * @param userId 参数
     *
     * @param offset 参数
     *
     * @param limit 参数
     *
     * @return 结果
     */
    public Mono<List<CreditTxEntity>> listByUserId(Long userId, int offset, int limit) {
        return Mono.fromCallable(() -> mapper.selectList(new LambdaQueryWrapper<CreditTxPO>()
                        .eq(CreditTxPO::getUserId, userId)
                        .orderByDesc(CreditTxPO::getCreateTime)
                        .last("limit " + limit + " offset " + offset)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(creditTxPoConverter::toEntityList);
    }

    /**
     * 统计用户积分交易记录数量。
     *
     * <p>查询指定用户的积分交易记录总数。</p>
     *
     * @param userId 用户 ID
     * @return 交易记录总数
     */
    @Override
    /**
     * 计数ByUserId
     *
     * @param userId 参数
     *
     * @return 结果
     */
    public Mono<Long> countByUserId(Long userId) {
        return Mono.fromCallable(() ->
                        mapper.selectCount(new LambdaQueryWrapper<CreditTxPO>().eq(CreditTxPO::getUserId, userId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
