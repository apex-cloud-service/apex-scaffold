package com.apex.scaffold.file.application.dto.share;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 分享兑换结果（不含文件内容）。
 *
 * @param shareId 分享记录编号
 * @param fileId 被分享的文件编号
 * @param originalName 原始文件名
 * @param contentType 内容类型
 * @param fileSize 文件大小（字节）
 * @param expireAt 分享失效时间
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Schema(name = "FileShareExchangeResult", description = "分享兑换结果（不含文件内容）")
public record FileShareExchangeResult(
        @Schema(description = "分享记录编号", example = "1001") Long shareId,
        @Schema(description = "被分享的文件编号", example = "2001") Long fileId,
        @Schema(description = "原始文件名", example = "验收报告.pdf") String originalName,
        @Schema(description = "内容类型", example = "application/pdf") String contentType,
        @Schema(description = "文件大小（字节）", example = "1048576") Long fileSize,
        @Schema(description = "分享失效时间") LocalDateTime expireAt) {}
