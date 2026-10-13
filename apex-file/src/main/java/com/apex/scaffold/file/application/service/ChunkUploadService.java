package com.apex.scaffold.file.application.service;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.chunk.ChunkInitRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkInitResult;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeResult;
import com.apex.scaffold.file.application.dto.chunk.ChunkUploadRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkUploadResult;
import com.apex.scaffold.file.domain.model.entity.FileChunkEntity;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.repository.FileChunkRepository;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.domain.service.StorageService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 分片上传应用服务。
 *
 * <p>为超大文件提供分片上传、断点续传与合片能力。流程分三阶段：
 * <ol>
 *   <li><b>init</b>：登记文件元数据，创建 {@code UPLOADING} 状态的 file_record 主记录并返回 fileId；</li>
 *   <li><b>uploadChunk</b>：逐片上传，分片对象以 {@code chunks/{fileId}/{chunkIndex}} 命名并记录真实对象键，同片重复上传幂等处理；</li>
 *   <li><b>merge</b>：按序号拼接全部分片为完整文件，更新主记录为 {@code STORED} 并清理分片。</li>
 * </ol>
 *
 * <p>通用分片不经过 {@link FileService}（其仅放行图片类型），直接依赖 {@link StorageService}
 * 以支持任意文件类型（{@code application/octet-stream} 已由存储门面放行）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see StorageService
 * @see FileChunkRepository
 */
@Service
public class ChunkUploadService {

    private static final String CHUNK_CONTENT_TYPE = "application/octet-stream";

    private final StorageService storageService;
    private final FileRepository fileRepository;
    private final FileChunkRepository fileChunkRepository;
    private final String defaultBucketName;
    private final int presignedUrlExpireSeconds;

    /**
     * 创建分片上传应用服务。
     *
     * @param storageService           由 {@code storage.default-type} 选出的默认存储门面
     * @param fileRepository           文件记录仓储
     * @param fileChunkRepository      分片记录仓储
     * @param defaultBucketName        服务端固定存储桶名称
     * @param presignedUrlExpireSeconds 预签名 URL 有效期，单位秒
     */
    public ChunkUploadService(
            StorageService storageService,
            FileRepository fileRepository,
            FileChunkRepository fileChunkRepository,
            @Value("${storage.bucket-name:apex}") String defaultBucketName,
            @Value("${storage.presigned-url-expire-seconds:300}") int presignedUrlExpireSeconds) {
        this.storageService = storageService;
        this.fileRepository = fileRepository;
        this.fileChunkRepository = fileChunkRepository;
        this.defaultBucketName = defaultBucketName;
        this.presignedUrlExpireSeconds = presignedUrlExpireSeconds;
    }

    /**
     * 初始化分片上传。
     *
     * <p>校验入参后创建 {@code UPLOADING} 状态的 file_record 主记录，返回文件主键与建议分片大小，
     * 供客户端开始逐片上传；分片大小缺省时按 {@code fileSize / totalChunks} 向上取整。</p>
     *
     * @param request 初始化请求
     * @return 初始化结果；分片大小为空的字段为 {@code null}
     */
    public Mono<ChunkInitResult> init(ChunkInitRequest request) {
        if (request.getChunkSize() == null) {
            request.setChunkSize(resolveChunkSize(request.getFileSize(), request.getTotalChunks()));
        }
        LocalDateTime now = LocalDateTime.now();
        FileEntity file = new FileEntity();
        file.setOriginalName(request.getFileName());
        file.setFileSize(request.getFileSize());
        file.setContentType(request.getContentType());
        file.setMd5(request.getMd5());
        file.setBucketName(defaultBucketName);
        file.setBusinessType("CHUNK");
        file.setStorageStatus("UPLOADING");
        file.setUserId(currentUserId());
        file.setCreateTime(now);
        file.setUpdateTime(now);
        return fileRepository
                .save(file)
                .map(saved -> new ChunkInitResult(
                        saved.getId(), defaultBucketName, request.getChunkSize(), request.getTotalChunks(), List.of()));
    }

