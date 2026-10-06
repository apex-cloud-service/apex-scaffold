package com.apex.scaffold.domain.service;

import com.apex.scaffold.common.dto.TokenStoreEntry;
import reactor.core.publisher.Mono;

/**
 * Token服务接口。
 *
 * <p>定义Token生成、验证和管理的接口规范，由基础设施层实现。
 * 支持JWT标准，提供AccessToken和RefreshToken双Token机制，
 * 保障用户会话安全和设备绑定。</p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>生成AccessToken：基于用户标识和设备ID生成短期访问令牌</li>
 *   <li>生成RefreshToken：生成长期刷新令牌，用于无感续期</li>
 *   <li>解析Token：解析JWT中的Claims，提取用户信息</li>
 *   <li>验证Token：校验Token的签名和有效期</li>
 *   <li>存储Token：将Token持久化，支持服务端会话管理</li>
 *   <li>验证RefreshToken：校验刷新令牌是否仍然有效</li>
 *   <li>删除Token：用户登出时清除所有关联Token</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
public interface TokenService {
    /**
     * 生成AccessToken（短期访问令牌）。
     *
     * <p>使用JWT标准生成短期有效的访问令牌，将用户标识和设备ID
     * 编码到Payload中，实现设备绑定。AccessToken有效期通常为2小时。
     * 未指定会话ID时按旧版单会话模型签发（claims 不含 sid，存储走 legacy 槽）。</p>
     *
     * @param subject  用户唯一标识（通常为userId或openId）
     * @param deviceId 设备标识，绑定Token与设备，防止Token跨设备使用
     * @return AccessToken字符串
     */
    String generateAccessToken(String subject, String deviceId);

    /**
     * 生成AccessToken（多会话模型，携带会话ID与客户端类型）。
     *
     * <p>在 claims 中注入 {@code sid}（会话ID）与 {@code clientType}（客户端类型），
     * 同一用户每次登录产生独立会话槽（{@code token:access:{subject}:{sid}}），
     * 多端同时在线互不覆盖。</p>
     *
     * @param subject    用户唯一标识（通常为userId或openId）
     * @param deviceId   设备标识，绑定Token与设备（可选）
     * @param sid        会话ID（UUID），由调用方生成并保持 access/refresh 一致；为空时退化为旧版单会话模型
     * @param clientType 客户端类型编码（{@code ClientType}），可为空
     * @return AccessToken字符串
     * @since 3.3.0
     */
    String generateAccessToken(String subject, String deviceId, String sid, String clientType);

    /**
     * 生成RefreshToken（长期刷新令牌）。
     *
     * <p>生成长期有效的刷新令牌，用于在AccessToken过期后
     * 无感获取新的AccessToken。RefreshToken有效期通常为7天。
     * 未指定会话ID时按旧版单会话模型签发。</p>
     *
     * @param subject  用户唯一标识（通常为userId或openId）
     * @param deviceId 设备标识，绑定Token与设备
     * @return RefreshToken字符串
     */
    String generateRefreshToken(String subject, String deviceId);

    /**
     * 生成RefreshToken（多会话模型，携带会话ID与客户端类型）。
     *
     * <p>{@code sid} 必须与同会话 AccessToken 一致，刷新轮换按会话槽隔离。</p>
     *
     * @param subject    用户唯一标识（通常为userId或openId）
     * @param deviceId   设备标识，绑定Token与设备（可选）
     * @param sid        会话ID（UUID），为空时退化为旧版单会话模型
     * @param clientType 客户端类型编码（{@code ClientType}），可为空
     * @return RefreshToken字符串
     * @since 3.3.0
     */
    String generateRefreshToken(String subject, String deviceId, String sid, String clientType);

    /**
     * 解析Token并提取Claims。
     *
     * <p>解析JWT Token字符串，提取其中包含的用户信息、
     * 过期时间、签发时间等Claims。解析失败时返回空Map。</p>
     *
     * @param token Token字符串
     * @return 解析后的Claims键值对Map，包含sub、exp、iat、deviceId等字段；
     *         解析失败返回空Map
     */
    java.util.Map<String, Object> parseToken(String token);

    /**
     * 验证Token的有效性。
     *
     * <p>校验Token的签名合法性、是否过期以及是否在黑名单中。
     * 用于API网关或拦截器中的统一鉴权。</p>
     *
     * @param token Token字符串
     * @return 响应式校验结果，true表示Token有效，false表示Token无效或已过期
     */
    Mono<Boolean> validateToken(String token);

    /**
     * 存储Token到服务端缓存（纯Token字符串格式，无用户信息）。
     *
     * <p>将AccessToken和RefreshToken关联到用户标识，
     * 持久化到Redis等缓存中，支持服务端会话管理和Token续期。
     * 同一用户多次登录会覆盖旧Token。</p>
     *
     * <p>此方法仅存储纯 JWT 字符串，适用于没有用户信息的场景
     * （如临时 Token）。需要携带用户信息时请使用
     * {@link #storeToken(String, String, String, TokenStoreEntry, TokenStoreEntry)}。
     *
     * @param subject      用户唯一标识
     * @param accessToken  AccessToken字符串
     * @param refreshToken RefreshToken字符串
     * @return 存储完成信号
     */
    Mono<Void> storeToken(String subject, String accessToken, String refreshToken);

    /**
     * 存储Token到服务端缓存（携带用户信息的 JSON 格式）。
     *
     * <p>与 {@link #storeToken(String, String, String)} 功能相同，
     * 但将 Token 及用户信息序列化为 {@link TokenStoreEntry} JSON 对象存储，
     * 使得后续从 Redis 读取时可直接获取用户基础信息，减少数据库查询。
     *
     * <p>对于仅需存储临时 AccessToken 的新用户场景（无用户信息），
     * 请使用 {@link #storeAccessToken(String, String, int)}。
     *
     * @param subject       用户唯一标识
     * @param accessToken   AccessToken 字符串
     * @param refreshToken  RefreshToken 字符串
     * @param accessEntry   AccessToken 对应的用户信息条目
     * @param refreshEntry  RefreshToken 对应的用户信息条目
     * @return 存储完成信号
     * @since 2.2.0
     */
    Mono<Void> storeToken(
            String subject,
            String accessToken,
            String refreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry);

    /**
     * 存储Token对到服务端缓存（多会话模型，携带会话上下文）。
     *
     * <p>按 {@code (subject, sid)} 写入独立会话槽并登记到会话索引，
     * 同一用户多次登录互不覆盖；{@code sid} 为空时退化为旧版单会话槽
     * （{@code token:access:{subject}}），保持与存量客户端兼容。</p>
     *
     * @param subject       用户唯一标识
     * @param sid           会话ID（UUID）；为空时走 legacy 单会话槽
     * @param accessToken   AccessToken 字符串
     * @param refreshToken  RefreshToken 字符串
     * @param accessEntry   AccessToken 对应的用户信息条目，可为 null（退化为纯字符串存储）
     * @param refreshEntry  RefreshToken 对应的用户信息条目，可为 null
     * @return 存储完成信号
     * @since 3.3.0
     */
    Mono<Void> storeSessionToken(
            String subject,
            String sid,
            String accessToken,
            String refreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry);

    /**
     * 验证RefreshToken的有效性。
     *
     * <p>校验刷新令牌是否与用户匹配且未过期。
     * 用于AccessToken过期后的静默续期流程。</p>
     *
     * @param subject      用户唯一标识
     * @param refreshToken RefreshToken字符串
     * @return true表示RefreshToken有效，false表示无效或已过期
     */
    Mono<Boolean> validateRefreshToken(String subject, String refreshToken);

    /**
     * 删除用户的所有Token。
     *
     * <p>用户退出登录时调用，清除该用户所有关联的
     * AccessToken和RefreshToken，使所有会话立即失效。</p>
     *
     * @param subject 用户唯一标识
     * @return 删除的Token数量
     */
    Mono<Long> deleteToken(String subject);

    /**
     * 删除指定会话的Token（多会话模型，单会话登出）。
     *
     * <p>仅清除 {@code (subject, sid)} 会话槽与会话索引登记，
     * 其他端会话不受影响；{@code sid} 为空时等价于 {@link #deleteToken(String)}。</p>
     *
     * @param subject 用户唯一标识
     * @param sid     会话ID（UUID）
     * @return 删除的Token数量
     * @since 3.3.0
     */
    Mono<Long> deleteSessionToken(String subject, String sid);

    /**
     * 删除用户全部会话（多会话模型，全端登出）。
     *
     * <p>遍历会话索引清除所有会话槽；用户无会话索引时退化为
     * {@link #deleteToken(String)} 清理 legacy 槽。</p>
     *
     * @param subject 用户唯一标识
     * @return 删除的Token数量
     * @since 3.3.0
     */
    Mono<Long> deleteAllSessions(String subject);

    /**
     * 单独存储AccessToken（用于新用户注册前临时Token场景）。
     *
     * <p>仅存储AccessToken到Redis，不设置RefreshToken。
     * 主用于短信登录新用户收到REGISTER_REQUIRED时发放的临时Token，
     * 有效期与authTicket一致，注册完成后由完整登录流程覆盖。</p>
     *
     * @param subject      用户唯一标识（新用户为手机号）
     * @param accessToken  AccessToken字符串
     * @param ttlSeconds   Redis键过期时间（秒）
     * @return 存储完成信号
     */
    Mono<Void> storeAccessToken(String subject, String accessToken, int ttlSeconds);

    /**
     * 单独存储AccessToken到指定会话槽（多会话滑动续期场景）。
     *
     * <p>鉴权过滤器滑动续期时使用：按 {@code (subject, sid)} 覆盖写入
     * 新 AccessToken，不影响其他会话；{@code sid} 为空时回退到
     * {@link #storeAccessToken(String, String, int)}（legacy 槽）。</p>
     *
     * @param subject     用户唯一标识
     * @param sid         会话ID（UUID）
     * @param accessToken 新 AccessToken 字符串
     * @param ttlSeconds  Redis键过期时间（秒）
     * @return 存储完成信号
     * @since 3.3.0
     */
    Mono<Void> storeSessionAccessToken(String subject, String sid, String accessToken, int ttlSeconds);

    /**
     * 获取用户已存储的RefreshToken字符串。
     *
     * <p>从 Redis 中读取 {@code token:refresh:{subject}} 键对应的值，
     * 兼容旧格式（纯 JWT 字符串）和新格式（{@link TokenStoreEntry} JSON 对象）。
     * 用于退出登录时将 RefreshToken 加入黑名单。</p>
     *
     * @param subject 用户唯一标识
     * @return RefreshToken 字符串；若不存在则返回空 Mono
     */
    Mono<String> findRefreshToken(String subject);

    /**
     * 获取指定会话已存储的RefreshToken字符串（多会话模型）。
     *
     * <p>{@code sid} 为空时回退到 {@link #findRefreshToken(String)}（legacy 槽）。</p>
     *
     * @param subject 用户唯一标识
     * @param sid     会话ID（UUID）
     * @return RefreshToken 字符串；若不存在则返回空 Mono
     * @since 3.3.0
     */
    Mono<String> findSessionRefreshToken(String subject, String sid);

    /**
     * 查询用户当前全部有效会话。
     *
     * <p>返回会话索引中登记的全部 {@code sid}。legacy 单会话槽不登记索引，
     * 存量令牌的清理由 {@link #deleteAllSessions(String)} 与
     * {@link #findRefreshToken(String)} 覆盖。用于在线会话管理与全端登出。</p>
     *
     * @param subject 用户唯一标识
     * @return 会话ID集合
     * @since 3.3.0
     */
    reactor.core.publisher.Flux<String> findSessions(String subject);

    /**
     * 原子轮换刷新令牌（分布式锁保护）。
     *
     * <p>在按用户维度的分布式锁内完成"读取旧令牌 → 比对旧令牌 → 写入新令牌对 →
     * 拉黑旧令牌"全过程，保证同一刷新令牌在同一时刻只能被一个请求消费：
     * 并发携带同一旧令牌的刷新请求中仅第一个成功，其余因旧令牌已被轮换而失败，
     * 杜绝刷新令牌并发重用竞态（攻击者与受害者同时刷新时各自成功的漏洞）。</p>
     *
     * <p>与"先拉黑旧令牌、后写新令牌"的旧流程不同，本方法在写入新令牌成功之后
     * 才拉黑旧令牌；若写入失败，旧令牌仍保持可用，用户不会被瞬时故障登出。</p>
     *
     * @param subject          用户唯一标识（OpenID）
     * @param oldRefreshToken  待轮换的旧刷新令牌
     * @param newAccessToken   新生成的 AccessToken
     * @param newRefreshToken  新生成的 RefreshToken
     * @param accessEntry      AccessToken 对应的用户信息条目，可为 null（退化为纯字符串存储）
     * @param refreshEntry     RefreshToken 对应的用户信息条目，可为 null
     * @return 轮换成功返回 true；旧令牌不匹配、已失效、已在黑名单或获取锁超时返回 false
     * @since 2.3.0
     */
    Mono<Boolean> rotateRefreshToken(
            String subject,
            String oldRefreshToken,
            String newAccessToken,
            String newRefreshToken,
            TokenStoreEntry accessEntry,
            TokenStoreEntry refreshEntry);
}
