package com.apex.scaffold.file.infrastructure.persistence.repository;

import com.apex.scaffold.common.persistence.util.ConditionUtil;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.domain.model.entity.FileChunkEntity;
import com.apex.scaffold.file.domain.repository.FileChunkRepository;
import com.apex.scaffold.file.infrastructure.persistence.converter.FileChunkPoConverter;
import com.apex.scaffold.file.infrastructure.persistence.mapper.FileChunkMapper;
import com.apex.scaffold.file.infrastructure.persistence.po.FileChunkPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link FileChunkRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link FileChunkEntity} 的持久化读写，通过 {@link FileChunkPoConverter}
 * 在领域实体与 {@link FileChunkPO} 之间转换，阻塞操作调度到 JDBC 专用线程池，
 * 支撑大文件分片上传、断点续传与分片合并。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see FileChunkRepository
 */
@Repository
@RequiredArgsConstructor
public class FileChunkRepositoryImpl implements FileChunkRepository {

    private final FileChunkMapper fileChunkMapper;
    private final FileChunkPoConverter fileChunkPoConverter;

    /**
     * 保存分片信息
     *
     * <p>当主键为空时执行插入，否则执行更新
     *
     * @param chunk 分片信息实体
     * @return 保存后的分片信息
     */
    @Override
    public Mono<FileChunkEntity> save(FileChunkEntity chunk) {
        return Mono.fromCallable(() -> {
                    FileChunkPO po = fileChunkPoConverter.toPo(chunk);
                    if (po.getId() == null) {
                        fileChunkMapper.insert(po);
                    } else {
                        fileChunkMapper.updateById(po);
                    }
                    return fileChunkPoConverter.toEntity(po);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 根据所属文件 ID 与分片序号查询分片信息
     *
     * @param fileId 文件 ID
     * @param chunkIndex 分片序号
     * @return 分片信息，未找到时返回 Mono.empty()
     */
    @Override
    public Mono<FileChunkEntity> findByFileIdAndIndex(Long fileId, Integer chunkIndex) {
        return Mono.fromCallable(() -> fileChunkMapper.selectOne(ConditionUtil.lambda(FileChunkPO.class)
                        .eq(FileChunkPO::getFileId, fileId)
                        .eq(FileChunkPO::getChunkIndex, chunkIndex)
                        .last("LIMIT 1")))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> {
                    if (po == null) {
                        return Mono.empty();
                    }
                    return Mono.just(fileChunkPoConverter.toEntity(po));
                });
    }

    /**
     * 按分片序号升序查询指定文件的全部有效分片
     *
     * @param fileId 文件 ID
     * @return 分片信息流（按 chunk_index 升序）
     */
    @Override
    public Flux<FileChunkEntity> findByFileIdOrderByIndex(Long fileId) {
        return Mono.fromCallable(() -> fileChunkMapper.selectList(ConditionUtil.lambda(FileChunkPO.class)
                        .eq(FileChunkPO::getFileId, fileId)
                        .orderByAsc(FileChunkPO::getChunkIndex)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable(fileChunkPoConverter.toEntityList(list)));
    }

    /**
     * 统计指定文件已入库的有效分片数量
     *
     * @param fileId 文件 ID
     * @return 分片数量
     */
    @Override
    public Mono<Long> countByFileId(Long fileId) {
        return Mono.fromCallable(() -> fileChunkMapper.selectCount(
                        ConditionUtil.lambda(FileChunkPO.class).eq(FileChunkPO::getFileId, fileId)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 按文件 ID 逻辑删除全部分片
     *
     * @param fileId 文件 ID
     * @return 完成信号
     */
    @Override
    public Mono<Void> deleteByFileId(Long fileId) {
        return Mono.fromRunnable(() -> fileChunkMapper.markDeletedByFileId(fileId))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }
}
