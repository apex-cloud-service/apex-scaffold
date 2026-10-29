package com.apex.scaffold.fileservice.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 兼容图片上传 multipart 表单文档模型，运行时由处理器解析文件与性别字段。
 * @author Apex Team
 * @since 1.0.0
 */
@Schema(name = "FileUploadRequest", description = "兼容图片上传 multipart 表单")
@Data
public class FileUploadRequestDoc {
    /** multipart 二进制图片。 */
    @Schema(
            description = "待上传图片，支持 jpg/jpeg/png/gif/bmp/webp/heic/heif",
            type = "string",
            format = "binary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String file;

    /** 兼容入口可选的性别目录编码。 */
    @Schema(
            description = "性别路由参数：1 保存到 AM 目录，2 保存到 AF 目录；省略时由应用服务处理",
            allowableValues = {"1", "2"},
            example = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Integer sex;
}
