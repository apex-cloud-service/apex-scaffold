package com.apex.scaffold.domain.office.document;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文档信息聚合根
 *
 * <p>表示办公平台中的一个文档或文件，包含文档的基本信息、权限、操作记录等。
 * 作为聚合根，负责维护文档信息的完整性和一致性，所有对文档的操作都需要通过此聚合根进行。
 *
 * <p>文档信息的主要特性：
 * <ul>
 *   <li>支持多平台文档管理（飞书、钉钉、企业微信）</li>
 *   <li>支持文档权限控制和协作</li>
 *   <li>支持文档版本管理</li>
 *   <li>支持文档操作审计</li>
 * </ul>
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfo {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 办公平台类型（feishu/dingtalk/wecom） */
    private String platform;

    /** 平台中的文档ID */
    private String platformDocumentId;

    /** 文档类型 */
    private DocumentType documentType;

    /** 文档标题 */
    private String title;

    /** 文档路径（在平台中的完整路径） */
    private String path;

    /** 文档URL */
    private String url;

    /** 文档所有者ID */
    private String ownerId;

    /** 文档所有者姓名 */
    private String ownerName;

    /** 父文件夹ID */
    private String parentFolderId;

    /** 文档大小（字节） */
    private Long fileSize;

    /** 文档扩展名 */
    private String fileExtension;

    /** 文档MIME类型 */
    private String mimeType;

    /** 是否为文件夹 */
    private Boolean isFolder;

    /** 权限类型（PRIVATE/PUBLIC/SHARED） */
    private String permission;

    /** 协作者ID列表（JSON格式） */
    private String collaboratorIds;

    /** 文档版本号 */
    private String version;

    /** 最后修改人ID */
    private String lastModifiedById;

    /** 最后修改人姓名 */
    private String lastModifiedByName;

    /** 最后修改时间 */
    private LocalDateTime lastModifiedTime;

    /** 是否已归档 */
    private Boolean isArchived;

    /** 乐观锁版本号 */
    private Integer versionNumber;

    /** 逻辑删除标记 */
    private Boolean deleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 检查文档是否为文件夹
     *
     * @return 如果是文件夹返回true，否则返回false
     */
    public boolean isFolder() {
        return Boolean.TRUE.equals(isFolder) || documentType == DocumentType.FOLDER;
    }

    /**
     * 检查文档是否为公开文档
     *
     * @return 如果是公开文档返回true，否则返回false
     */
    public boolean isPublic() {
        return "PUBLIC".equals(permission);
    }

    /**
     * 检查文档是否为私有文档
     *
     * @return 如果是私有文档返回true，否则返回false
     */
    public boolean isPrivate() {
        return "PRIVATE".equals(permission);
    }

    /**
     * 检查文档是否已归档
     *
     * @return 如果已归档返回true，否则返回false
     */
    public boolean isArchived() {
        return Boolean.TRUE.equals(isArchived);
    }

    /**
     * 归档文档
     */
    public void archive() {
        this.isArchived = true;
    }

    /**
     * 取消归档
     */
    public void unarchive() {
        this.isArchived = false;
    }

    /**
     * 更新文档标题
     *
     * @param newTitle 新标题
     */
    public void updateTitle(String newTitle) {
        if (newTitle == null || newTitle.trim().isEmpty()) {
            throw new IllegalArgumentException("文档标题不能为空");
        }
        this.title = newTitle;
    }

    /**
     * 更新权限
     *
     * @param newPermission 新权限
     */
    public void updatePermission(String newPermission) {
        if (!isValidPermission(newPermission)) {
            throw new IllegalArgumentException("无效的权限类型: " + newPermission);
        }
        this.permission = newPermission;
    }

    /**
     * 检查权限是否有效
     *
     * @param permission 权限类型
     * @return 如果有效返回true，否则返回false
     */
    private boolean isValidPermission(String permission) {
        return "PRIVATE".equals(permission) || "PUBLIC".equals(permission) || "SHARED".equals(permission);
    }
}
