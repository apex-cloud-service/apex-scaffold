package com.apex.scaffold.file.application.dto.chunk;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分片合并结果。
 *
 * <p>合并成功后返回最终对象定位信息与临时访问地址，结构与单文件上传结果保持一致，
 * 客户端可直接用于后续业务引用。</p>
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
        @Schema(description = "临时访问 URL") String url,
        @Schema(description = "合并后的文件大小（字节）", example = "104857600") Long fileSize,
        @Schema(description = "文件 MD5（客户端未提交时为 null）") String md5) {}
