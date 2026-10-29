package com.apex.scaffold.file.application.dto.share;

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
 * @version 1.0.0
 * @since 1.3.2
 */
public record FileShareCreated(Long shareId, Long fileId, String token, LocalDateTime expireAt) {}
