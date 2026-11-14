package com.apex.scaffold.file.application.dto.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片上传初始化请求。
 *
 * <p>客户端在大文件分片上传前调用初始化接口，提交文件名、总大小与分片数量，
 * 服务端据此创建一条 {@code UPLOADING} 状态的 file_record 主记录并返回 fileId，
 * 后续各分片上传与合并均以该 fileId 为归属标识。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分片上传初始化请求")
public class ChunkInitRequest {

    @Schema(description = "原始文件名（含扩展名）", example = "report.pdf")
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @Schema(description = "文件总大小（字节）", example = "104857600")
    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须大于 0")
    private Long fileSize;

    @Schema(description = "分片大小（字节），缺省时按总大小与分片数自动计算", example = "5242880")
    @Positive(message = "分片大小必须大于 0")
    private Long chunkSize;

    @Schema(description = "分片总数", example = "20")
    @NotNull(message = "分片总数不能为空")
    @Positive(message = "分片总数必须大于 0")
    private Integer totalChunks;

    @Schema(description = "整个文件的 MD5，用于秒传与完整性校验", example = "d41d8cd98f00b204e9800998ecf8427e")
    private String md5;

    @Schema(description = "文件 MIME 类型", example = "application/pdf")
    private String contentType;
}
