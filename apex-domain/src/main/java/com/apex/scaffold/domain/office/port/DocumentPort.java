package com.apex.scaffold.domain.office.port;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 文档端口接口
 *
 * <p>定义文档领域的外部依赖接口，遵循端口-适配器架构模式。
 * 该接口由 Domain 层定义，由 Infrastructure 层实现，Application 层使用。
 *
 * <p>这是一个出站端口（Outbound Port），用于与外部办公平台的文档系统交互。
 * 通过依赖倒置原则，Application 层依赖此接口而非具体实现，保持架构清晰。
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
public interface DocumentPort {

    /**
     * 创建文档
     *
     * @param folderId 父文件夹ID
     * @param title 文档标题
     * @param type 文档类型（doc/sheet/slide）
     * @return 平台返回的文档ID
     */
    Mono<String> createDocument(String folderId, String title, String type);

    /**
     * 查询文档详情
     *
     * @param platformDocumentId 平台中的文档ID
     * @return 文档详情
     */
    Mono<Map<String, Object>> getDocumentDetail(String platformDocumentId);

    /**
     * 更新文档标题
     *
     * @param platformDocumentId 平台中的文档ID
     * @param newTitle 新标题
     * @return 更新是否成功
     */
    Mono<Boolean> updateDocumentTitle(String platformDocumentId, String newTitle);

    /**
     * 移动文档到指定文件夹
     *
     * @param platformDocumentId 平台中的文档ID
     * @param targetFolderId 目标文件夹ID
     * @return 移动是否成功
     */
    Mono<Boolean> moveDocument(String platformDocumentId, String targetFolderId);

    /**
     * 复制文档
     *
     * @param platformDocumentId 平台中的文档ID
     * @param targetFolderId 目标文件夹ID
     * @param newTitle 新文档标题
     * @return 新文档的ID
     */
    Mono<String> copyDocument(String platformDocumentId, String targetFolderId, String newTitle);

    /**
     * 删除文档
     *
     * @param platformDocumentId 平台中的文档ID
     * @return 删除是否成功
     */
    Mono<Boolean> deleteDocument(String platformDocumentId);

    /**
     * 获取文档权限信息
     *
     * @param platformDocumentId 平台中的文档ID
     * @return 权限信息
     */
    Mono<Map<String, Object>> getDocumentPermission(String platformDocumentId);

    /**
     * 更新文档权限
     *
     * @param platformDocumentId 平台中的文档ID
     * @param userId 用户ID
     * @param permissionType 权限类型（view/edit/full_control）
     * @return 更新是否成功
     */
    Mono<Boolean> updateDocumentPermission(String platformDocumentId, String userId, String permissionType);

    /**
     * 分享文档
     *
     * @param platformDocumentId 平台中的文档ID
     * @param shareType 分享类型（link/user）
     * @param expireTime 过期时间（秒，-1表示永不过期）
     * @return 分享链接或分享ID
     */
    Mono<String> shareDocument(String platformDocumentId, String shareType, Long expireTime);

    /**
     * 下载文档
     *
     * @param platformDocumentId 平台中的文档ID
     * @param exportFormat 导出格式（pdf/docx/xlsx等）
     * @return 文档下载URL
     */
    Mono<String> downloadDocument(String platformDocumentId, String exportFormat);

    /**
     * 上传文件
     *
     * @param folderId 父文件夹ID
     * @param fileName 文件名
     * @param fileContent 文件内容（Base64编码）
     * @return 平台返回的文件ID
     */
    Mono<String> uploadFile(String folderId, String fileName, String fileContent);

    /**
     * 列出文件夹下的文档
     *
     * @param folderId 文件夹ID
     * @param pageSize 每页数量
     * @param pageToken 分页标记
     * @return 文档列表和下一页标记
     */
    Mono<Map<String, Object>> listDocuments(String folderId, Integer pageSize, String pageToken);

    /**
     * 搜索文档
     *
     * @param keyword 搜索关键字
     * @param pageSize 每页数量
     * @param pageToken 分页标记
     * @return 搜索结果和下一页标记
     */
    Mono<Map<String, Object>> searchDocuments(String keyword, Integer pageSize, String pageToken);

    /**
     * 获取支持的平台类型
     *
     * @return 平台类型标识（feishu/dingtalk/wecom）
     */
    String getPlatformType();
}
