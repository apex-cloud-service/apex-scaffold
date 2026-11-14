package com.apex.scaffold.file.application.dto.chunk;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分片合并结果。
 *
 * <p>合并成功后返回最终对象定位信息与访问地址，客户端可用于后续业务引用。
 * 云 URL 的签名有效秒数由 storage.presigned-url-expire-seconds 配置；Local 地址每次访问
 * 仍须认证授权，不适用签名 TTL。</p>
 *
 * @param fileId     文件记录主键
 * @param bucketName 服务端配置的存储桶名称
 * @param fileName   合并后存储的唯一对象名称
 * @param url        临时访问 URL
 * @param fileSize   合并后的文件大小（字节）
 * @param md5        文件 MD5（客户端未提交时为 null）
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "ChunkMergeResult", description = "分片合并结果")
public record ChunkMergeResult(
        @Schema(description = "文件记录主键") Long fileId,
        @Schema(description = "服务端配置的存储桶名称", example = "apex") String bucketName,
        @Schema(description = "合并后存储的唯一对象名称") String fileName,
        @Schema(
                        description =
                                "云存储返回临时预签名 URL，有效秒数由 storage.presigned-url-expire-seconds 配置；Local 返回需携带认证凭证的 /api/v1/file/content 地址，不适用签名 TTL")
                String url,
        @Schema(description = "合并后的文件大小（字节）", example = "104857600") Long fileSize,
        @Schema(description = "文件 MD5（客户端未提交时为 null）") String md5) {}
