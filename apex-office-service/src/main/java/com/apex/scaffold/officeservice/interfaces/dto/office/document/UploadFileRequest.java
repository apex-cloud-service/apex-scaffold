package com.apex.scaffold.officeservice.interfaces.dto.office.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上传文件请求DTO
 *
 * <p>用于接收客户端上传文件的请求参数。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上传文件请求")
public class UploadFileRequest {

    @NotBlank(message = "办公平台类型不能为空")
    @Schema(description = "办公平台类型", example = "feishu", requiredMode = Schema.RequiredMode.REQUIRED)
    private String platform;

    @Schema(description = "父文件夹ID", example = "folder_abc123")
    private String folderId;

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "文件名", example = "项目计划.xlsx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileName;

    @NotBlank(message = "文件内容不能为空")
    @Schema(description = "文件内容（Base64编码）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileContent;

    @NotBlank(message = "所有者ID不能为空")
    @Schema(description = "所有者ID", example = "user001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ownerId;

    @NotBlank(message = "所有者姓名不能为空")
    @Schema(description = "所有者姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ownerName;
}
