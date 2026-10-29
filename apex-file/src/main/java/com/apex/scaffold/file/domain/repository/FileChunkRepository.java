package com.apex.scaffold.file.domain.repository;

import com.apex.scaffold.file.domain.model.entity.FileChunkEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件分片仓储接口
 *
 * <p>仓储接口（领域层），负责分片元数据的读写，支撑大文件分片上传与断点续传。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface FileChunkRepository {

    /**
     * 保存分片信息
     *
     * @param chunk 分片信息
     * @return 保存后的分片信息
     */
    Mono<FileChunkEntity> save(FileChunkEntity chunk);

    /**
     * 根据所属文件 ID 与分片序号查询分片信息
     *
     * @param fileId 文件 ID
     * @param chunkIndex 分片序号
     * @return 分片信息，未找到时返回 Mono.empty()
     */
    Mono<FileChunkEntity> findByFileIdAndIndex(Long fileId, Integer chunkIndex);

    /**
     * 按分片序号升序查询指定文件的全部有效分片
     *
     * @param fileId 文件 ID
     * @return 分片信息流（按 chunk_index 升序）
     */
    Flux<FileChunkEntity> findByFileIdOrderByIndex(Long fileId);

    /**
     * 统计指定文件已入库的有效分片数量
     *
     * @param fileId 文件 ID
     * @return 分片数量
     */
    Mono<Long> countByFileId(Long fileId);

    /**
     * 按文件 ID 逻辑删除全部分片
     *
     * @param fileId 文件 ID
     */
    Mono<Void> deleteByFileId(Long fileId);
}
