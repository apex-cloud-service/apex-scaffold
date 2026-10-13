package com.apex.scaffold.fileservice.interfaces.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通用文件上传 multipart 表单文档模型。
 *
 * <p>该类型仅用于生成 OpenAPI 文档；运行时由 WebFlux {@code FilePart} 流式读取
 * {@code file} 字段。相比 {@link FileUploadRequestDoc}，该模型支持更广泛的文件类型，
 * 包括文档、压缩包、音视频等。</p>
 *
 * <h2>支持的文件类型</h2>
 * <ul>
 *   <li>图片：jpg/jpeg/png/gif/bmp/webp/heic/heif</li>
 *   <li>文档：pdf/doc/docx/xls/xlsx/ppt/pptx/txt</li>
 *   <li>压缩包：zip/rar/7z/tar/gz</li>
 *   <li>音视频：mp3/mp4/avi/mov/wav</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>附件上传：合同、协议等业务文档上传</li>
 *   <li>资料上传：证件照、身份证等资料上传</li>
 *   <li>多媒体上传：音频、视频等多媒体文件上传</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 * @see FileUploadRequestDoc
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