    /**
     * 上传单个分片。
     *
     * <p>分片二进制经请求体直接传入。若指定分片已存在（幂等）则直接返回既有对象键，
     * 否则写入对象存储并入库分片元数据。</p>
     *
     * @param request    分片元数据
     * @param content    分片二进制内容
     * @return 上传结果；{@code uploaded=false} 表示命中已存在分片
     */
    public Mono<ChunkUploadResult> uploadChunk(ChunkUploadRequest request, byte[] content) {
        if (content == null || content.length == 0) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片内容不能为空"));
        }
        return fileRepository
                .findById(request.getFileId())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "文件记录不存在")))
                .flatMap(file -> fileChunkRepository
                        .findByFileIdAndIndex(request.getFileId(), request.getChunkIndex())
                        .flatMap(existing -> Mono.just(new ChunkUploadResult(
                                request.getFileId(),
                                request.getChunkIndex(),
                                false,
                                existing.getObjectName(),
                                existing.getChunkSize())))
                        .switchIfEmpty(doUploadChunk(request, content)));
    }

    /**
     * 合并分片为完整文件。
     *
     * <p>校验分片完整性后按序号下载全部分片并拼接，上传为最终文件，随后更新
     * file_record 为 {@code STORED}、生成访问 URL 并清理已用分片。</p>
     *
     * @param request 合并请求
     * @return 合并结果（对象定位信息与临时访问 URL）
     */
    public Mono<ChunkMergeResult> merge(ChunkMergeRequest request) {
        return fileRepository
                .findById(request.getFileId())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "文件记录不存在")))
                .flatMap(file -> fileChunkRepository
                        .findByFileIdOrderByIndex(request.getFileId())
                        .collectList()
                        .flatMap(chunks -> doMerge(file, chunks, request)));
    }

    /**
     * 查询分片上传进度（断点续传）。
     *
     * <p>返回已成功上传的分片序号列表与分片大小信息，客户端据此跳过已完成分片。</p>
     *
     * @param fileId 文件记录主键
     * @return 上传进度；文件不存在时 Mono 以 BusinessException 错误终止
     */
    public Mono<ChunkInitResult> status(Long fileId) {
        return fileRepository
                .findById(fileId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "文件记录不存在")))
                .flatMap(file -> fileChunkRepository
                        .findByFileIdOrderByIndex(fileId)
                        .collectList()
                        .map(chunks -> new ChunkInitResult(
                                fileId,
                                defaultBucketName,
                                chunks.isEmpty() ? null : chunks.get(0).getChunkSize(),
                                chunks.isEmpty() ? null : chunks.get(0).getTotalChunks(),
                                chunks.stream()
                                        .map(FileChunkEntity::getChunkIndex)
                                        .collect(Collectors.toList()))));
    }

    /**
     * 执行分片写入：上传对象存储并入库分片元数据。
     *
     * @param request 分片元数据
     * @param content 分片二进制内容
     * @return 新上传的分片结果
     */
    private Mono<ChunkUploadResult> doUploadChunk(ChunkUploadRequest request, byte[] content) {
        String objectName = "chunks/" + request.getFileId() + "/" + request.getChunkIndex();
        return storageService
                .upload(defaultBucketName, objectName, new ByteArrayInputStream(content), CHUNK_CONTENT_TYPE)
                .flatMap(storedObjectName -> fileChunkRepository
                        .save(FileChunkEntity.stored(
                                request.getFileId(),
                                request.getChunkIndex(),
                                (long) content.length,
                                request.getTotalChunks(),
                                request.getMd5(),
                                storedObjectName,
                                defaultBucketName,
                                currentUserId()))
                        .map(saved -> new ChunkUploadResult(
                                request.getFileId(),
                                request.getChunkIndex(),
                                true,
                                saved.getObjectName(),
                                saved.getChunkSize())));
    }

    /**
     * 执行分片合并主流程。
     *
     * @param file    文件主记录
     * @param chunks  已按序号排序的全部分片
     * @param request 合并请求
     * @return 合并结果
     */
    private Mono<ChunkMergeResult> doMerge(FileEntity file, List<FileChunkEntity> chunks, ChunkMergeRequest request) {
        if (chunks.isEmpty()) {
            return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "尚未上传任何分片"));
        }
        Integer totalChunks = chunks.get(0).getTotalChunks();
        if (totalChunks == null || chunks.size() != totalChunks) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "分片不完整，已上传 " + chunks.size() + "/" + totalChunks);
        }
        String finalName = resolveFinalName(file, request.getFileName());
        String finalContentType = resolveContentType(file, request.getContentType());
        long fileSize = chunks.stream()
                .mapToLong(c -> c.getChunkSize() == null ? 0L : c.getChunkSize())
                .sum();
        return downloadAll(chunks)
                .flatMap(streams ->
                        storageService.upload(defaultBucketName, finalName, mergeStreams(streams), finalContentType))
                .flatMap(storedObjectName -> updateStoredFile(
                                file, storedObjectName, fileSize, finalContentType, request.getMd5())
                        .flatMap(saved -> storageService
                                .getPresignedUrl(defaultBucketName, storedObjectName, presignedUrlExpireSeconds)
                                .map(url -> new ChunkMergeResult(
                                        file.getId(),
                                        defaultBucketName,
                                        storedObjectName,
                                        url,
                                        fileSize,
                                        request.getMd5()))))
                .flatMap(result -> cleanupChunks(chunks).thenReturn(result));
    }

    /**
     * 按序下载全部分片。
     *
     * @param chunks 分片列表（升序）
     * @return 分片输入流列表（调用方负责关闭）
     */
    private Mono<List<InputStream>> downloadAll(List<FileChunkEntity> chunks) {
        return Flux.fromIterable(chunks)
                .concatMap(chunk -> storageService.download(chunk.getBucketName(), chunk.getObjectName()))
                .collectList();
    }

    /**
     * 拼接多个分片流为单一输入流。
     *
     * @param streams 按片序排列的输入流
     * @return 合并后的输入流
     */
    @SuppressWarnings("deprecation")
    private InputStream mergeStreams(List<InputStream> streams) {
        return new java.io.SequenceInputStream(Collections.enumeration(streams));
    }

    /**
     * 将 file_record 主记录更新为已存储状态。
     *
     * @param file             主记录实体
     * @param storedObjectName 最终对象键
     * @param fileSize         文件大小
     * @param contentType      内容类型
     * @param md5              文件 MD5，可为空
     * @return 更新后的主记录
     */
    private Mono<FileEntity> updateStoredFile(
            FileEntity file, String storedObjectName, long fileSize, String contentType, String md5) {
        file.setFileName(storedObjectName);
        file.setFileSize(fileSize);
        file.setContentType(contentType);
        if (md5 != null && !md5.isBlank()) {
            file.setMd5(md5);
        }
        file.setStorageStatus("STORED");
        file.setUpdateTime(LocalDateTime.now());
        return fileRepository.save(file);
    }

    /**
     * 清理已用分片：删除对象存储中的分片对象并逻辑删除分片元数据。
     *
     * @param chunks 全部分片
     * @return 完成信号
     */
    private Mono<Void> cleanupChunks(List<FileChunkEntity> chunks) {
        Long fileId = chunks.get(0).getFileId();
        return Flux.fromIterable(chunks)
                .flatMap(chunk -> storageService
                        .delete(chunk.getBucketName(), chunk.getObjectName())
                        .onErrorReturn(false))
                .then(fileChunkRepository.deleteByFileId(fileId));
    }

    /**
     * 解析最终文件名：请求显式指定则使用，否则沿用初始化登记的文件名。
     *
     * @param file      文件主记录
     * @param requested 请求携带的文件名，可为空
     * @return 最终文件名
     */
    private String resolveFinalName(FileEntity file, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        if (file.getOriginalName() != null && !file.getOriginalName().isBlank()) {
            return file.getOriginalName();
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "缺少最终文件名");
    }

    /**
     * 解析最终内容类型：请求显式指定则使用，否则沿用初始化登记值，最后回退为通用二进制类型。
     *
     * @param file      文件主记录
     * @param requested 请求携带的内容类型，可为空
     * @return 最终内容类型
     */
    private String resolveContentType(FileEntity file, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        if (file.getContentType() != null && !file.getContentType().isBlank()) {
            return file.getContentType();
        }
        return CHUNK_CONTENT_TYPE;
    }

    /**
     * 当分片大小未显式指定时，按文件大小与分片数向上取整计算。
     *
     * @param fileSize    文件总大小
     * @param totalChunks 分片总数
     * @return 建议分片大小
     */
    private long resolveChunkSize(Long fileSize, Integer totalChunks) {
        if (fileSize == null || fileSize <= 0 || totalChunks == null || totalChunks <= 0) {
            return 5 * 1024 * 1024L;
        }
        return (fileSize + totalChunks - 1) / totalChunks;
    }

    /**
     * 解析当前用户 ID；文件服务上下文中租户未装配时返回 {@code null}。
     *
     * @return 当前用户 ID
     */
    private Long currentUserId() {
        return TenantContext.currentTenantId().map(Long::valueOf).orElse(null);
    }
}
