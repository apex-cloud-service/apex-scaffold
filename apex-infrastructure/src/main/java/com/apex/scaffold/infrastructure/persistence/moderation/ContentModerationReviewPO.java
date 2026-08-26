package com.apex.scaffold.infrastructure.persistence.moderation;

import com.apex.scaffold.infrastructure.persistence.po.BasePO;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * {@code biz_content_moderation_review} 内容人工复核任务持久化对象。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_content_moderation_review")
public class ContentModerationReviewPO extends BasePO {

    @TableField("source_type")
    private String sourceType;

    @TableField("user_id")
    private Long userId;

    @TableField("content_text")
    private String contentText;

    @TableField("media_json")
    private String mediaJson;

    @TableField("provider")
    private String provider;

    @TableField("provider_request_id")
    private String providerRequestId;

    @TableField("findings_json")
    private String findingsJson;

    @TableField("content_hash")
    private String contentHash;

    @TableField("status")
    private String status;

    @TableField("reviewer_open_id")
    private String reviewerOpenId;

    @TableField("review_comment")
    private String reviewComment;

    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    @TableField("decided_at")
    private LocalDateTime decidedAt;
}
