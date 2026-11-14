package com.apex.scaffold.file.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.file.application.dto.chunk.*;
import com.apex.scaffold.file.domain.model.entity.FileChunkEntity;
import com.apex.scaffold.file.domain.model.entity.FileEntity;
import com.apex.scaffold.file.domain.repository.FileChunkRepository;
import com.apex.scaffold.file.domain.repository.FileRepository;
import com.apex.scaffold.file.domain.service.StorageService;
import com.apex.scaffold.file.domain.service.StorageServiceProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 分片上传应用服务，依据持久化归属授权并按实际提供商读写，失败补偿不构成云存储与数据库原子事务。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Service
@Slf4j
public class ChunkUploadService {
    private static final String UPLOADING = "UPLOADING";
    private static final String STORED = "STORED";
    private static final int MAX_TOTAL_CHUNKS = 100_000;
    private static final long MAX_CHUNK_SIZE = 64L * 1024 * 1024;
    private static final String MD5_PATTERN = "^[a-fA-F0-9]{32}$";
    private final StorageServiceProvider provider;
    private final FileRepository files;
    private final FileChunkRepository chunks;
    private final String bucket;
    private final String defaultType;
    private final int expiry;
    private final long maxChunkBytes;
    private final int maxTotalChunks;

    /**
     * 测试与程序化装配入口：采用安全默认限制的分片服务。
     *
     * @param provider 实际类型路由器
     * @param files 文件记录仓储
     * @param chunks 分片仓储
     * @param bucket 服务端默认桶
     * @param expiry 签名有效秒数
     * @param defaultType 初始化时的提供商类型
     * @return 构建完成的服务实例
     */
    public static ChunkUploadService forTesting(
            StorageServiceProvider provider,
            FileRepository files,
            FileChunkRepository chunks,
            String bucket,
            int expiry,
            String defaultType) {
        return new ChunkUploadService(
                provider, files, chunks, bucket, expiry, defaultType, MAX_CHUNK_SIZE, MAX_TOTAL_CHUNKS);
    }

    /**
     * 配置化构造器（唯一 public 构造器，Spring 按唯一构造器注入，无需 {@code @Autowired}）。
     *
     * @param provider 实际类型路由器
     * @param files 文件记录仓储
     * @param chunks 分片仓储
     * @param bucket 服务端默认桶
     * @param expiry 签名有效秒数
     * @param defaultType 初始化时的提供商类型
     * @param maxChunkBytes 单分片最大字节数
     * @param maxTotalChunks 分片总数上限
     */
    public ChunkUploadService(
            StorageServiceProvider provider,
            FileRepository files,
            FileChunkRepository chunks,
            @Value("${storage.bucket-name:apex}") String bucket,
            @Value("${storage.presigned-url-expire-seconds:300}") int expiry,
            @Value("${storage.default-type:local}") String defaultType,
            @Value("${file.upload.max-chunk-bytes:67108864}") long maxChunkBytes,
            @Value("${file.upload.max-total-chunks:100000}") int maxTotalChunks) {
        this.provider = provider;
        this.files = files;
        this.chunks = chunks;
        this.bucket = bucket;
        this.expiry = expiry;
        this.defaultType = defaultType;
        this.maxChunkBytes = maxChunkBytes;
        this.maxTotalChunks = maxTotalChunks;
    }

