package com.apex.scaffold.file.application.dto.share;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 文件分享创建结果。
 *
 * <p>{@code token} 为明文分享令牌，仅在创建响应中返回一次，服务端只保留摘要。</p>
 *
 * @param shareId 分享记录编号
 * @param fileId 被分享的文件编号
 * @param token 明文分享令牌
 * @param expireAt 分享失效时间
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Schema(name = "FileShareCreated", description = "文件分享创建结果；token 明文仅创建响应返回一次")
public record FileShareCreated(
        @Schema(description = "分享记录编号", example = "1001") Long shareId,
        @Schema(description = "被分享的文件编号", example = "2001") Long fileId,
        @Schema(description = "明文分享令牌（仅创建响应返回一次）") String token,
        @Schema(description = "分享失效时间") LocalDateTime expireAt) {}
