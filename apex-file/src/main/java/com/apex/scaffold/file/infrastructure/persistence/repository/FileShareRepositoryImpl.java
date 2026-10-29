package com.apex.scaffold.file.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.domain.model.entity.FileShareEntity;
import com.apex.scaffold.file.domain.repository.FileShareRepository;
import com.apex.scaffold.file.infrastructure.persistence.converter.FileSharePoConverter;
import com.apex.scaffold.file.infrastructure.persistence.mapper.FileShareMapper;
import com.apex.scaffold.file.infrastructure.persistence.po.FileSharePO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link FileShareRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>阻塞访问统一调度到 JDBC 线程池；撤销使用条件更新同时约束租户与创建者，
 * 受影响行数不等于 1 时视为无权撤销或状态已变更。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 * @see FileShareRepository
 */
@Repository
@RequiredArgsConstructor
public class FileShareRepositoryImpl implements FileShareRepository {

    private final FileShareMapper fileShareMapper;
    private final FileSharePoConverter fileSharePoConverter;

    /** {@inheritDoc} */
    @Override
    public Mono<FileShareEntity> save(FileShareEntity share) {
        return Mono.fromCallable(() -> {
                    FileSharePO po = fileSharePoConverter.toPo(share);
                    int affected = po.getId() == null ? fileShareMapper.insert(po) : fileShareMapper.updateById(po);
                    if (affected != 1) {
                        throw new IllegalStateException("文件分享记录保存失败或并发版本冲突");
                    }
                    return fileSharePoConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<FileShareEntity> findById(Long id) {
        return Mono.fromCallable(() -> fileShareMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(fileSharePoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<FileShareEntity> findByTokenHash(String tokenHash) {
        return Mono.fromCallable(() -> fileShareMapper.selectOne(
                        ConditionUtil.lambda(FileSharePO.class).eq(FileSharePO::getTokenHash, tokenHash)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(fileSharePoConverter.toEntity(po)));
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> markRevoked(Long id, String tenantId, Long creatorId, LocalDateTime now) {
        return Mono.fromCallable(() -> fileShareMapper.update(
                                null,
                                new LambdaUpdateWrapper<FileSharePO>()
                                        .eq(FileSharePO::getId, id)
                                        .eq(FileSharePO::getTenantId, tenantId)
                                        .eq(FileSharePO::getCreatorId, creatorId)
                                        .eq(FileSharePO::getStatus, FileShareEntity.STATUS_ACTIVE)
                                        .set(FileSharePO::getStatus, FileShareEntity.STATUS_REVOKED)
                                        .set(FileSharePO::getUpdateTime, now))
                        == 1)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
