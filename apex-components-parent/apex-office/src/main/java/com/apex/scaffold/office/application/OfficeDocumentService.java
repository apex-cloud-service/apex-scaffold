package com.apex.scaffold.office.application;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.office.domain.document.*;
import com.apex.scaffold.office.domain.port.DocumentPort;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公平台文档应用服务
 *
 * <p>负责文档操作的用例编排和业务逻辑协调，是Application层的核心服务。
 * 主要职责包括：
 * <ul>
 *   <li>协调领域对象和基础设施组件完成文档操作</li>
 *   <li>管理事务边界，确保数据一致性</li>
 *   <li>处理跨平台文档的统一逻辑</li>
 *   <li>维护租户隔离和安全控制</li>
 * </ul>
 *
 * <p>本服务依赖 {@link DocumentPort} 端口接口而非具体实现，
 * 符合依赖倒置原则和端口-适配器架构模式。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeDocumentService {

    private static final String FIELD_LAST_MODIFIED_TIME = "lastModifiedTime";

    private static final String FIELD_URL = "url";

    private static final String FIELD_TITLE = "title";

    private final DocumentInfoRepository documentInfoRepository;
    private final Map<String, DocumentPort> documentClients;

    /**
     * 创建文档
     *
     * <p>在指定的办公平台创建文档，并在本地数据库保存文档记录。
     *
     * @param platform 办公平台类型（feishu/dingtalk/wecom）
     * @param folderId 父文件夹ID
     * @param title 文档标题
     * @param documentType 文档类型
     * @param ownerId 所有者ID
     * @param ownerName 所有者姓名
     * @return 创建成功的文档信息
     */
    public Mono<DocumentInfo> createDocument(
            String platform,
            String folderId,
            String title,
            DocumentType documentType,
            String ownerId,
            String ownerName) {
        log.info("创建文档 - platform: {}, folderId: {}, title: {}, type: {}", platform, folderId, title, documentType);

        DocumentPort client = getDocumentClient(platform);
        String type = convertDocumentType(documentType);

        return client.createDocument(folderId, title, type)
                .flatMap(platformDocumentId -> client.getDocumentDetail(platformDocumentId)
                        .map(detail -> buildDocumentInfo(
                                platform, platformDocumentId, detail, documentType, ownerId, ownerName, folderId)))
                .flatMap(documentInfoRepository::save)
                .doOnSuccess(doc -> log.info(
                        "文档创建成功 - documentId: {}, platformDocumentId: {}", doc.getId(), doc.getPlatformDocumentId()))
                .doOnError(error -> log.error("文档创建失败 - title: {}", title, error));
    }

    /**
     * 查询文档详情
     *
     * <p>根据文档ID查询文档的详细信息。
     *
     * @param documentId 文档ID
     * @return 文档详情
     */
    public Mono<DocumentInfo> getDocumentDetail(Long documentId) {
        log.info("查询文档详情 - documentId: {}", documentId);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    DocumentPort client = getDocumentClient(doc.getPlatform());
                    return client.getDocumentDetail(doc.getPlatformDocumentId()).map(detail -> {
                        // 可以根据平台返回的最新信息更新文档
                        updateDocumentFromPlatform(doc, detail);
                        return doc;
                    });
                })
                .doOnSuccess(doc -> log.info("文档详情查询成功 - documentId: {}", documentId))
                .doOnError(error -> log.error("文档详情查询失败 - documentId: {}", documentId, error));
    }

    /**
     * 更新文档标题
     *
     * <p>更新文档的标题。
     *
     * @param documentId 文档ID
     * @param newTitle 新标题
     * @return 更新是否成功
     */
    public Mono<Boolean> updateDocumentTitle(Long documentId, String newTitle) {
        log.info("更新文档标题 - documentId: {}, newTitle: {}", documentId, newTitle);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    DocumentPort client = getDocumentClient(doc.getPlatform());
                    return client.updateDocumentTitle(doc.getPlatformDocumentId(), newTitle)
                            .flatMap(success -> {
                                if (success) {
                                    doc.updateTitle(newTitle);
                                    return documentInfoRepository.update(doc).thenReturn(true);
                                }
                                return Mono.just(false);
                            });
                })
                .doOnSuccess(success -> log.info("文档标题更新成功 - documentId: {}, result: {}", documentId, success))
                .doOnError(error -> log.error("文档标题更新失败 - documentId: {}", documentId, error));
    }

    /**
     * 移动文档
     *
     * <p>将文档移动到指定的文件夹。
     *
     * @param documentId 文档ID
     * @param targetFolderId 目标文件夹ID
     * @return 移动是否成功
     */
    public Mono<Boolean> moveDocument(Long documentId, String targetFolderId) {
        log.info("移动文档 - documentId: {}, targetFolderId: {}", documentId, targetFolderId);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    DocumentPort client = getDocumentClient(doc.getPlatform());
                    return client.moveDocument(doc.getPlatformDocumentId(), targetFolderId)
                            .flatMap(success -> {
                                if (success) {
                                    doc.setParentFolderId(targetFolderId);
                                    return documentInfoRepository.update(doc).thenReturn(true);
                                }
                                return Mono.just(false);
                            });
                })
                .doOnSuccess(success -> log.info("文档移动成功 - documentId: {}, result: {}", documentId, success))
                .doOnError(error -> log.error("文档移动失败 - documentId: {}", documentId, error));
    }

    /**
     * 复制文档
     *
     * <p>复制文档到指定的文件夹。
     *
     * @param documentId 文档ID
     * @param targetFolderId 目标文件夹ID
     * @param newTitle 新文档标题
     * @return 新文档信息
     */
    public Mono<DocumentInfo> copyDocument(Long documentId, String targetFolderId, String newTitle) {
        log.info("复制文档 - documentId: {}, targetFolderId: {}, newTitle: {}", documentId, targetFolderId, newTitle);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    DocumentPort client = getDocumentClient(doc.getPlatform());
                    return client.copyDocument(doc.getPlatformDocumentId(), targetFolderId, newTitle)
                            .flatMap(newPlatformDocId -> client.getDocumentDetail(newPlatformDocId)
                                    .map(detail -> buildDocumentInfo(
                                            doc.getPlatform(),
                                            newPlatformDocId,
                                            detail,
                                            doc.getDocumentType(),
                                            doc.getOwnerId(),
                                            doc.getOwnerName(),
                                            targetFolderId)))
                            .flatMap(documentInfoRepository::save);
                })
                .doOnSuccess(newDoc -> log.info("文档复制成功 - sourceId: {}, newId: {}", documentId, newDoc.getId()))
                .doOnError(error -> log.error("文档复制失败 - documentId: {}", documentId, error));
    }

    /**
     * 删除文档
     *
     * <p>删除文档（逻辑删除）。
     *
     * @param documentId 文档ID
     * @return 删除是否成功
     */
    public Mono<Boolean> deleteDocument(Long documentId) {
        log.info("删除文档 - documentId: {}", documentId);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    DocumentPort client = getDocumentClient(doc.getPlatform());
                    return client.deleteDocument(doc.getPlatformDocumentId()).flatMap(success -> {
                        if (success) {
                            return documentInfoRepository.deleteById(documentId);
                        }
                        return Mono.just(false);
                    });
                })
                .doOnSuccess(success -> log.info("文档删除成功 - documentId: {}, result: {}", documentId, success))
                .doOnError(error -> log.error("文档删除失败 - documentId: {}", documentId, error));
    }

    /**
     * 分享文档
     *
     * <p>生成文档的分享链接。
     *
     * @param documentId 文档ID
     * @param shareType 分享类型
     * @param expireTime 过期时间（秒）
     * @return 分享链接
     */
    public Mono<String> shareDocument(Long documentId, String shareType, Long expireTime) {
        log.info("分享文档 - documentId: {}, shareType: {}, expireTime: {}", documentId, shareType, expireTime);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    DocumentPort client = getDocumentClient(doc.getPlatform());
                    return client.shareDocument(doc.getPlatformDocumentId(), shareType, expireTime);
                })
                .doOnSuccess(shareLink -> log.info("文档分享成功 - documentId: {}, shareLink: {}", documentId, shareLink))
                .doOnError(error -> log.error("文档分享失败 - documentId: {}", documentId, error));
    }

    /**
     * 下载文档
     *
     * <p>获取文档的下载URL。
     *
     * @param documentId 文档ID
     * @param exportFormat 导出格式
     * @return 下载URL
     */
    public Mono<String> downloadDocument(Long documentId, String exportFormat) {
        log.info("下载文档 - documentId: {}, exportFormat: {}", documentId, exportFormat);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    DocumentPort client = getDocumentClient(doc.getPlatform());
                    return client.downloadDocument(doc.getPlatformDocumentId(), exportFormat);
                })
                .doOnSuccess(downloadUrl -> log.info("文档下载URL获取成功 - documentId: {}", documentId))
                .doOnError(error -> log.error("文档下载URL获取失败 - documentId: {}", documentId, error));
    }

    /**
     * 上传文件
     *
     * <p>上传文件到指定的文件夹。
     *
     * @param platform 办公平台类型
     * @param folderId 父文件夹ID
     * @param fileName 文件名
     * @param fileContent 文件内容（Base64编码）
     * @param ownerId 所有者ID
     * @param ownerName 所有者姓名
     * @return 上传成功的文档信息
     */
    public Mono<DocumentInfo> uploadFile(
            String platform, String folderId, String fileName, String fileContent, String ownerId, String ownerName) {
        log.info("上传文件 - platform: {}, folderId: {}, fileName: {}", platform, folderId, fileName);

        DocumentPort client = getDocumentClient(platform);

        return client.uploadFile(folderId, fileName, fileContent)
                .flatMap(platformDocumentId -> client.getDocumentDetail(platformDocumentId)
                        .map(detail -> buildDocumentInfo(
                                platform, platformDocumentId, detail, DocumentType.FILE, ownerId, ownerName, folderId)))
                .flatMap(documentInfoRepository::save)
                .doOnSuccess(doc -> log.info(
                        "文件上传成功 - documentId: {}, platformDocumentId: {}", doc.getId(), doc.getPlatformDocumentId()))
                .doOnError(error -> log.error("文件上传失败 - fileName: {}", fileName, error));
    }

    /**
     * 列出文件夹下的文档
     *
     * <p>查询指定文件夹下的所有文档。
     *
     * @param parentFolderId 父文件夹ID
     * @return 文档信息列表
     */
    public Mono<List<DocumentInfo>> listDocuments(String parentFolderId) {
        log.info("列出文件夹下的文档 - parentFolderId: {}", parentFolderId);

        return documentInfoRepository
                .findByParentFolder(parentFolderId)
                .doOnSuccess(list -> log.info("文档列表查询成功 - parentFolderId: {}, count: {}", parentFolderId, list.size()))
                .doOnError(error -> log.error("文档列表查询失败 - parentFolderId: {}", parentFolderId, error));
    }

    /**
     * 搜索文档
     *
     * <p>根据标题关键字搜索文档。
     *
     * @param keyword 搜索关键字
     * @return 文档信息列表
     */
    public Mono<List<DocumentInfo>> searchDocuments(String keyword) {
        log.info("搜索文档 - keyword: {}", keyword);

        return documentInfoRepository
                .searchByTitle(keyword)
                .doOnSuccess(list -> log.info("文档搜索成功 - keyword: {}, count: {}", keyword, list.size()))
                .doOnError(error -> log.error("文档搜索失败 - keyword: {}", keyword, error));
    }

    /**
     * 查询我的文档
     *
     * <p>查询指定所有者的所有文档。
     *
     * @param ownerId 所有者ID
     * @return 文档信息列表
     */
    public Mono<List<DocumentInfo>> getMyDocuments(String ownerId) {
        log.info("查询我的文档 - ownerId: {}", ownerId);

        return documentInfoRepository
                .findByOwner(ownerId)
                .doOnSuccess(list -> log.info("我的文档查询成功 - ownerId: {}, count: {}", ownerId, list.size()))
                .doOnError(error -> log.error("我的文档查询失败 - ownerId: {}", ownerId, error));
    }

    /**
     * 归档文档
     *
     * <p>将文档标记为已归档。
     *
     * @param documentId 文档ID
     * @return 归档是否成功
     */
    public Mono<Boolean> archiveDocument(Long documentId) {
        log.info("归档文档 - documentId: {}", documentId);

        return documentInfoRepository
                .findById(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("文档不存在: " + documentId)))
                .flatMap(doc -> {
                    doc.archive();
                    return documentInfoRepository.update(doc).thenReturn(true);
                })
                .doOnSuccess(success -> log.info("文档归档成功 - documentId: {}", documentId))
                .doOnError(error -> log.error("文档归档失败 - documentId: {}", documentId, error));
    }

    /**
     * 根据平台类型获取文档端口实现
     *
     * @param platform 平台类型
     * @return 文档端口实例
     */
    private DocumentPort getDocumentClient(String platform) {
        DocumentPort client = documentClients.get(platform + "DocumentClient");
        if (client == null) {
            throw new RuntimeException("不支持的办公平台: " + platform);
        }
        return client;
    }

    /**
     * 根据平台返回的详情构建文档信息
     *
     * @param platform 平台类型
     * @param platformDocumentId 平台文档ID
     * @param detail 平台返回的详情
     * @param documentType 文档类型
     * @param ownerId 所有者ID
     * @param ownerName 所有者姓名
     * @param parentFolderId 父文件夹ID
     * @return 文档信息
     */
    private DocumentInfo buildDocumentInfo(
            String platform,
            String platformDocumentId,
            Map<String, Object> detail,
            DocumentType documentType,
            String ownerId,
            String ownerName,
            String parentFolderId) {
        Long tenantId = TenantContext.currentTenantId()
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("租户ID未设置"));

        return DocumentInfo.builder()
                .tenantId(tenantId)
                .platform(platform)
                .platformDocumentId(platformDocumentId)
                .documentType(documentType)
                .title((String) detail.getOrDefault(FIELD_TITLE, ""))
                .path((String) detail.get("path"))
                .url((String) detail.get(FIELD_URL))
                .ownerId(ownerId)
                .ownerName(ownerName)
                .parentFolderId(parentFolderId)
                .permission("PRIVATE")
                .isFolder(documentType == DocumentType.FOLDER)
                .isArchived(false)
                .build();
    }

    /**
     * 根据平台返回的信息更新文档
     *
     * @param doc 文档信息
     * @param detail 平台返回的详情
     */
    private void updateDocumentFromPlatform(DocumentInfo doc, Map<String, Object> detail) {
        if (detail.containsKey(FIELD_TITLE)) {
            doc.setTitle((String) detail.get(FIELD_TITLE));
        }
        if (detail.containsKey(FIELD_URL)) {
            doc.setUrl((String) detail.get(FIELD_URL));
        }
        if (detail.containsKey(FIELD_LAST_MODIFIED_TIME)) {
            doc.setLastModifiedTime((LocalDateTime) detail.get(FIELD_LAST_MODIFIED_TIME));
        }
    }

    /**
     * 转换文档类型为平台类型
     *
     * @param documentType 文档类型枚举
     * @return 平台类型字符串
     */
    private String convertDocumentType(DocumentType documentType) {
        return switch (documentType) {
            case DOC -> "doc";
            case SHEET -> "sheet";
            case SLIDE -> "slide";
            default -> "doc";
        };
    }
}
