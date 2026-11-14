package com.apex.scaffold.fileservice.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 普通图片上传 multipart 表单文档模型，运行时由处理器解析文件与性别字段。
 * @author Apex Team
 * @since 1.0.0
 */
@Schema(name = "GenericFileUploadRequest", description = "普通图片上传表单")
@Data
public class GenericFileUploadRequestDoc {
    /** multipart 二进制图片。 */
    @Schema(
            description = "待上传图片，支持 jpg/jpeg/png/gif/bmp/webp/heic/heif",
            type = "string",
            format = "binary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String file;

    /** 对象存储性别目录编码。 */
    @Schema(
            description = "对象存储目录性别编码：1=男（AM），2=女（AF）",
            allowableValues = {"1", "2"},
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sex;
}
