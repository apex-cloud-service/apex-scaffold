package com.apex.scaffold.office.domain.document;

import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 文档信息仓储接口
 *
 * <p>定义文档信息聚合根的持久化操作，遵循DDD仓储模式。
 * 所有方法返回Mono类型以支持响应式编程，实现类需要使用BlockingIoSchedulers隔离阻塞I/O操作。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
public interface DocumentInfoRepository {

    /**
     * 保存文档信息
     *
     * @param documentInfo 文档信息聚合根
     * @return 保存后的文档信息（包含生成的ID）
     */
    Mono<DocumentInfo> save(DocumentInfo documentInfo);

    /**
     * 根据ID查询文档信息
     *
     * @param id 文档信息ID
     * @return 文档信息，如果不存在则返回空Mono
     */
    Mono<DocumentInfo> findById(Long id);

    /**
     * 根据平台和平台文档ID查询文档信息
     *
     * @param platform 办公平台类型
     * @param platformDocumentId 平台中的文档ID
     * @return 文档信息，如果不存在则返回空Mono
     */
    Mono<DocumentInfo> findByPlatformAndDocumentId(String platform, String platformDocumentId);

    /**
     * 查询指定所有者的文档列表
     *
     * @param ownerId 所有者ID
     * @return 文档信息列表
     */
    Mono<List<DocumentInfo>> findByOwner(String ownerId);

    /**
     * 查询指定父文件夹下的文档列表
     *
     * @param parentFolderId 父文件夹ID
     * @return 文档信息列表
     */
    Mono<List<DocumentInfo>> findByParentFolder(String parentFolderId);

    /**
     * 根据文档标题模糊查询
     *
     * @param titleKeyword 标题关键字
     * @return 文档信息列表
     */
    Mono<List<DocumentInfo>> searchByTitle(String titleKeyword);

    /**
     * 查询指定协作者参与的文档列表
     *
     * @param collaboratorId 协作者ID
     * @return 文档信息列表
     */
    Mono<List<DocumentInfo>> findByCollaborator(String collaboratorId);

    /**
     * 查询已归档的文档列表
     *
     * @param ownerId 所有者ID
     * @return 文档信息列表
     */
    Mono<List<DocumentInfo>> findArchivedDocuments(String ownerId);

    /**
     * 更新文档信息
     *
     * @param documentInfo 文档信息聚合根
     * @return 更新后的文档信息
     */
    Mono<DocumentInfo> update(DocumentInfo documentInfo);

    /**
     * 删除文档信息（逻辑删除）
     *
     * @param id 文档信息ID
     * @return 删除成功返回true，否则返回false
     */
    Mono<Boolean> deleteById(Long id);

    /**
     * 批量保存文档信息
     *
     * @param documentInfoList 文档信息列表
     * @return 保存成功的数量
     */
    Mono<Integer> batchSave(List<DocumentInfo> documentInfoList);
}
