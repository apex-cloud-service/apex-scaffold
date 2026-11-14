package com.apex.scaffold.file.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件分片持久化对象。
 *
 * <p>对应数据库中的 file_chunk 表，存储大文件分片上传的每个分片元数据。
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
@TableName("file_chunk")
public class FileChunkPO extends BasePO {

    /** 可信租户标识。 */
    @TableField("tenant_id")
    private String tenantId;

    /** 对象实际存储提供商类型。 */
    @TableField("storage_type")
    private String storageType;

    /**
     * 所属文件 ID（关联 file_record.id）。
     */
    @TableField("file_id")
    private Long fileId;

    /**
     * 分片序号（从 0 开始）。
     */
    @TableField("chunk_index")
    private Integer chunkIndex;

    /**
     * 分片大小（字节）。
     */
    @TableField("chunk_size")
    private Long chunkSize;

    /**
     * 分片总数。
     */
    @TableField("total_chunks")
    private Integer totalChunks;

    /**
     * 分片MD5。
     */
    @TableField("md5")
    private String md5;

    /**
     * 分片在对象存储中的对象键。
     */
    @TableField("object_name")
    private String objectName;

    /**
     * 分片存储桶名称。
     */
    @TableField("bucket_name")
    private String bucketName;

    /**
     * 存储状态（STORED/ORPHANED/DELETED）。
     */
    @TableField("storage_status")
    private String storageStatus;

    /**
     * 上传用户ID。
     */
    @TableField("user_id")
    private Long userId;
}
