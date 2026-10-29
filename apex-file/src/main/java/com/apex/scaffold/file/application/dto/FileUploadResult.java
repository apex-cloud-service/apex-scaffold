package com.apex.scaffold.file.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 文件上传结果。
 *
 * <p>同时返回稳定的对象定位信息、临时访问地址和六维美学分析结果，
 * 避免客户端从带签名的 URL 中反向解析存储桶与对象名称。
 * 后续创建媒体样本或文件消息时应优先提交 {@code bucketName} 与 {@code fileName}。
 * v2.1 新增照片异步接入相关字段（fileId/photoId/processingTaskId/processingStatus）。</p>
 *
 * @param bucketName      服务端配置的存储桶名称
 * @param fileName        存储后生成的唯一对象名称
 * @param url             云预签名 URL 或需认证授权的 Local 内容地址
 * @param expiresIn       云签名有效期，单位秒；Local 为 0，表示不适用签名 TTL
 * @param aesthetic       FG-CLIP2 六维美学分析结果（分析失败时为 null）
 * @param fileId          文件记录主键（六维分析失败时为 null）
 * @param photoId         照片记录主键（六维分析失败时为 null）
 * @param processingTaskId 异步接入任务 ID（六维分析失败时为 null）
 * @param processingStatus 异步接入状态：PENDING/READY（六维分析失败时为 null）
 * @param moderationTaskId 人工审核任务 ID（无需人工审核时为 null）
 * @param moderationStatus 审核状态：WAITING_REVIEW/APPROVED/REJECTED
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "FileUploadResult", description = "文件上传结果")
public record FileUploadResult(
        @Schema(description = "服务端配置的存储桶名称", example = "apex") String bucketName,
        @Schema(description = "存储后生成的唯一对象名称", example = "0f12ab34cd56ef78.jpg") String fileName,
        @Schema(description = "云存储返回临时预签名 URL；Local 返回需携带认证凭证的 /api/v1/file/content 地址") String url,
        @Schema(description = "云签名有效期，单位秒；Local 为 0，表示不适用签名 TTL，每次访问仍须认证授权", example = "300") int expiresIn,
        @Schema(description = "FG-CLIP2 六维美学分析结果（分析失败时为 null）") Map<String, Object> aesthetic,
        @Schema(description = "文件记录主键（非投票上传时为 null）") Long fileId,
        @Schema(description = "照片记录主键（非投票上传时为 null）") Long photoId,
        @Schema(description = "异步接入任务 ID（非投票上传时为 null）") Long processingTaskId,
        @Schema(description = "异步接入状态：WAITING_REVIEW/PENDING/READY（非投票上传时为 null）") String processingStatus,
        @Schema(description = "人工审核任务 ID（无需人工审核时为 null）") Long moderationTaskId,
        @Schema(description = "审核状态：WAITING_REVIEW/APPROVED/REJECTED") String moderationStatus) {

    /**
     * 构造用于非投票上传或六维分析降级场景的结果（fileId/photoId 等新增字段为 null）。
     * 六维分析成功时请使用全参构造器以便返回异步接入标识。
     *
     * @param bucketName 存储桶名称
     * @param fileName   对象名称
     * @param url        临时访问 URL
     * @param expiresIn  URL 有效期
     * @param aesthetic  六维分析结果，可为 null
     */
    public FileUploadResult(
            String bucketName, String fileName, String url, int expiresIn, Map<String, Object> aesthetic) {
        this(bucketName, fileName, url, expiresIn, aesthetic, null, null, null, null, null, null);
    }

    /**
     * 兼容原有投票照片结果构造器（不含审核字段）。
     *
     * <p>用于从旧版投票上传流程迁移的兼容入口，不包含 {@code moderationTaskId} 和
     * {@code moderationStatus} 参数，两者会被默认置为 {@code null}。</p>
     *
     * @param bucketName       存储桶名称
     * @param fileName         对象名称
     * @param url              临时访问 URL
     * @param expiresIn        URL 有效期
     * @param aesthetic        六维分析结果，可为 null
     * @param fileId           文件记录主键
     * @param photoId          照片记录主键
     * @param processingTaskId 异步接入任务 ID
     * @param processingStatus 异步接入状态
     */
    public FileUploadResult(
            String bucketName,
            String fileName,
            String url,
            int expiresIn,
            Map<String, Object> aesthetic,
            Long fileId,
            Long photoId,
            Long processingTaskId,
            String processingStatus) {
        this(
                bucketName,
                fileName,
                url,
                expiresIn,
                aesthetic,
                fileId,
                photoId,
                processingTaskId,
                processingStatus,
                null,
                null);
    }
}
