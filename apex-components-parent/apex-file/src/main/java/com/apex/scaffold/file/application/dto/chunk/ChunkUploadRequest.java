package com.apex.scaffold.file.application.dto.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个分片上传请求。
 *
 * <p>承载分片归属文件、分片序号与分片总数等元数据，分片二进制内容通过
 * 请求体单独传递。同一分片重复上传时服务端幂等处理，不会产生重复对象。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "单个分片上传请求")
public class ChunkUploadRequest {

    @Schema(description = "文件记录主键", example = "1900000000000000001")
    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;

    @Schema(description = "分片序号（从 0 开始）", example = "0")
    @NotNull(message = "分片序号不能为空")
    @Min(value = 0, message = "分片序号不能为负数")
    private Integer chunkIndex;

    @Schema(description = "分片总数", example = "20")
    @Positive(message = "分片总数必须大于 0")
    private Integer totalChunks;

    @Schema(description = "分片 MD5，用于分片完整性校验", example = "d41d8cd98f00b204e9800998ecf8427e")
    private String md5;
}
