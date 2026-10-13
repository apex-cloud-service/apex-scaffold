package com.apex.scaffold.file.domain.model.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 文件信息领域实体
 *
 * <p>记录系统中上传的所有文件元数据，包括文件名、路径、URL、大小、类型和存储状态等。
 * 支持通用文件上传和业务照片上传两种场景，通过businessType区分。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>文件上传后经过内容审核，审核结果通过storageStatus标识</li>
 *   <li>支持STORED/ORPHANED/DELETED三种存储状态</li>
 *   <li>通过MD5和SHA-256进行文件完整性校验</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FileEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建文件实体对象。 */
    public FileEntity() {}

    /** 文件名称。 */
    private String fileName;

    /** 原始文件名。 */
    private String originalName;

    /** 文件路径。 */
    private String filePath;

    /** 文件访问 URL。 */
    private String fileUrl;

    /** 文件大小（字节）。 */
    private Long fileSize;

    /** 文件类型。 */
    private String fileType;

    /** 内容类型（MIME）。 */
    private String contentType;

    /** 存储桶名称。 */
    private String bucketName;

    /** 文件 MD5。 */
    private String md5;

    /** 上传用户 ID。 */
    private Long userId;

    /** 文件业务类型（GENERIC/VOTE_PHOTO）。 */
    private String businessType;

    /** 存储状态（STORED/ORPHANED/DELETED）。 */
    private String storageStatus;

    /** 文件 SHA-256 哈希值。 */
    private String sha256;

    /**
     * 设置文件名（仅供持久层和构造器使用）。
     *
     * @param value 文件名
     */
    public void setFileName(String value) {
        this.fileName = value;
    }

    /**
     * 设置原始文件名（仅供持久层和构造器使用）。
     *
     * @param value 原始文件名
     */
    public void setOriginalName(String value) {
        this.originalName = value;
    }

    /**
     * 设置文件路径（仅供持久层和构造器使用）。
     *
     * @param value 文件路径
     */
    public void setFilePath(String value) {
        this.filePath = value;
    }

    /**
     * 设置文件访问URL（仅供持久层和构造器使用）。
     *
     * @param value 文件访问URL
     */
    public void setFileUrl(String value) {
        this.fileUrl = value;
    }

    /**
     * 设置文件大小（仅供持久层和构造器使用）。
     *
     * @param value 文件大小（字节）
     */
    public void setFileSize(Long value) {
        this.fileSize = value;
    }

    /**
     * 设置文件类型（仅供持久层和构造器使用）。
     *
     * @param value 文件类型
     */
    public void setFileType(String value) {
        this.fileType = value;
    }

    /**
     * 设置内容类型（仅供持久层和构造器使用）。
     *
     * @param value 内容类型（MIME）
     */
    public void setContentType(String value) {
        this.contentType = value;
    }

    /**
     * 设置存储桶名称（仅供持久层和构造器使用）。
     *
     * @param value 存储桶名称
     */
    public void setBucketName(String value) {
        this.bucketName = value;
    }

    /**
     * 设置文件MD5（仅供持久层和构造器使用）。
     *
     * @param value 文件MD5
     */
    public void setMd5(String value) {
        this.md5 = value;
    }

    /**
     * 设置上传用户ID（仅供持久层和构造器使用）。
     *
     * @param value 上传用户ID
     */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /**
     * 设置文件业务类型（仅供持久层和构造器使用）。
     *
     * @param value 文件业务类型
     */
    public void setBusinessType(String value) {
        this.businessType = value;
    }

    /**
     * 设置存储状态（仅供持久层和构造器使用）。
     *
     * @param value 存储状态
     */
    public void setStorageStatus(String value) {
        this.storageStatus = value;
    }

    /**
     * 设置文件SHA-256哈希值（仅供持久层和构造器使用）。
     *
     * @param value 文件SHA-256哈希值
     */
    public void setSha256(String value) {
        this.sha256 = value;
    }
}
