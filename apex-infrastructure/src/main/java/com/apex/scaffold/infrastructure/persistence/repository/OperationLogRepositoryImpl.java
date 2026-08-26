package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import com.apex.scaffold.domain.repository.OperationLogRepository;
import com.apex.scaffold.infrastructure.persistence.converter.OperationLogPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.OperationLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.OperationLogPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link OperationLogRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link OperationLogEntity} 的持久化读写，通过 {@link OperationLogPoConverter}
 * 完成领域实体与 {@link OperationLogPO} 之间的转换，阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OperationLogRepository
 */
@Repository
@RequiredArgsConstructor
public class OperationLogRepositoryImpl implements OperationLogRepository {

    private final OperationLogMapper operationLogMapper;
    private final OperationLogPoConverter operationLogPoConverter;

    /**
     * 插入操作日志
     *
     * <p>写入数据库后回填主键到领域实体中
     *
     * @param log 操作日志实体
     * @return 影响行数
     */
    @Override
    /**
     * 插入
     *
     * @param log 参数
     *
     * @return 结果
     */
    public Mono<Integer> insert(OperationLogEntity log) {
        return Mono.fromCallable(() -> {
                    OperationLogPO po = operationLogPoConverter.toPo(log);
                    int rows = operationLogMapper.insert(po);
                    log.setId(po.getId());
                    log.setCreateTime(po.getCreateTime());
                    log.setUpdateTime(po.getUpdateTime());
                    log.setDeleted(po.getDeleted());
                    return rows;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
