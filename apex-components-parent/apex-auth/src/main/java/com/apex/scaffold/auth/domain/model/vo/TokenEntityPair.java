package com.apex.scaffold.auth.domain.model.vo;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;

/**
 * 已准备或已持久化的 ACCESS/REFRESH 记录，不携带明文 JWT。
 *
 * @param accessToken 访问令牌记录
 * @param refreshToken 刷新令牌记录；与访问令牌共享租户、用户及家族
 * @author Apex Team
 * @since 1.3.1
 */
public record TokenEntityPair(TokenEntity accessToken, TokenEntity refreshToken) {}