    /** @param request 初始化参数 @return 带可信归属的上传会话 */
    public Mono<ChunkInitResult> init(ChunkInitRequest request) {
        return FileAccessContext.current().flatMap(identity -> {
            if (request.getFileSize() == null
                    || request.getFileSize() <= 0
                    || request.getTotalChunks() == null
                    || request.getTotalChunks() <= 0
                    || request.getTotalChunks() > maxTotalChunks) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "文件大小与分片数必须在允许范围内"));
            }
            if (request.getChunkSize() != null
                    && (request.getChunkSize() <= 0 || request.getChunkSize() > maxChunkBytes)) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片大小超出允许范围"));
            }
            if (request.getMd5() != null && !request.getMd5().matches(MD5_PATTERN)) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "文件 MD5 格式不合法"));
            }
            long expectedChunkSize = (request.getFileSize() + request.getTotalChunks() - 1) / request.getTotalChunks();
            long chunkSize = request.getChunkSize() == null ? expectedChunkSize : request.getChunkSize();
            if (chunkSize > maxChunkBytes
                    || chunkSize > Long.MAX_VALUE / request.getTotalChunks()
                    || chunkSize * (long) request.getTotalChunks() < request.getFileSize()) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片大小与文件大小不匹配"));
            }
            FileEntity file = new FileEntity();
            file.setTenantId(identity.tenantId());
            file.setUserId(identity.userId());
            file.setStorageType(defaultType);
            file.setOriginalName(request.getFileName());
            file.setFileSize(request.getFileSize());
            file.setContentType(request.getContentType());
            file.setMd5(request.getMd5());
            file.setBucketName(bucket);
            file.setBusinessType("CHUNK");
            file.setStorageStatus("UPLOADING");
            file.setCreateTime(LocalDateTime.now());
            file.setUpdateTime(LocalDateTime.now());
            provider.getStorageService(defaultType);
            long size = request.getChunkSize() == null
                    ? (request.getFileSize() + request.getTotalChunks() - 1) / request.getTotalChunks()
                    : request.getChunkSize();
            file.setTotalChunks(request.getTotalChunks());
            file.setChunkSize(size);
            return files.save(file)
                    .map(saved ->
                            new ChunkInitResult(saved.getId(), bucket, size, request.getTotalChunks(), List.of()));
        });
    }

    /** @param request 分片参数 @param content 分片内容 @return 授权后的上传结果 */
    public Mono<ChunkUploadResult> uploadChunk(ChunkUploadRequest request, byte[] content) {
        if (request == null
                || content == null
                || content.length == 0
                || request.getFileId() == null
                || request.getFileId() <= 0
                || request.getChunkIndex() == null
                || request.getTotalChunks() == null
                || request.getTotalChunks() <= 0
                || request.getTotalChunks() > maxTotalChunks
                || request.getChunkIndex() < 0
                || request.getChunkIndex() >= request.getTotalChunks()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片参数或内容不合法"));
        }
        return authorized(request.getFileId()).flatMap(file -> {
            if (!UPLOADING.equals(file.getStorageStatus())) {
                return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "上传会话已结束"));
            }
            if (!Objects.equals(file.getTotalChunks(), request.getTotalChunks())) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片总数与初始化约定不一致"));
            }
            if (file.getChunkSize() == null || file.getChunkSize() <= 0 || content.length > file.getChunkSize()) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片大小超过初始化约定"));
            }
            long previousBytes = (long) request.getChunkIndex() * file.getChunkSize();
            long remainingBytes = file.getFileSize() == null ? -1 : file.getFileSize() - previousBytes;
            if (remainingBytes <= 0 || content.length > remainingBytes) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片内容超过文件大小约定"));
            }
            String calculatedMd5 = md5Hex(content);
            if (request.getMd5() != null && !request.getMd5().isBlank()) {
                if (!request.getMd5().matches(MD5_PATTERN) || !request.getMd5().equalsIgnoreCase(calculatedMd5)) {
                    return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片 MD5 校验失败"));
                }
            }
            ChunkUploadRequest normalizedRequest = ChunkUploadRequest.builder()
                    .fileId(request.getFileId())
                    .chunkIndex(request.getChunkIndex())
                    .totalChunks(request.getTotalChunks())
                    .md5(calculatedMd5)
                    .build();
            return chunks.findByFileIdAndIndex(file.getId(), request.getChunkIndex())
                    .flatMap(existing -> Mono.just(existingChunkResult(file, normalizedRequest, existing)))
                    .switchIfEmpty(Mono.defer(() -> storeChunk(file, normalizedRequest, content)));
        });
    }

    /** @param file 主记录 @param request 已校验元数据 @param existing 已存分片 @return 幂等上传结果 */
    private ChunkUploadResult existingChunkResult(
            FileEntity file, ChunkUploadRequest request, FileChunkEntity existing) {
        validateChunk(file, existing);
        if (!Objects.equals(existing.getTotalChunks(), request.getTotalChunks())
                || !Objects.equals(existing.getChunkSize(), (long)
                        (request.getChunkIndex() == file.getTotalChunks() - 1
                                ? file.getFileSize() - (long) request.getChunkIndex() * file.getChunkSize()
                                : file.getChunkSize()))
                || (existing.getMd5() != null && !Objects.equals(existing.getMd5(), request.getMd5()))) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "重复分片内容与已保存分片不一致");
        }
        return new ChunkUploadResult(
                file.getId(), request.getChunkIndex(), false, existing.getObjectName(), existing.getChunkSize());
    }

    /** @param file 主记录 @param request 分片参数 @param content 内容 @return 落库成功的实际对象定位 */
    private Mono<ChunkUploadResult> storeChunk(FileEntity file, ChunkUploadRequest request, byte[] content) {
        StorageService storage = provider.getStorageService(file.getStorageType());
        String key = "chunks/" + file.getId() + "/" + UUID.randomUUID();
        return storage.upload(file.getBucketName(), key, new ByteArrayInputStream(content), "application/octet-stream")
                .flatMap(actual -> {
                    FileChunkEntity chunk = FileChunkEntity.stored(
                            file.getId(),
                            request.getChunkIndex(),
                            (long) content.length,
                            request.getTotalChunks(),
                            request.getMd5(),
                            actual,
                            file.getBucketName(),
                            file.getUserId());
                    chunk.setTenantId(file.getTenantId());
                    chunk.setStorageType(file.getStorageType());
                    return Mono.defer(() -> chunks.saveIfAbsent(chunk))
                            .switchIfEmpty(Mono.error(new IllegalStateException("分片记录未保存")))
                            .flatMap(saved -> {
                                boolean winner = Objects.equals(saved.getObjectName(), actual);
                                if (winner) {
                                    return Mono.just(new ChunkUploadResult(
                                            file.getId(),
                                            saved.getChunkIndex(),
                                            true,
                                            saved.getObjectName(),
                                            saved.getChunkSize()));
                                }
                                return FileService.deleteConfirmed(storage, file.getBucketName(), actual)
                                        .thenReturn(new ChunkUploadResult(
                                                file.getId(),
                                                saved.getChunkIndex(),
                                                false,
                                                saved.getObjectName(),
                                                saved.getChunkSize()));
                            });
                });
    }

    /** @param request 合并参数 @return 完整文件定位，缺失任何对象时失败 */
    public Mono<ChunkMergeResult> merge(ChunkMergeRequest request) {
        return authorized(request.getFileId()).flatMap(file -> chunks.findByFileIdOrderByIndex(file.getId())
                .collectList()
                .flatMap(parts -> merge(file, parts, request)));
    }

    /** @param fileId 文件记录编号 @return 仅向文件所有者返回上传进度 */
    public Mono<ChunkInitResult> status(Long fileId) {
        return authorized(fileId).flatMap(file -> chunks.findByFileIdOrderByIndex(fileId)
                .collectList()
                .map(parts -> {
                    parts.forEach(part -> validateChunk(file, part));
                    return new ChunkInitResult(
                            fileId,
                            file.getBucketName(),
                            file.getChunkSize(),
                            file.getTotalChunks(),
                            parts.stream().map(FileChunkEntity::getChunkIndex).toList());
                }));
    }

    /** @param id 文件编号 @return 已完成租户、用户和历史归属验证的记录 */
    private Mono<FileEntity> authorized(Long id) {
        return FileAccessContext.current().flatMap(identity -> files.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "文件记录不存在")))
                .map(file -> {
                    identity.authorize(file);
                    return file;
                }));
    }

    /** @param file 主记录 @param part 分片记录 @throws BusinessException 归属或实际存储定位不一致 */
    private void validateChunk(FileEntity file, FileChunkEntity part) {
        if (!Objects.equals(file.getId(), part.getFileId())
                || !Objects.equals(file.getTenantId(), part.getTenantId())
                || !Objects.equals(file.getUserId(), part.getUserId())
                || !Objects.equals(file.getStorageType(), part.getStorageType())
                || !Objects.equals(file.getBucketName(), part.getBucketName())
                || part.getObjectName() == null
                || !STORED.equals(part.getStorageStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "分片归属或定位不完整");
        }
    }

    /** @param file 主记录 @param parts 有序分片 @param request 合并参数 @return 保存、签名与清理后的结果 */
    private Mono<ChunkMergeResult> merge(FileEntity file, List<FileChunkEntity> parts, ChunkMergeRequest request) {
        if (!UPLOADING.equals(file.getStorageStatus())
                || parts.isEmpty()
                || file.getTotalChunks() == null
                || parts.size() != file.getTotalChunks()) {
            return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "分片不完整或会话已结束"));
        }
        for (int index = 0; index < parts.size(); index++) {
            FileChunkEntity part = parts.get(index);
            validateChunk(file, part);
            if (!Objects.equals(part.getChunkIndex(), index) || !Objects.equals(part.getTotalChunks(), parts.size())) {
                return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "分片序号不连续或总数不一致"));
            }
        }
        if (!Objects.equals(
                file.getFileSize(),
                parts.stream().mapToLong(FileChunkEntity::getChunkSize).sum())) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分片大小与初始化约定不一致"));
        }
        String name = request.getFileName() == null || request.getFileName().isBlank()
                ? file.getOriginalName()
                : request.getFileName();
        if (name == null || name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "最终文件名不合法"));
        }
        String type =
                request.getContentType() == null || request.getContentType().isBlank()
                        ? file.getContentType()
                        : request.getContentType();
        if (type == null || type.isBlank()) {
            type = "application/octet-stream";
        }
        String requestedMd5 = request.getMd5() == null || request.getMd5().isBlank() ? file.getMd5() : request.getMd5();
        if (requestedMd5 != null && !requestedMd5.matches(MD5_PATTERN)) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "文件 MD5 格式不合法"));
        }
        StorageService storage = provider.getStorageService(file.getStorageType());
        String key = UUID.randomUUID() + "-" + name;
        String finalContentType = type;
        return files.claimMerge(file).flatMap(claimed -> {
            if (!claimed) {
                return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "会话正在合并或已结束"));
            }
            file.setStorageStatus("MERGING");
            return uploadMergedObject(file, parts, storage, key, finalContentType)
                    .flatMap(upload -> {
                        if (requestedMd5 != null && !requestedMd5.equalsIgnoreCase(upload.md5())) {
                            BusinessException error = new BusinessException(ResultCode.PARAM_ERROR, "文件 MD5 校验失败");
                            return storage.delete(file.getBucketName(), upload.objectName())
                                    .defaultIfEmpty(false)
                                    .flatMap(deleted -> deleted ? releaseFailedMerge(file, error) : Mono.error(error));
                        }
                        file.setFileName(upload.objectName());
                        file.setFilePath(upload.objectName());
                        file.setContentType(finalContentType);
                        file.setMd5(upload.md5());
                        file.setStorageStatus("STORED");
                        file.setUpdateTime(LocalDateTime.now());
                        return Mono.defer(() -> files.save(file))
                                .switchIfEmpty(Mono.error(new IllegalStateException("合并记录未保存")))
                                .onErrorResume(error -> FileService.<FileEntity>compensate(
                                                storage, files, file, upload.objectName(), error)
                                        .onErrorResume(original ->
                                                recoverFailedObject(file, storage, upload.objectName(), original)))
                                .flatMap(saved -> cleanup(parts)
                                        .then(Mono.defer(() -> FileService.accessUrl(saved, storage, expiry)))
                                        .map(url -> new ChunkMergeResult(
                                                saved.getId(),
                                                saved.getBucketName(),
                                                upload.objectName(),
                                                url,
                                                saved.getFileSize(),
                                                saved.getMd5())));
                    })
                    .onErrorResume(error -> "MERGING".equals(file.getStorageStatus())
                            ? releaseFailedMerge(file, error)
                            : Mono.error(error));
        });
    }

    private record MergedObject(String objectName, String md5) {}

    /** 在上传流消费过程中计算最终文件 MD5。 */
    private Mono<MergedObject> uploadMergedObject(
            FileEntity file, List<FileChunkEntity> parts, StorageService storage, String key, String contentType) {
        return Mono.using(
                ArrayList<InputStream>::new,
                streams -> Flux.fromIterable(parts)
                        .concatMap(part -> storage.download(part.getBucketName(), part.getObjectName())
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "分片对象缺失")))
                                .doOnNext(streams::add))
                        .onErrorResume(error -> releaseFailedMerge(file, error))
                        .then(Mono.defer(() -> {
                            try {
                                MessageDigest digest = MessageDigest.getInstance("MD5");
                                InputStream merged = new java.security.DigestInputStream(
                                        new SequenceInputStream(Collections.enumeration(streams)), digest);
                                return storage.upload(file.getBucketName(), key, merged, contentType)
                                        .switchIfEmpty(Mono.error(new IllegalStateException("合并对象未返回定位")))
                                        .map(actual -> new MergedObject(actual, md5Hex(digest.digest())))
                                        .onErrorResume(error -> recoverFailedObject(file, storage, key, error));
                            } catch (NoSuchAlgorithmException error) {
                                return Mono.error(new IllegalStateException("JVM 不支持 MD5", error));
                            }
                        }))
                        .subscribeOn(BlockingIoSchedulers.storage()),
                ChunkUploadService::closeStreams);
    }

    /** 计算 MD5 hex 字符串。 */
    private static String md5Hex(byte[] digest) {
        StringBuilder result = new StringBuilder(32);
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    /** @param file 合并会话 @param storage 实际提供商 @param key 最终对象键 @param error 原始失败 @param <T> 结果类型 @return 确认无最终对象才释放会话 */
    private <T> Mono<T> recoverFailedObject(FileEntity file, StorageService storage, String key, Throwable error) {
        return Mono.defer(() -> storage.exists(file.getBucketName(), key))
                .defaultIfEmpty(true)
                .onErrorResume(checkError -> {
                    if (checkError != error) {
                        error.addSuppressed(checkError);
                    }
                    return Mono.just(true);
                })
                .flatMap(exists -> {
                    if (!exists) {
                        return releaseFailedMerge(file, error);
                    }
                    log.error(
                            "合并结果未确认，保留 MERGING，须核对对象后人工恢复: fileId={}, type={}, bucket={}, object={}",
                            file.getId(),
                            file.getStorageType(),
                            file.getBucketName(),
                            key,
                            error);
                    return Mono.error(error);
                });
    }

    /** @param file 无最终对象的会话 @param error 原始失败 @param <T> 结果类型 @return 释放合并权后仍传播原始失败 */
    private <T> Mono<T> releaseFailedMerge(FileEntity file, Throwable error) {
        return Mono.defer(() -> files.releaseMerge(file))
                .defaultIfEmpty(false)
                .onErrorResume(releaseError -> {
                    if (releaseError != error) {
                        error.addSuppressed(releaseError);
                    }
                    return Mono.just(false);
                })
                .flatMap(released -> {
                    if (released) {
                        file.setStorageStatus(UPLOADING);
                    } else {
                        log.error("释放合并会话失败，须核对持久化状态后人工恢复: fileId={}", file.getId(), error);
                    }
                    return Mono.error(error);
                });
    }

    /** @param streams 已打开的分片流，失败与取消时也必须关闭 */
    private static void closeStreams(List<InputStream> streams) {
        for (InputStream stream : streams) {
            try {
                stream.close();
            } catch (java.io.IOException error) {
                log.warn("关闭分片流失败", error);
            }
        }
    }

    /**
     * 重试清理已完成会话的分片对象。
     *
     * <p>仅文件属主可重试；删除失败时保留分片元数据，供下一次重试继续确认。
     *
     * @param fileId 已完成会话编号
     */
    public Mono<Void> retryCleanup(Long fileId) {
        return authorized(fileId).flatMap(file -> {
            if (!STORED.equals(file.getStorageStatus())) {
                return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "仅已完成会话可清理分片"));
            }
            return chunks.findByFileIdOrderByIndex(fileId).collectList().flatMap(parts -> {
                parts.forEach(part -> validateChunk(file, part));
                return cleanup(parts);
            });
        });
    }

    /**
     * 删除已合并分片对应的存储对象与元数据。
     *
     * <p>全部对象确认删除后才移除元数据；任一删除失败即保留记录供后续重试。
     *
     * @param parts 已合并分片
     */
    private Mono<Void> cleanup(List<FileChunkEntity> parts) {
        if (parts.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(parts)
                .concatMap(part -> FileService.deleteConfirmed(
                                provider.getStorageService(part.getStorageType()),
                                part.getBucketName(),
                                part.getObjectName())
                        .onErrorReturn(false))
                .collectList()
                .flatMap(results -> results.stream().allMatch(Boolean.TRUE::equals)
                        ? chunks.deleteByFileId(parts.get(0).getFileId())
                        : Mono.empty());
    }
}
