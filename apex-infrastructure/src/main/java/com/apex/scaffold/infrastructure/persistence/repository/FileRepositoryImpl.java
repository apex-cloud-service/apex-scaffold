package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.FileEntity;
import com.apex.scaffold.domain.repository.FileRepository;
import com.apex.scaffold.infrastructure.persistence.converter.FilePoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.FileMapper;
import com.apex.scaffold.infrastructure.persistence.po.FilePO;
import com.apex.scaffold.common.persistence.util.ConditionUtil;
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

    private final FileMapper fileMapper;
    private final FilePoConverter filePoConverter;

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
                    FilePO po = filePoConverter.toPo(file);
                    if (po.getId() == null) {
                        fileMapper.insert(po);
                    } else {
                        fileMapper.updateById(po);
                    }
                    return filePoConverter.toEntity(po);
                })
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
                        .eq(FilePO::getFileName, fileName)
                        .last("LIMIT 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(filePoConverter.toEntity(po)));
    }

    /**
     * 根据文件 ID 删除文件信息
     *
     * @param id 文件 ID
     * @return 成功返回 Mono.empty()
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
