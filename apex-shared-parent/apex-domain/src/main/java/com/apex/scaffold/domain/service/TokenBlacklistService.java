package com.apex.scaffold.domain.service;

import java.util.Date;
import reactor.core.publisher.Mono;

/**
 * Token 黑名单服务接口。
 *
 * <p>定义Token黑名单操作的接口规范，由基础设施层实现。
 * 用于管理退出登录或失效的Token，防止已吊销的Token被继续使用。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>Token加入黑名单</b>：将指定Token加入黑名单，设置过期时间，
 *       过期后自动从黑名单中移除</li>
 *   <li><b>黑名单查询</b>：检查指定Token是否已被加入黑名单，
 *       用于接口鉴权时拦截已吊销的Token</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户退出登录时，将当前AccessToken加入黑名单</li>
 *   <li>管理员禁用用户时，将该用户的所有Token加入黑名单</li>
 *   <li>Token刷新时，将旧的AccessToken加入黑名单</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>黑名单通常基于Redis实现，利用其key过期机制自动清理过期的Token记录。
 * 黑名单的过期时间与Token本身的剩余有效期保持一致，
 * 避免无限期增长的内存占用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface TokenBlacklistService {
    /**
     * 将Token加入黑名单。
     *
     * <p>将指定的Token字符串加入黑名单数据结构，并设置过期时间。
     * 过期时间通常设为Token的剩余有效时长，过期后自动从黑名单中移除，
     * 避免内存无限增长。在鉴权流程中，会优先检查Token是否在黑名单中。</p>
     *
     * @param token      Token 字符串
     * @param expireTime 过期时间（毫秒），0表示使用Token自身剩余有效期
     */
    Mono<Void> add(String token, long expireTime);

    /**
     * 判断Token是否在黑名单中。
     *
     * <p>查询指定Token是否已被加入黑名单。此方法通常在
     * API网关的JWT鉴权过滤器中被调用，用于拦截已吊销的Token。</p>
     *
     * @param token Token 字符串
     * @return true表示Token在黑名单中（已被吊销），false表示不在黑名单中
     */
    Mono<Boolean> isBlacklisted(String token);

    /**
     * 吊销某个身份在指定时间点之前签发的全部令牌。
     *
     * @param subject JWT subject
     * @param expireTime 吊销标记保留时间（毫秒）
     */
    default Mono<Void> revokeSubject(String subject, long expireTime) {
        return Mono.empty();
    }

    /**
     * 判断某个令牌签发时间是否早于身份吊销时间。
     *
     * @param subject JWT subject
     * @param issuedAt 令牌签发时间
     * @return 是否已被身份级吊销
     */
    default Mono<Boolean> isSubjectRevoked(String subject, Date issuedAt) {
        return Mono.just(false);
    }
}
