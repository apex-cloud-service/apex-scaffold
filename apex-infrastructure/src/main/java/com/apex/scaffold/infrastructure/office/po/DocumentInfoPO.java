package com.apex.scaffold.infrastructure.office.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 文档信息持久化对象
 *
 * <p>对应数据库表office_document_info，用于存储文档信息的持久化数据。
 * 使用MyBatis-Plus注解进行ORM映射，支持乐观锁和逻辑删除。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("office_document_info")
public class DocumentInfoPO {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    @TableField("tenant_id")
    private Long tenantId;

    /** 办公平台类型 */
    @TableField("platform")
    private String platform;

    /** 平台中的文档ID */
    @TableField("platform_document_id")
    private String platformDocumentId;

    /** 文档类型 */
    @TableField("document_type")
    private String documentType;

    /** 文档标题 */
    @TableField("title")
    private String title;

    /** 文档路径 */
    @TableField("path")
    private String path;

    /** 文档URL */
    @TableField("url")
    private String url;

    /** 文档所有者ID */
    @TableField("owner_id")
    private String ownerId;

    /** 文档所有者姓名 */
    @TableField("owner_name")
    private String ownerName;

    /** 父文件夹ID */
    @TableField("parent_folder_id")
    private String parentFolderId;

    /** 文档大小 */
    @TableField("file_size")
    private Long fileSize;

    /** 文档扩展名 */
    @TableField("file_extension")
    private String fileExtension;

    /** 文档MIME类型 */
    @TableField("mime_type")
    private String mimeType;

    /** 是否为文件夹 */
    @TableField("is_folder")
    private Boolean isFolder;

    /** 权限类型 */
    @TableField("permission")
    private String permission;

    /** 协作者ID列表 */
    @TableField("collaborator_ids")
    private String collaboratorIds;

    /** 文档版本号 */
    @TableField("version")
    private String version;

    /** 最后修改人ID */
    @TableField("last_modified_by_id")
    private String lastModifiedById;

    /** 最后修改人姓名 */
    @TableField("last_modified_by_name")
    private String lastModifiedByName;

    /** 最后修改时间 */
    @TableField("last_modified_time")
    private LocalDateTime lastModifiedTime;

    /** 是否已归档 */
    @TableField("is_archived")
    private Boolean isArchived;

    /** 乐观锁版本号 */
    @Version
    @TableField("version_number")
    private Integer versionNumber;

    /** 逻辑删除标记 */
    @TableLogic
    @TableField("deleted")
    private Boolean deleted;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
