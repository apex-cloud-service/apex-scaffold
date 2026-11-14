package com.apex.scaffold.file.application.dto.chunk;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 单个分片上传结果。
 *
 * <p>返回分片归属文件、分片序号、真实对象键与是否新写入。当客户端重复上传
 * 同一分片时 {@code uploaded} 为 false，表示命中已存在分片（幂等）。</p>
 *
 * @param fileId     文件记录主键
 * @param chunkIndex 分片序号（从 0 开始）
 * @param uploaded   是否为本次新上传（false 表示该分片此前已存在）
 * @param objectName 分片在对象存储中的真实对象键
 * @param chunkSize  分片大小（字节）
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "ChunkUploadResult", description = "单个分片上传结果")
public record ChunkUploadResult(
        @Schema(description = "文件记录主键") Long fileId,
        @Schema(description = "分片序号（从 0 开始）", example = "0") Integer chunkIndex,
        @Schema(description = "是否为本次新上传") boolean uploaded,
        @Schema(description = "分片在对象存储中的真实对象键") String objectName,
        @Schema(description = "分片大小（字节）", example = "5242880") Long chunkSize) {}
