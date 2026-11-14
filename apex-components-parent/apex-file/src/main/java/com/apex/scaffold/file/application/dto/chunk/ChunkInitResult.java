package com.apex.scaffold.file.application.dto.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 分片上传初始化/状态查询结果。
 *
 * <p>初始化时返回新创建的 fileId、存储桶、建议分片大小与分片总数，
 * {@code uploadedChunks} 为空；状态查询时用于返回已成功上传的分片序号列表，
 * 客户端据此跳过已完成分片，实现断点续传。</p>
 *
 * @param fileId         文件记录主键
 * @param bucketName     服务端配置的存储桶名称
 * @param chunkSize      建议/已使用的分片大小（字节），无分片时为 null
 * @param totalChunks    分片总数，无分片信息时为 null
 * @param uploadedChunks 已成功上传的分片序号列表（升序）
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "ChunkInitResult", description = "分片上传初始化/状态查询结果")
public record ChunkInitResult(
        @Schema(description = "文件记录主键") Long fileId,
        @Schema(description = "服务端配置的存储桶名称", example = "apex") String bucketName,
        @Schema(description = "建议/已使用的分片大小（字节）", example = "5242880") Long chunkSize,
        @Schema(description = "分片总数", example = "20") Integer totalChunks,
        @Schema(description = "已成功上传的分片序号列表（升序）") List<Integer> uploadedChunks) {}
