package com.apex.scaffold.file.application.dto.share;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 文件分享列表项（不含令牌与摘要）。
 *
 * @param shareId 分享记录编号
 * @param fileId 文件编号
 * @param status 分享状态
 * @param expireAt 失效时间
 * @param createTime 创建时间
 * @author Apex Team
 * @since 1.3.3
 */
@Schema(description = "文件分享列表项")
public record FileShareItem(
        Long shareId, Long fileId, String status, LocalDateTime expireAt, LocalDateTime createTime) {}
