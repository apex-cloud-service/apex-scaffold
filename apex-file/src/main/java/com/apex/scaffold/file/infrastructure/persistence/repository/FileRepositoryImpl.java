package com.apex.scaffold.file.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.infrastructure.persistence.converter.FilePoConverter;
import com.apex.scaffold.file.infrastructure.persistence.mapper.FileMapper;
import com.apex.scaffold.file.infrastructure.persistence.po.FilePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link FileRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link FileEntity} 的持久化读写，通过 {@link FilePoConverter}
 * 在领域实体与 {@link FilePO} 之间转换，阻塞操作调度到 JDBC 专用线程池。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see FileRepository
 */
@Repository
@RequiredArgsConstructor
public class FileRepositoryImpl implements FileRepository {

    private static final String ORPHANED = "ORPHANED";

    private final FileMapper fileMapper;
    private final FilePoConverter filePoConverter;
    private final javax.sql.DataSource dataSource;

    /**
     * 保存文件信息
     *
     * <p>当主键为空时执行插入，否则执行更新
     *
     * @param file 文件信息实体
     * @return 保存后的文件信息
     */
    @Override
    public Mono<FileEntity> save(FileEntity file) {
        return Mono.fromCallable(() -> {
                    if (ORPHANED.equals(file.getStorageStatus()) && file.getId() == null) {
                        // 补偿依据必须独立提交，不继承业务失败的事务回滚状态。
                        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
                        transaction.setPropagationBehavior(
                                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                        return transaction.execute(status -> persist(file));
                    }
                    return persist(file);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** @param file 文件或独立清理依据 @return 校验受影响行数后的持久化结果 */
    private FileEntity persist(FileEntity file) {
        FilePO po = filePoConverter.toPo(file);
        int affected = po.getId() == null ? fileMapper.insert(po) : fileMapper.updateById(po);
        if (affected != 1) {
            throw new IllegalStateException("文件记录保存失败或并发版本冲突");
        }
        return filePoConverter.toEntity(po);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> claimMerge(FileEntity file) {
        return Mono.fromCallable(() -> fileMapper.update(
                                null,
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FilePO>()
                                        .eq(FilePO::getId, file.getId())
                                        .eq(FilePO::getTenantId, file.getTenantId())
                                        .eq(FilePO::getUserId, file.getUserId())
                                        .eq(FilePO::getStorageStatus, "UPLOADING")
                                        .set(FilePO::getStorageStatus, "MERGING")
                                        .set(FilePO::getUpdateTime, java.time.LocalDateTime.now()))
                        == 1)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> releaseMerge(FileEntity file) {
        return Mono.fromCallable(() -> fileMapper.update(
                                null,
                                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<FilePO>()
                                        .eq(FilePO::getId, file.getId())
                                        .eq(FilePO::getTenantId, file.getTenantId())
                                        .eq(FilePO::getUserId, file.getUserId())
                                        .eq(FilePO::getStorageStatus, "MERGING")
                                        .set(FilePO::getStorageStatus, "UPLOADING")
                                        .set(FilePO::getUpdateTime, java.time.LocalDateTime.now()))
                        == 1)
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据文件 ID 查询文件信息
     *
     * @param id 文件 ID
     * @return 文件信息，未找到时返回 Mono.empty()
     */
    @Override
    public Mono<FileEntity> findById(Long id) {
        return Mono.fromCallable(() -> fileMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> {
                    if (po == null) {
                        return Mono.empty();
                    }
                    return Mono.just(filePoConverter.toEntity(po));
                });
    }

    /**
     * 根据文件 MD5 查询文件信息
     *
     * @param md5 文件 MD5
     * @return 文件信息，未找到时返回 Mono.empty()
     */
    @Override
    public Mono<FileEntity> findByMd5(String md5) {
        return Mono.fromCallable(() -> {
                    return fileMapper.selectOne(
                            ConditionUtil.lambda(FilePO.class).eqIfPresent(FilePO::getMd5, md5));
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> {
                    if (po == null) {
                        return Mono.empty();
                    }
                    return Mono.just(filePoConverter.toEntity(po));
                });
    }

    /** {@inheritDoc} */
    @Override
    public Mono<FileEntity> findByStorageLocation(String bucketName, String fileName) {
        return Mono.fromCallable(() -> fileMapper.selectOne(ConditionUtil.lambda(FilePO.class)
                        .eq(FilePO::getBucketName, bucketName)
                        .eq(FilePO::getFileName, fileName)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(filePoConverter.toEntity(po)));
    }

    /**
     * 根据文件 ID 删除文件信息
     *
     * @param id 文件 ID
     */
    @Override
    public Mono<Void> deleteById(Long id) {
        return Mono.fromRunnable(() -> fileMapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    /**
     * 查询指定用户上传的文件列表
     *
     * @param userId 用户 ID
     * @return 文件信息流
     */
    @Override
    public Flux<FileEntity> findByUserId(Long userId) {
        return Mono.fromCallable(() -> fileMapper.selectList(
                        ConditionUtil.lambda(FilePO.class).eqIfPresent(FilePO::getUserId, userId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable(filePoConverter.toEntityList(list)));
    }
}
