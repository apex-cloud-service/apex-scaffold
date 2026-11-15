package com.apex.scaffold.auth.domain.service;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import reactor.core.publisher.Mono;

/**
 * 令牌生命周期管理接口，提供签发、验证、轮换刷新与撤销能力。
 *
 * <p>当前实现签发 JWT，并将访问令牌与刷新令牌持久化；验证时核对 JWT 声明与
 * 仓储记录，通过记录的撤销状态拒绝已撤销令牌。未提供 Opaque Token 签发或验证，
 * 也未调用独立的黑名单服务。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * TokenService tokenService = ...;
 * Mono<TokenPair> tokens = tokenService.issueToken(userId, tenantId, deviceId, clientIp);
 * }</pre>
 *
 * <p>调用方应将返回的 {@link Mono} 组合进响应式链，由外层订阅执行；
 * 不应在 WebFlux 请求链中阻塞等待。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
public interface TokenService {

    /**
     * 为指定租户的用户签发访问令牌与刷新令牌。
     *
     * <p>当前实现的访问令牌有效期为 2 小时，刷新令牌有效期为 7 天；
     * 此重载不携带 {@code clientType} 声明。用户不存在、租户不匹配、账户禁用或锁定
     * 均通过错误信号返回，两枚令牌在同一 JDBC 事务中持久化；提交后的审计失败或超时
     * 仅记录日志，不撤销已提交令牌或将其恢复为可重复签发状态。
     *
     * @param userId 认证中心用户 ID
     * @param tenantId 用户所属租户 ID
     * @param deviceId 设备标识
     * @param clientIp 客户端 IP，用于持久化及审计
     * @return 签发并持久化的令牌对；签发失败时发出错误信号
     */
    Mono<TokenPair> issueToken(Long userId, String tenantId, String deviceId, String clientIp);

    /**
     * 签发可携带客户端类型的令牌对。
     *
     * <p>接口的默认实现仅委托四参数重载，不处理 {@code clientType}。
     * 当前服务实现覆盖此方法，在客户端类型非空白时写入同名 JWT 声明；
     * 用户档案中的 {@code accountId} 非空时、{@code username} 非空白时也会写入声明。
     * {@code jti} 是 JWT 声明及仓储定位键，{@code tokenFamily} 仅保存在令牌记录中，
     * 不写入 JWT，也不额外生成 {@code sid} 声明。
     *
     * @param userId 认证中心用户 ID
     * @param tenantId 用户所属租户 ID
     * @param deviceId 设备标识
     * @param clientIp 客户端 IP，用于持久化及审计
     * @param clientType 客户端类型编码，可为 {@code null} 或空白
     * @return 令牌对；用户校验、签名、加密或持久化失败时发出错误信号，提交后审计失败不改变结果
     * @since 3.3.0
     */
    default Mono<TokenPair> issueToken(
            Long userId, String tenantId, String deviceId, String clientIp, String clientType) {
        return issueToken(userId, tenantId, deviceId, clientIp);
    }

    /**
     * 验证 JWT 并返回与声明一致的有效令牌记录。
     *
     * <p>当前实现检查签名及有效期，要求 {@code jti}、{@code userId}、{@code tenantId}
     * 声明完整，并核对仓储记录的租户、用户、令牌类型、过期时间与撤销状态。
     * 请求上下文已有租户且与令牌租户不一致时，以 {@code FORBIDDEN} 错误信号拒绝。
     * 此方法不以空完成表示令牌无效，也不检查独立黑名单。
     *
     * @param token 待验证的 JWT 字符串
     * @return 有效的令牌实体；无效、过期、已撤销或记录不存在时发出 {@code UNAUTHORIZED}
     *     业务错误信号，租户冲突或底层调用失败时传播相应错误
     */
    Mono<TokenEntity> validateToken(String token);

    /**
     * 校验并消费刷新令牌，再签发新的访问令牌与刷新令牌。
     *
     * <p>刷新链共享验签、声明、租户及持久化记录核对，但允许读取撤销状态用于重放分类；
     * 普通 {@link #validateToken(String)} 仍拒绝已撤销令牌。新 JWT 在事务前签名加密，
     * 同步 JDBC 事务锁定租户内用户行，条件消费旧刷新令牌并原子保存两枚新令牌。
     * 签名或事务失败不会留下部分新记录；已提交旧令牌不得因审计或响应失败恢复。
     *
     * <p>已消费刷新令牌的再次使用会在同一锁边界撤销当前租户下该用户的全部有效令牌，
     * 安全撤销提交后再发出认证失败信号。手工撤销或登出令牌仅拒绝，不归类为消费重放。
     * 成功轮换保留 family、前驱 JTI、轮换计数和已验签的客户端类型；正常轮换不撤销旧访问令牌。
     *
     * @param refreshToken 刷新 JWT，须通过签名、声明、租户及有效期校验
     * @return 新令牌对；校验、签名、消费或持久化失败发出错误信号，非刷新令牌以 {@code PARAM_ERROR}
     *     拒绝，消费重放以 {@code UNAUTHORIZED} 拒绝；提交后审计错误不改变已提交结果
     */
    Mono<TokenPair> refreshToken(String refreshToken);

    /**
     * 解析 JWT，撤销其对应的仓储记录并记录安全审计。
     *
     * <p>当前实现按 {@code jti} 查询记录并更新撤销标记，不写入独立黑名单。
     * 此路径不调用 {@link #validateToken(String)}，但仍要求 JWT 能被解析；
     * 解析失败以 {@code UNAUTHORIZED} 拒绝，令牌记录不存在以 {@code NOT_FOUND} 拒绝。
     *
     * @param token 待撤销的 JWT 字符串
     * @return 记录撤销及提交后有界审计结束的完成信号；解析、查询或撤销失败时发出错误信号，
     *     审计失败不恢复已撤销记录
     */
    Mono<Void> revokeToken(String token);

    /**
     * 批量撤销指定租户下用户的访问令牌与刷新令牌记录。
     *
     * <p>用于强制下线或安全事件响应；当前实现委托仓储批量撤销，
     * 不返回受影响记录数，也不逐条调用单令牌撤销或审计接口。
     *
     * @param userId 认证中心用户 ID
     * @param tenantId 用户所属租户 ID
     * @return 批量撤销结束后的完成信号；仓储失败时发出错误信号
     */
    Mono<Void> revokeAllUserTokens(Long userId, String tenantId);

    /**
     * 验证令牌后提取仓储记录中的用户 ID，而非仅解码 JWT 声明。
     *
     * @param token 待验证的 JWT 字符串
     * @return 用户 ID；验证失败时传播 {@link #validateToken(String)} 的错误信号
     */
    Mono<Long> extractUserId(String token);
}
