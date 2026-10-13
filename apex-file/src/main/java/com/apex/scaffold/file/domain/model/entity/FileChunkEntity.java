package com.apex.scaffold.file.domain.model.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 文件分片领域实体
 *
 * <p>记录大文件分片上传过程中每个分片的元数据，包括所属文件、分片序号、
 * 分片大小、对象存储键与存储状态等。配合 file_record 支撑分片上传与断点续传：
 * 上传前先 init 生成 file_record 主记录，随后逐片上传并记录 object_name，
 * 全部分片到位后由 merge 按序拼接为完整文件。</p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>同一 file_id 下 chunk_index 从 0 开始且唯一</li>
 *   <li>object_name 为存储 SPI 返回的真实对象键（服务端 UUID 重命名）</li>
 *   <li>storage_status 标识分片的生命周期（STORED/ORPHANED/DELETED）</li>
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
public class FileChunkEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建分片实体对象。 */
    public FileChunkEntity() {}

    /** 所属文件 ID（关联 file_record.id）。 */
    private Long fileId;

    /** 分片序号（从 0 开始）。 */
    private Integer chunkIndex;

    /** 分片大小（字节）。 */
    private Long chunkSize;

    /** 分片总数。 */
    private Integer totalChunks;

    /** 分片 MD5。 */
    private String md5;

    /** 分片在对象存储中的对象键。 */
    private String objectName;

    /** 分片存储桶名称。 */
    private String bucketName;

    /** 存储状态（STORED/ORPHANED/DELETED）。 */
    private String storageStatus;

    /** 上传用户 ID。 */
    private Long userId;

    /**
     * 创建 STORED 状态下已入库的分片实体。
     *
     * <p>用于分片上传成功后持久化：记录分片归属、序号、大小、真实对象键与桶名。</p>
     *
     * @param fileId      所属文件 ID
     * @param chunkIndex  分片序号（从 0 开始）
     * @param chunkSize   分片大小（字节）
     * @param totalChunks 分片总数
     * @param md5         分片 MD5，可为空
     * @param objectName  分片在对象存储中的对象键
     * @param bucketName  分片存储桶名称
     * @param userId      上传用户 ID，可为空
     * @return 新的分片实体
     */
    public static FileChunkEntity stored(
            Long fileId,
            Integer chunkIndex,
            Long chunkSize,
            Integer totalChunks,
            String md5,
            String objectName,
            String bucketName,
            Long userId) {
        FileChunkEntity entity = new FileChunkEntity();
        entity.fileId = fileId;
        entity.chunkIndex = chunkIndex;
        entity.chunkSize = chunkSize;
        entity.totalChunks = totalChunks;
        entity.md5 = md5;
        entity.objectName = objectName;
        entity.bucketName = bucketName;
        entity.storageStatus = "STORED";
        entity.userId = userId;
        return entity;
    }

    /**
     * 设置所属文件 ID（仅供持久层和构造器使用）。
     *
     * @param value 所属文件 ID
     */
    public void setFileId(Long value) {
        this.fileId = value;
    }

    /**
     * 设置分片序号（仅供持久层和构造器使用）。
     *
     * @param value 分片序号
     */
    public void setChunkIndex(Integer value) {
        this.chunkIndex = value;
    }

    /**
     * 设置分片大小（仅供持久层和构造器使用）。
     *
     * @param value 分片大小（字节）
     */
    public void setChunkSize(Long value) {
        this.chunkSize = value;
    }

    /**
     * 设置分片总数（仅供持久层和构造器使用）。
     *
     * @param value 分片总数
     */
    public void setTotalChunks(Integer value) {
        this.totalChunks = value;
    }

    /**
     * 设置分片 MD5（仅供持久层和构造器使用）。
     *
     * @param value 分片 MD5
     */
    public void setMd5(String value) {
        this.md5 = value;
    }

    /**
     * 设置分片对象键（仅供持久层和构造器使用）。
     *
     * @param value 分片对象键
     */
    public void setObjectName(String value) {
        this.objectName = value;
    }

    /**
     * 设置分片存储桶名称（仅供持久层和构造器使用）。
     *
     * @param value 分片存储桶名称
     */
    public void setBucketName(String value) {
        this.bucketName = value;
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
     * 设置上传用户 ID（仅供持久层和构造器使用）。
     *
     * @param value 上传用户 ID
     */
    public void setUserId(Long value) {
        this.userId = value;
    }
}
