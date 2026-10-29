package com.apex.scaffold.file.infrastructure.persistence.po;

import com.apex.scaffold.common.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 文件分享持久化对象。
 *
 * <p>对应数据库中的 file_share 表，仅保存分享令牌的摘要，不保存明文令牌。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("file_share")
public class FileSharePO extends BasePO {

    /** 被分享的文件编号。 */
    @TableField("file_id")
    private Long fileId;

    /** 文件所属租户。 */
    @TableField("tenant_id")
    private String tenantId;

    /** 分享创建者用户编号。 */
    @TableField("creator_id")
    private Long creatorId;

    /** 分享令牌 SHA-256 十六进制摘要。 */
    @TableField("token_hash")
    private String tokenHash;

    /** 分享失效时间。 */
    @TableField("expire_at")
    private LocalDateTime expireAt;

    /** 分享状态（ACTIVE/REVOKED）。 */
    @TableField("status")
    private String status;
}
