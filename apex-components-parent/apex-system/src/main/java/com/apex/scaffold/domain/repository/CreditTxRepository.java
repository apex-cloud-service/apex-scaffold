package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.CreditTxEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 积分流水仓储接口（领域层）
 *
 * <p>定义积分交易流水（CreditTx）的持久化操作规范，
 * 由基础设施层的 {@code CreditTxRepositoryImpl} 提供具体实现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface CreditTxRepository {
    /**
     * 分页查询用户积分流水。
     *
     * <p>按用户ID查询该用户的所有积分交易记录，按时间倒序排列，
     * 支持 offset/limit 分页。
     *
     * @param userId 用户ID
     * @param offset 查询偏移量
     * @param limit 最大返回条数
     * @return 积分流水列表的 Mono 包装
     */
    Mono<List<CreditTxEntity>> listByUserId(Long userId, int offset, int limit);

    /**
     * 统计用户积分流水总数。
     *
     * <p>按用户ID统计该用户的积分交易记录总数。
     *
     * @param userId 用户ID
     * @return 积分流水总数的 Mono 包装
     */
    Mono<Long> countByUserId(Long userId);
}
