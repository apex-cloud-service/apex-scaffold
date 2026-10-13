package com.apex.scaffold.file.domain.repository;

import com.apex.scaffold.file.domain.model.entity.FileEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件信息仓储接口
 *
 * <p>仓储接口（领域层）
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface FileRepository {

    /**
     * 保存文件信息
     *
     * @param file 文件信息
     * @return 保存后的文件信息
     */
    Mono<FileEntity> save(FileEntity file);

    /**
     * 根据ID查询文件信息
     *
     * @param id 文件ID
     * @return 文件信息
     */
    Mono<FileEntity> findById(Long id);

    /**
     * 根据MD5查询文件信息
     *
     * @param md5 文件MD5
     * @return 文件信息
     */
    Mono<FileEntity> findByMd5(String md5);

    /**
     * 根据对象存储位置查询文件信息。
     *
     * @param bucketName 存储桶名称
     * @param fileName 对象键
     * @return 文件信息
     */
    Mono<FileEntity> findByStorageLocation(String bucketName, String fileName);

    /**
     * 删除文件信息
     *
     * @param id 文件ID
     * @return 成功返回 Mono.empty()
     */
    Mono<Void> deleteById(Long id);

    /**
     * 查询用户上传的所有文件
     *
     * @param userId 用户ID
     * @return 文件列表
     */
    Flux<FileEntity> findByUserId(Long userId);
}
