package com.apex.scaffold.file.application.dto.share;

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
 * @version 1.0.0
 * @since 1.3.2
 */
public record FileShareExchangeResult(
        Long shareId, Long fileId, String originalName, String contentType, Long fileSize, LocalDateTime expireAt) {}
