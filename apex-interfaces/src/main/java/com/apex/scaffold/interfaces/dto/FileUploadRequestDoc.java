package com.apex.scaffold.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 文件上传 multipart 表单文档模型。
 *
 * <p>该类型只用于生成 OpenAPI 文档；运行时由 WebFlux {@code FilePart} 流式读取
 * {@code file} 字段。</p>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Schema(name = "FileUploadRequest", description = "图片上传 multipart 表单")
@Data
public class FileUploadRequestDoc {

    /** multipart 中的二进制图片字段。 */
    @Schema(
            description = "待上传图片，支持 jpg/jpeg/png/gif/bmp/webp/heic/heif",
            type = "string",
            format = "binary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String file;

    /** multipart 中的 sex 路由字段（可选，后端会自动检测）。 */
    @Schema(
            description = "性别路由参数（可选）：1 保存到 AM 目录，2 保存到 AF 目录；不传则后端通过人脸分析自动检测",
            example = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer sex;

    /** multipart 中的 usage 用途字段。 */
    @Schema(
            description = "上传用途：GENERIC（普通文件）或 VOTE_PHOTO（投票照片）",
            example = "GENERIC",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String usage;

    /** multipart 中的 clientRequestId 幂等键字段。 */
    @Schema(description = "客户端请求幂等键，最长 64 字符，VOTE_PHOTO 推荐必传", example = "upload-20260720-0001", maxLength = 64)
    private String clientRequestId;
}
