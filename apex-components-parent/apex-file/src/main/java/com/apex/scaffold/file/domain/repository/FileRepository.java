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
     * 原子抢占尚未合并的会话，数据库条件更新确保只有一个调用方可写最终对象。
     * @param file 已验证归属的会话
     * @return 是否取得合并权
     */
    Mono<Boolean> claimMerge(FileEntity file);

    /**
     * 最终对象尚未写入或已确认清理后，条件释放 MERGING 会话供所有者重试。
     * @param file 已验证归属的会话
     * @return 是否将 MERGING 重置为 UPLOADING
     */
    Mono<Boolean> releaseMerge(FileEntity file);

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
     * @return 操作完成信号，失败时传播错误
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
