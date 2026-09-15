package com.apex.scaffold.interfaces.dto.office.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建文档请求DTO
 *
 * <p>用于接收客户端创建文档的请求参数，包含文档的基本信息、类型、所有者等。
 * 使用JSR-303注解进行参数校验，确保数据的有效性。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建文档请求")
public class CreateDocumentRequest {

    @NotBlank(message = "办公平台类型不能为空")
    @Schema(description = "办公平台类型", example = "feishu", requiredMode = Schema.RequiredMode.REQUIRED)
    private String platform;

    @Schema(description = "父文件夹ID", example = "folder_abc123")
    private String folderId;

    @NotBlank(message = "文档标题不能为空")
    @Schema(description = "文档标题", example = "项目需求文档", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank(message = "文档类型不能为空")
    @Schema(description = "文档类型", example = "DOC", requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"DOC", "SHEET", "SLIDE", "MINDMAP", "FOLDER", "FILE"})
    private String documentType;

    @NotBlank(message = "所有者ID不能为空")
    @Schema(description = "所有者ID", example = "user001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ownerId;

    @NotBlank(message = "所有者姓名不能为空")
    @Schema(description = "所有者姓名", example = "张三", requiredMode = Schema.RequiredMode.REQUIRED)
    private String ownerName;
}
