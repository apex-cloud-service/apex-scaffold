package com.apex.scaffold.interfaces.dto.office.document;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档信息响应DTO
 *
 * <p>用于向客户端返回文档的详细信息。
 * 包含文档的所有属性和状态信息。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文档信息响应")
public class DocumentInfoResponse {

    @Schema(description = "文档ID", example = "1001")
    private Long id;

    @Schema(description = "租户ID", example = "1")
    private Long tenantId;

    @Schema(description = "办公平台类型", example = "feishu")
    private String platform;

    @Schema(description = "平台中的文档ID", example = "doc_abc123")
    private String platformDocumentId;

    @Schema(description = "文档类型", example = "DOC")
    private String documentType;

    @Schema(description = "文档标题", example = "项目需求文档")
    private String title;

    @Schema(description = "文档路径", example = "/workspace/项目文档/需求文档")
    private String path;

    @Schema(description = "文档URL", example = "https://example.feishu.cn/docs/doc_abc123")
    private String url;

    @Schema(description = "文档所有者ID", example = "user001")
    private String ownerId;

    @Schema(description = "文档所有者姓名", example = "张三")
    private String ownerName;

    @Schema(description = "父文件夹ID", example = "folder_abc123")
    private String parentFolderId;

    @Schema(description = "文档大小（字节）", example = "1024000")
    private Long fileSize;

    @Schema(description = "文档扩展名", example = "docx")
    private String fileExtension;

    @Schema(description = "文档MIME类型", example = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    private String mimeType;

    @Schema(description = "是否为文件夹", example = "false")
    private Boolean isFolder;

    @Schema(description = "权限类型", example = "PRIVATE")
    private String permission;

    @Schema(description = "协作者ID列表", example = "[\"user002\", \"user003\"]")
    private String collaboratorIds;

    @Schema(description = "文档版本号", example = "v1.2")
    private String version;

    @Schema(description = "最后修改人ID", example = "user002")
    private String lastModifiedById;

    @Schema(description = "最后修改人姓名", example = "李四")
    private String lastModifiedByName;

    @Schema(description = "最后修改时间", example = "2026-09-14T16:30:00")
    private LocalDateTime lastModifiedTime;

    @Schema(description = "是否已归档", example = "false")
    private Boolean isArchived;

    @Schema(description = "创建时间", example = "2026-09-10T10:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-09-14T16:30:00")
    private LocalDateTime updateTime;
}
