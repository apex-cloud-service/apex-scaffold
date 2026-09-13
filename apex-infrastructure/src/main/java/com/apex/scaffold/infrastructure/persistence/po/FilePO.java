package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件信息持久化对象。
 *
 * <p>对应数据库中的sys_file表，存储文件上传信息。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
/**n * FilePO - 持久化对象n *n * <p>对应数据库表：TODO: 补充表名n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class FilePO extends BasePO {

    /**
     * 构造文件信息持久化对象。
     */

    /**
     * 文件名称。
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 原始文件名。
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 文件路径。
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件访问URL。
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 文件大小（字节）。
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 文件类型。
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 内容类型（MIME）。
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 存储桶名称。
     */
    @TableField("bucket_name")
    private String bucketName;

    /**
     * 文件MD5。
     */
    @TableField("md5")
    private String md5;

    /**
     * 上传用户ID。
     */
    @TableField("user_id")
    private Long userId;

    /** 文件业务类型（GENERIC/FACE_PROFILE/LIFESTYLE_PHOTO）。 */
    @TableField("business_type")
    private String businessType;

    /** 客户端上传幂等键。 */
    @TableField("client_request_id")
    private String clientRequestId;

    /** 存储状态（STORED/ORPHANED/DELETED）。 */
    @TableField("storage_status")
    private String storageStatus;

    /** 文件SHA-256哈希值。 */
    @TableField("sha256")
    private String sha256;
}
