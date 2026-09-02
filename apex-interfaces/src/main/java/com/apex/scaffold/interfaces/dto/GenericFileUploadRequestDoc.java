package com.apex.scaffold.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 普通图片上传 multipart 文档模型。
 *
 *
 *<p>该模型只用于生成函数式路由的 OpenAPI schema。运行时由 WebFlux 读取表单：
 *{@code file} 是待上传图片，{@code sex} 仅用于选择 AM/AF 对象存储目录。
 *接口不接受 usage 或 clientRequestId，也不会创建照片、分析任务、ELO 或分桶记录。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "GenericFileUploadRequest", description = "普通图片上传表单")
@Data
public class GenericFileUploadRequestDoc {

    @Schema(
            description = "待上传图片，仅支持 JPG、PNG、BMP、WEBP",
            type = "string",
            format = "binary",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String file;

    @Schema(
            description = "对象存储目录性别编码：0=男（AM），1=女（AF）",
            allowableValues = {"0", "1"},
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer sex;
}
