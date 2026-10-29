package com.apex.scaffold.file.domain.model.entity;

import com.apex.scaffold.domain.model.entity.BaseEntity;
import java.io.Serial;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class FileEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 可信租户标识，历史未核实记录保持为空。 */
    private String tenantId;

    /** 实际存储提供商类型，访问时不得使用当前默认类型替代。 */
    private String storageType;

    /** 初始化约定的总片数；历史未知会话保持为空并禁止合并。 */
    private Integer totalChunks;

    /** 初始化约定的分片大小。 */
    private Long chunkSize;

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
}
