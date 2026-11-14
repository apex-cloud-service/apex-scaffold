package com.apex.scaffold.file.application.dto.chunk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片合并请求。
 *
 * <p>当全部分片上传完成后，客户端提交本请求触发服务端按序拼接分片为完整文件。
 * 文件名与内容类型缺省时沿用初始化阶段登记的值。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分片合并请求")
public class ChunkMergeRequest {

    @Schema(description = "文件记录主键", example = "1900000000000000001")
    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;

    @Schema(description = "最终文件名（含扩展名），缺省时沿用初始化登记值", example = "report.pdf")
    private String fileName;

    @Schema(description = "最终文件 MIME 类型，缺省时沿用初始化登记值", example = "application/pdf")
    private String contentType;

    @Schema(description = "整个文件的 MD5，用于完整性校验", example = "d41d8cd98f00b204e9800998ecf8427e")
    private String md5;
}
