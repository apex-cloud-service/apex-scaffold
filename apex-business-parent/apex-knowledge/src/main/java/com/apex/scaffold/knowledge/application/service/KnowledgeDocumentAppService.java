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
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentLifecyclePort;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeDocumentRepository;
import com.apex.scaffold.knowledge.domain.gateway.KnowledgeSpaceRepository;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocument;
import com.apex.scaffold.knowledge.domain.model.KnowledgeDocumentLease;
import com.apex.scaffold.knowledge.domain.model.KnowledgeSpace;
import com.apex.scaffold.storage.ObjectStorage;
import com.apex.scaffold.storage.StorageRegistry;
import java.io.ByteArrayInputStream;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 知识文档应用服务，编排上传或在线录入、查询、删除及重新摄取。
 *
 * <p>上传前预留独占对象定位凭证与数据库租约，登记失败补偿已知对象并保留失败凭证。
 * 主档登记或重建状态更新后捕获可信租户独立订阅 {@link IngestionService}，响应不等待摄取完成。
 * 删除在短 JDBC 事务内记录清理意图并 soft-delete 隐藏，Boolean 仅表示接受。
 *
 * <p>对象、分块与索引清理由持久任务及租约恢复；索引任务必须成功确认，失败可查询并重试。
 * 历史上传缺定位快照不会使用当前默认存储删除。删除 tombstone 长期定向复核迟到任务，
 * 保证最终收敛而非瞬时永久无残留；外部可用性与持续扫描仍是恢复条件。
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

    private final KnowledgeProperties properties;

    private final StorageRegistry storageRegistry;

    private final IngestionService ingestionService;

    private final KnowledgeAssembler assembler;

    private final KnowledgeDocumentLifecyclePort lifecycle;

    private final KnowledgeDocumentLeaseRunner leaseRunner;

    /**
     * 保存上传对象、登记待解析主档，并独立触发摄取。
     *
     * <p>扩展名与字节数在方法调用时同步校验；随后检查空间可用性，预留持久补偿凭证后保存对象并登记主档。
     * 对象已保存而登记失败时仅删除本次已知独占对象，补偿失败保留凭证供后台重试。
     * 正常登记原子关闭 staging 凭证；取消或进程失联依赖租约到期恢复，不宣称跨资源原子回滚。
     * 返回的 DTO 来自主档登记结果，不代表解析、分块或索引已完成。
     *
     * @param spaceId 所属空间 ID，须对应启用状态的空间
     * @param fileName 原始文件名，用于扩展名校验、标题与对象键生成
     * @param contentType 上传内容类型；主档对 {@code null} 使用默认类型
     * @param content 完整文件字节，调用方须提供非 {@code null} 值
     * @return 登记后的文档 DTO；空间无效发出 {@code NOT_FOUND} 错误信号，
     *     上传链失败转换为 {@code STORAGE_ERROR}，主档持久化失败传播相应错误
     * @throws BusinessException 文件类型不受支持、内容为空或大小超限时同步抛出
     */
    public Mono<KnowledgeDocumentDTO> registerUpload(
            Long spaceId, String fileName, String contentType, byte[] content) {
        String extension = extractExtension(fileName);
        validateUpload(extension, content.length);
        String objectKey = "kb/" + UUID.randomUUID() + "-" + sanitize(fileName);
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "可信租户上下文缺失")))
                .flatMap(tenant -> requireSpace(spaceId)
                        .then(Mono.defer(() -> {
                            ObjectStorage storage = storageRegistry.defaultStorage();
                            String bucket = properties.getStorage().getBucket();
                            return lifecycle
                                    .reserveUpload(
                                            titleOf(fileName),
                                            storage.type(),
                                            bucket,
                                            objectKey,
                                            properties.getMeilisearch().getUrl(),
                                            properties.getMeilisearch().getIndexPrefix() + "kb_chunks")
                                    .flatMap(lease -> Mono.usingWhen(
                                            Mono.just(lease),
                                            owned -> leaseRunner.run(
                                                    owned,
                                                    uploadAndRegister(
                                                            owned,
                                                            storage,
                                                            bucket,
                                                            objectKey,
                                                            spaceId,
                                                            fileName,
                                                            contentType,
                                                            content)),
                                            owned -> Mono.empty(),
                                            (owned, error) -> compensateUpload(owned, storage, bucket, objectKey),
                                            owned -> compensateUpload(owned, storage, bucket, objectKey)))
                                    .doOnNext(document -> triggerIngestion(document.getId(), tenant));
                        }))
                        .contextWrite(TenantContext.withTenant(tenant)))
                .map(assembler::toDocumentDTO);
    }

    /**
     * 登记在线录入正文，并独立触发分块与索引摄取。
     *
     * <p>不访问对象存储；主档初始解析状态为 {@code PARSED}，索引状态为 {@code PENDING}。
     * 保存的 {@code fileSize} 使用正文字符串长度，并非编码后的字节数。
     * 请求字段校验由调用方负责，此方法只检查空间是否启用。
     *
     * @param request 已完成字段校验的录入请求，含空间 ID、标题与正文
     * @return 登记后的文档 DTO，不等待摄取完成；空间无效或持久化失败时发出错误信号
     */
    public Mono<KnowledgeDocumentDTO> registerManual(KnowledgeManualDocumentRequest request) {
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "可信租户上下文缺失")))
                .flatMap(tenant -> requireSpace(request.spaceId())
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
                        .doOnNext(document -> triggerIngestion(document.getId(), tenant))
                        .contextWrite(TenantContext.withTenant(tenant)))
                .map(assembler::toDocumentDTO);
    }

    /**
     * 按仓储筛选条件分页查询文档，并映射为 DTO。
     *
     * @param spaceId 空间过滤，可为 {@code null}
     * @param parseStatus 解析状态过滤，可为 {@code null}
     * @param keyword 标题关键词，可为 {@code null}
     * @param pageNum 传给仓储并回填响应的页码
     * @param pageSize 传给仓储并回填响应的页大小
     * @return 文档分页 DTO；查询失败时传播仓储错误信号
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
     * 查询文档主档与该文档的分块，组装详情及分块预览。
     *
     * @param id 文档 ID
     * @return 详情 DTO；文档不存在发出 {@code NOT_FOUND} 错误信号，查询失败传播相应错误
     */
    public Mono<KnowledgeDocumentDetailDTO> findById(Long id) {
        return requireDocument(id).flatMap(document -> chunkRepository
                .findByDocument(document.getId())
                .map(chunks -> new KnowledgeDocumentDetailDTO(
                        assembler.toDocumentDTO(document),
                        chunks.stream().map(assembler::toChunkDTO).toList())));
    }

    /**
     * 在短 JDBC 事务内记录持久清理意图并逻辑删除主档，使其立即退出普通查询。
     *
     * <p>必须提供可信租户；事务失败整体回滚。已有删除凭证可幂等接受重复请求。
     * 活跃摄取认领未释放时清理 worker 等待租约；主档已删除后禁止摄取继续写库。
     * 实际对象、分块、索引清理及失败重试通过清理进度接口观察，不在本次请求调用远程端点。
     *
     * @param id 文档 ID
     * @return true 表示意图已接受，而非资源已清除；缺租户、无文档/凭证或事务失败发出错误
     */
    public Mono<Boolean> delete(Long id) {
        return lifecycle.acceptDelete(
                id,
                properties.getMeilisearch().getUrl(),
                properties.getMeilisearch().getIndexPrefix() + "kb_chunks");
    }

    /**
     * 更新重建状态并独立触发完整摄取，而非仅重写已有索引条目。
     *
     * <p>已有非空正文时将解析状态设为 {@code PARSED}，摄取管线跳过解析；
     * 无正文时设为 {@code PENDING}。两种情况均在更新请求中将分块数设为零、索引状态设为
     * {@code PENDING}、解析错误设为 {@code null}。后续分块替换与索引提交不与本次主档更新组成事务。
     *
     * @param id 文档 ID
     * @return 状态更新后的文档 DTO，不等待重新摄取或索引任务执行完成；
     *     文档不存在或更新失败时发出错误信号
     */
    public Mono<KnowledgeDocumentDTO> reindex(Long id) {
        return TenantContext.currentTenantIdReactive()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "可信租户上下文缺失")))
                .flatMap(tenant -> lifecycle
                        .requestReindex(id)
                        .doOnNext(document -> triggerIngestion(document.getId(), tenant))
                        .contextWrite(TenantContext.withTenant(tenant)))
                .map(assembler::toDocumentDTO);
    }

    /**
     * 在登记或状态更新后的桥接边界独立订阅摄取管线。
     *
     * <p>返回值不承载摄取结果；使用 {@link Mono#defer} 捕获链构建异常并明确重新装配可信租户，
     * 独立订阅不依赖请求结束后的 ThreadLocal。摄取与校准使用数据库认领协调并发，
     * 失败状态仅对仍有效且未删除的主档写入；持久任务仍需持续扫描与外部资源恢复。
     *
     * @param documentId 已登记的文档 ID
     * @param tenantId 从原请求可信上下文捕获的租户
     */
    private void triggerIngestion(Long documentId, String tenantId) {
        TenantContext.withTenant(Mono.defer(() -> ingestionService.ingest(documentId)))
                .contextWrite(TenantContext.withTenant(tenantId))
                .subscribe(nothing -> {}, error -> log.warn("知识文档摄取管线触发失败: documentId={}", documentId));
    }

    private Mono<KnowledgeDocument> uploadAndRegister(
            KnowledgeDocumentLease lease,
            ObjectStorage storage,
            String bucket,
            String objectKey,
            Long spaceId,
            String fileName,
            String contentType,
            byte[] content) {
        return Mono.defer(() -> storage.upload(bucket, objectKey, new ByteArrayInputStream(content), contentType))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.STORAGE_ERROR, "对象存储写入未确认")))
                .flatMap(key -> lifecycle
                        .bindUploadedKey(lease, key)
                        .then(lifecycle.registerUpload(
                                lease,
                                KnowledgeDocument.builder()
                                        .spaceId(spaceId)
                                        .title(titleOf(fileName))
                                        .sourceType(KnowledgeDocument.SourceType.UPLOAD)
                                        .contentType(contentType == null ? "application/octet-stream" : contentType)
                                        .objectKey(key)
                                        .fileSize((long) content.length)
                                        .parseStatus(KnowledgeDocument.ParseStatus.PENDING)
                                        .indexStatus(KnowledgeDocument.IndexStatus.NONE)
                                        .chunkCount(0)
                                        .build())));
    }

    /** 只补偿本次生成的独占键；失败始终保留 staging 凭证，进程失联由租约到期扫描恢复。 */
    private Mono<Void> compensateUpload(
            KnowledgeDocumentLease lease, ObjectStorage storage, String bucket, String key) {
        return lifecycle.abortUpload(lease).flatMap(aborted -> {
            if (!aborted) return Mono.empty();
            return Mono.defer(() -> storage.delete(bucket, key))
                    .switchIfEmpty(Mono.error(new IllegalStateException("对象补偿未确认")))
                    .flatMap(removed -> lifecycle.failUpload(lease, "上传或登记失败；独占对象补偿已执行，继续复核迟到写入", false))
                    .onErrorResume(error -> lifecycle.failUpload(lease, "上传或登记失败；对象补偿未确认，请重试清理", false));
        });
    }

    /**
     * 要求仓储查询得到文档，避免下游将不存在的文档作为空结果处理。
     *
     * @param id 文档 ID
     * @return 文档主档；仓储空结果转为 {@code NOT_FOUND} 业务错误信号，查询错误原样传播
     */
    private Mono<KnowledgeDocument> requireDocument(Long id) {
        return documentRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "知识文档不存在")));
    }

    /**
     * 要求空间存在且状态为启用值 {@code 1}。
     *
     * @param id 空间 ID
     * @return 可用空间；不存在、状态为空或非启用时发出 {@code NOT_FOUND} 业务错误信号，
     *     查询错误原样传播
     */
    private Mono<KnowledgeSpace> requireSpace(Long id) {
        return spaceRepository
                .findById(id)
                .filter(space -> space.getStatus() != null && space.getStatus() == 1)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "知识空间不存在或已停用")));
    }

    /**
     * 同步校验扩展名白名单及文件字节数，不检查 MIME 类型或文件实际内容。
     *
     * @param extension 小写且不含点的扩展名，按配置集合精确匹配
     * @param sizeBytes 文件字节数，须大于零且不超过配置的 MB 上限乘以 {@code 1024 * 1024}
     * @throws BusinessException 扩展名为空、不受支持或大小无效时同步抛出 {@code PARAM_ERROR}
     */
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

    /**
     * 提取最后一个点之后的文本并转为小写，不对文件名做修剪或类型识别。
     *
     * @param fileName 原始文件名，可为 {@code null}
     * @return 扩展名；文件名为空引用或不含点时返回 {@code null}，以点结尾时返回空字符串
     */
    private String extractExtension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 替换不符合对象键文件名片段规则的字符，并保留最多 128 个末尾字符。
     *
     * @param fileName 原始文件名，可为 {@code null}
     * @return 保留单词字符、指定汉字范围、点及连字符的片段，其余字符替换为下划线；
     *     {@code null} 使用 {@code file}
     */
    private String sanitize(String fileName) {
        String cleaned = fileName == null ? "file" : fileName.replaceAll("[^\\w\\u4e00-\\u9fa5.\\-]", "_");
        return cleaned.length() > 128 ? cleaned.substring(cleaned.length() - 128) : cleaned;
    }

    /**
     * 从原始文件名生成文档标题；最后一个点不在首位时去除扩展名。
     *
     * @param fileName 原始文件名，可为 {@code null}
     * @return 文件标题；{@code null} 使用「未命名文档」，无点或仅有首位点时保留原值
     */
    private String titleOf(String fileName) {
        if (fileName == null) {
            return "未命名文档";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
