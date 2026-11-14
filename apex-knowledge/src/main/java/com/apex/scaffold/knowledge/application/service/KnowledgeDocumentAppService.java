package com.apex.scaffold.knowledge.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.assembler.KnowledgeAssembler;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDetailDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeManualDocumentRequest;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeChunkRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeVectorIndexPort;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.storage.StorageRegistry;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 知识文档应用服务：登记、删除、重建索引与文档查询。
 *
 * <p>上传经存储五后端适配器落对象，主档登记完成后以桥接边界触发摄取管线；
 * 管线失败由校准 Job 与手动重建端点兜底，不阻塞上传响应。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentAppService {

    private final KnowledgeDocumentRepository documentRepository;

    private final KnowledgeSpaceRepository spaceRepository;

    private final KnowledgeChunkRepository chunkRepository;

    private final KnowledgeVectorIndexPort vectorIndexPort;

    private final KnowledgeProperties properties;

    private final StorageRegistry storageRegistry;

    private final IngestionService ingestionService;

    private final KnowledgeAssembler assembler;

    /**
     * 登记上传文档并触发摄取。
     *
     * @param spaceId 所属空间 ID
     * @param fileName 原始文件名
     * @param contentType 内容类型
     * @param content 文件字节
     * @return 文档 DTO
     */
    public Mono<KnowledgeDocumentDTO> registerUpload(
            Long spaceId, String fileName, String contentType, byte[] content) {
        String extension = extractExtension(fileName);
        validateUpload(extension, content.length);
        String objectKey = "kb/" + UUID.randomUUID() + "-" + sanitize(fileName);
        return requireSpace(spaceId)
                .then(storageRegistry
                        .defaultStorage()
                        .upload(
                                properties.getStorage().getBucket(),
                                objectKey,
                                new ByteArrayInputStream(content),
                                contentType)
                        .onErrorMap(e -> new BusinessException(ResultCode.STORAGE_ERROR, "对象存储写入失败"))
                        .flatMap(key -> documentRepository.insert(KnowledgeDocument.builder()
                                .spaceId(spaceId)
                                .title(titleOf(fileName))
                                .sourceType(KnowledgeDocument.SourceType.UPLOAD)
                                .contentType(contentType == null ? "application/octet-stream" : contentType)
                                .objectKey(key)
                                .fileSize((long) content.length)
                                .parseStatus(KnowledgeDocument.ParseStatus.PENDING)
                                .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                                .chunkCount(0)
                                .build())))
                .doOnNext(document -> triggerIngestion(document.getId()))
                .map(assembler::toDocumentDTO);
    }

    /**
     * 在线录入文档（正文直接落库，无对象存储环节）。
     *
     * @param request 录入请求
     * @return 文档 DTO
     */
    public Mono<KnowledgeDocumentDTO> registerManual(KnowledgeManualDocumentRequest request) {
        return requireSpace(request.spaceId())
                .then(documentRepository.insert(KnowledgeDocument.builder()
                        .spaceId(request.spaceId())
                        .title(request.title())
                        .sourceType(KnowledgeDocument.SourceType.MANUAL)
                        .contentType("text/plain")
                        .objectKey(null)
                        .fileSize((long) request.content().length())
                        .contentText(request.content())
                        .parseStatus(KnowledgeDocument.ParseStatus.PARSED)
                        .indexStatus(KnowledgeDocument.IndexStatus.PENDING)
                        .chunkCount(0)
                        .build()))
                .doOnNext(document -> triggerIngestion(document.getId()))
                .map(assembler::toDocumentDTO);
    }

    /**
     * 分页查询文档。
     *
     * @param spaceId 空间过滤（可空）
     * @param parseStatus 解析状态过滤（可空）
     * @param keyword 标题模糊匹配（可空）
     * @param pageNum 页码
     * @param pageSize 页大小
     * @return 文档分页 DTO
     */
    public Mono<PageResponse<KnowledgeDocumentDTO>> findPage(
            Long spaceId, String parseStatus, String keyword, int pageNum, int pageSize) {
        return documentRepository
                .findPage(spaceId, parseStatus, keyword, pageNum, pageSize)
                .map(page -> PageResponse.of(
                        page.getList().stream().map(assembler::toDocumentDTO).toList(),
                        page.getTotal(),
                        pageNum,
                        pageSize));
    }

    /**
     * 文档详情（含分块预览）。
     *
     * @param id 文档 ID
     * @return 详情 DTO
     */
    public Mono<KnowledgeDocumentDetailDTO> findById(Long id) {
        return requireDocument(id).flatMap(document -> chunkRepository
                .findByDocument(document.getId())
                .map(chunks -> new KnowledgeDocumentDetailDTO(
                        assembler.toDocumentDTO(document),
                        chunks.stream().map(assembler::toChunkDTO).toList())));
    }

    /**
     * 删除文档：同步清理分块索引，再删除主档。
     *
     * <p>索引删除失败不阻塞主档删除（记录告警），残留索引条目会在下次重建时收敛。
     *
     * @param id 文档 ID
     * @return 删除结果
     */
    public Mono<Boolean> delete(Long id) {
        return requireDocument(id).flatMap(document -> chunkRepository
                .deleteByDocument(document.getId())
                .then(removeIndexQuietly(document))
                .then(documentRepository.deleteById(id)));
    }

    /**
     * 手动重建索引：重置状态并触发摄取管线（解析产物已有则跳过解析）。
     *
     * @param id 文档 ID
     * @return 文档 DTO
     */
    public Mono<KnowledgeDocumentDTO> reindex(Long id) {
        return requireDocument(id)
                .flatMap(document -> documentRepository.update(KnowledgeDocument.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .sourceType(document.getSourceType())
                        .contentType(document.getContentType())
                        .objectKey(document.getObjectKey())
                        .fileSize(document.getFileSize())
                        .contentText(document.getContentText())
                        .parseStatus(
                                document.getContentText() == null
                                                || document.getContentText().isEmpty()
                                        ? KnowledgeDocument.ParseStatus.PENDING
                                        : KnowledgeDocument.ParseStatus.PARSED)
                        .parseError(null)
                        .chunkCount(0)
                        .indexStatus(KnowledgeDocument.IndexStatus.PENDING)
                        .build()))
                .doOnNext(document -> triggerIngestion(document.getId()))
                .map(assembler::toDocumentDTO);
    }

    /**
     * 桥接边界：上传/录入响应不等待摄取管线，异步触发并完整消费订阅错误；
     * 进程中断遗留的 PENDING 文档由校准 Job 收敛。
     *
     * @param documentId 文档 ID
     */
    private void triggerIngestion(Long documentId) {
        ingestionService
                .ingest(documentId)
                .subscribe(nothing -> {}, error -> log.error("知识文档摄取管线触发失败: documentId={}", documentId, error));
    }

    private Mono<Void> removeIndexQuietly(KnowledgeDocument document) {
        return Mono.defer(() -> {
                    String tenantId = TenantContext.currentTenantId().orElse(null);
                    if (tenantId == null || tenantId.isBlank()) {
                        log.warn("租户上下文缺失，跳过文档索引清理: documentId={}", document.getId());
                        return Mono.empty();
                    }
                    return vectorIndexPort.deleteDocumentChunks(tenantId, document.getId());
                })
                .onErrorResume(e -> {
                    log.warn("删除文档索引失败（主档继续删除）: documentId={}", document.getId(), e);
                    return Mono.empty();
                });
    }

    private Mono<KnowledgeDocument> requireDocument(Long id) {
        return documentRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "知识文档不存在")));
    }

    private Mono<KnowledgeSpace> requireSpace(Long id) {
        return spaceRepository
                .findById(id)
                .filter(space -> space.getStatus() != null && space.getStatus() == 1)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "知识空间不存在或已停用")));
    }

    private void validateUpload(String extension, int sizeBytes) {
        Set<String> supported = Set.copyOf(properties.getIngest().getSupportedTypes());
        if (extension == null || !supported.contains(extension)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的文件类型: " + extension);
        }
        long maxBytes = (long) properties.getIngest().getMaxUploadSizeMb() * 1024 * 1024;
        if (sizeBytes <= 0 || sizeBytes > maxBytes) {
            throw new BusinessException(
                    ResultCode.PARAM_ERROR,
                    "文件大小超出限制: " + properties.getIngest().getMaxUploadSizeMb() + "MB");
        }
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : fileName.substring(dot + 1).toLowerCase();
    }

    private String sanitize(String fileName) {
        String cleaned = fileName == null ? "file" : fileName.replaceAll("[^\\w\\u4e00-\\u9fa5.\\-]", "_");
        return cleaned.length() > 128 ? cleaned.substring(cleaned.length() - 128) : cleaned;
    }

    private String titleOf(String fileName) {
        if (fileName == null) {
            return "未命名文档";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
