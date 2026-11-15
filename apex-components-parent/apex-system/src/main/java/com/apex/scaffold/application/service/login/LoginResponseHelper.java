package com.apex.scaffold.application.service.login;

import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.auth.AuthNextAction;
import com.apex.scaffold.domain.event.UserRegisteredEvent;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录响应构建器
 *
 * <p>负责统一构建登录成功后的响应对象，消除多个 {@link LoginStrategy} 实现类中重复的
 * 响应构建代码。采用模板方法模式，将登录响应的公共构建逻辑集中于此组件管理。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>将用户实体通过 {@link UserAssembler} 转换为 {@link LoginResponse} DTO</li>
 *   <li>填充 JWT Token 对（accessToken、refreshToken）、Token 类型和过期时间</li>
 *   <li>新注册用户发布注册事件 {@link UserRegisteredEvent}（登录事件
 *       {@link com.apex.scaffold.domain.event.UserLoggedInEvent} 当前无消费者，不再发布）</li>
 *   <li>支持带手机号和不带手机号两种构建场景</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>注册事件的发布采用有界超时阻塞，执行失败仅记录错误日志，不影响登录流程的正常返回。
 * 通过 try-catch 包裹整个事件发布过程，防止事件发布器本身的异常影响主流程。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see LoginStrategy
 * @see UserAssembler
 * @see LoginResponse
 */
@Slf4j
@Component
public class LoginResponseHelper {

    /** Bearer Token 类型标识，符合 RFC 6750 规范 */
    private static final String TOKEN_TYPE = "Bearer";

    /** 登录领域事件发布的阻塞超时（秒），超时按发布失败降级处理，不阻断登录响应。 */
    private static final long EVENT_PUBLISH_TIMEOUT_SECONDS = 3;

    /** AccessToken 过期时间（毫秒），默认 2 小时。expiresIn 由此值 / 1000 计算。 */
    private final long accessTokenExpireMs;

    /**
     * 用户实体到登录响应对象的转换器。
     *
     * <p>负责将领域层的 {@code UserEntity} 映射为接口层的 {@code LoginResponse.UserInfo}。</p>
     */
    private final UserAssembler userAssembler;

    /** 领域事件发布器，用于发布用户登录和注册领域事件 */
    private final DomainEventPublisher domainEventPublisher;

    /**
     * 创建登录响应构建器。
     *
     * @param userAssembler 用户实体到登录响应对象的转换器
     * @param domainEventPublisher 领域事件发布器
     * @param accessTokenExpireMs AccessToken 过期时间（毫秒），默认 7200000（2 小时）
     */
    public LoginResponseHelper(
            UserAssembler userAssembler,
            DomainEventPublisher domainEventPublisher,
            @Value("${jwt.access-token-expire:7200000}") long accessTokenExpireMs) {
        this.userAssembler = userAssembler;
        this.domainEventPublisher = domainEventPublisher;
        this.accessTokenExpireMs = accessTokenExpireMs;
    }

    /**
     * 构建登录响应（不带手机号）
     *
     * <p>委托给 {@link #build(UserEntity, String, String, boolean, String)} 方法，
     * 传 {@code null} 作为 phone 参数。
     *
     * @param user         登录用户实体
     * @param accessToken  JWT 访问令牌
     * @param refreshToken JWT 刷新令牌
     * @param isNewUser    是否为新注册用户
     * @return 包含 JWT Token 对和用户信息的登录响应
     * @deprecated 请使用 {@link #build(UserEntity, String, String, boolean, String, String)} 替代，
     *     显式传递客户端IP参数用于安全审计
     */
    @Deprecated(forRemoval = true)
    public LoginResponse build(UserEntity user, String accessToken, String refreshToken, boolean isNewUser) {
        return build(user, accessToken, refreshToken, isNewUser, null);
    }

    /**
     * 构建登录响应（带手机号）
     *
     * <p>执行完整的登录响应构建流程：
     * <ol>
     *   <li>使用 {@link UserAssembler} 将用户实体转换为 {@link LoginResponse} DTO</li>
     *   <li>填充 JWT Token 对（accessToken、refreshToken）、Token 类型和过期时间</li>
     *   <li>设置用户的 openId、unionId 和新用户标识</li>
     *   <li>如果提供了手机号且用户信息不为空，回填手机号到用户信息中</li>
     *   <li>发布用户登录事件（新用户额外发布注册事件）</li>
     * </ol>
     *
     * @param user         登录用户实体
     * @param accessToken  JWT 访问令牌
     * @param refreshToken JWT 刷新令牌
     * @param isNewUser    是否为新注册用户
     * @param phone        用户手机号，可为 null
     * @return 包含 JWT Token 对和用户信息的登录响应
     * @deprecated 请使用 {@link #build(UserEntity, String, String, boolean, String, String)} 替代，
     *     显式传递客户端IP参数用于安全审计，避免硬编码 {@code 127.0.0.1}
     */
    @Deprecated(forRemoval = true)
    public LoginResponse build(
            UserEntity user, String accessToken, String refreshToken, boolean isNewUser, String phone) {
        return build(user, accessToken, refreshToken, isNewUser, phone, "127.0.0.1");
    }

    /**
     * 构建登录响应（带手机号和客户端IP）
     *
     * <p>执行完整的登录响应构建流程：
     * <ol>
     *   <li>使用 {@link UserAssembler} 将用户实体转换为 {@link LoginResponse} DTO</li>
     *   <li>填充 JWT Token 对（accessToken、refreshToken）、Token 类型和过期时间</li>
     *   <li>设置用户的 openId、unionId 和新用户标识</li>
     *   <li>如果提供了手机号且用户信息不为空，回填手机号到用户信息中</li>
     *   <li>新注册用户发布注册事件（登录事件无消费者，不再发布）</li>
     * </ol>
     *
     * @param user         登录用户实体
     * @param accessToken  JWT 访问令牌
     * @param refreshToken JWT 刷新令牌
     * @param isNewUser    是否为新注册用户
     * @param phone        用户手机号，可为 null
     * @param clientIp     客户端IP地址（保留参数，供未来安全审计消费者使用）
     * @return 包含 JWT Token 对和用户信息的登录响应
     */
    public LoginResponse build(
            UserEntity user,
            String accessToken,
            String refreshToken,
            boolean isNewUser,
            String phone,
            String clientIp) {
        LoginResponse response = userAssembler.toLoginResponse(user);
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setTokenType(TOKEN_TYPE);
        response.setExpiresIn(accessTokenExpireMs / 1000L);
        response.setOpenId(user.getOpenId());
        response.setUnionId(user.getUnionId());
        response.setIsNewUser(isNewUser);
        response.setNextAction(AuthNextAction.AUTHENTICATED.name());
        if (phone != null && response.getUserInfo() != null) {
            response.getUserInfo().setPhone(phone);
        }
        publishEvents(user, isNewUser, Optional.ofNullable(phone).orElse(user.getPhone()));
        return response;
    }

    /**
     * 发布领域事件
     *
     * <p>新注册用户额外发布 {@link UserRegisteredEvent} 用户注册事件（登录事件
     * {@code UserLoggedInEvent} 当前无消费者，不再发布，避免登录热路径每次写入 Outbox）。
     * 注册事件属于登录成功后的非关键副作用（新用户分析 / 欢迎引导），以有界超时阻塞发布；
     * 发布失败（Outbox 依赖故障、超时）仅记录告警日志并降级，不中止已成功的登录响应，
     * 避免"登录成功却收到错误"或请求无限挂起。
     *
     * @param user      登录用户实体
     * @param isNewUser 是否为新注册用户
     * @param phone     用户手机号
     */
    private void publishEvents(UserEntity user, boolean isNewUser, String phone) {
        if (!isNewUser) {
            return;
        }
        try {
            domainEventPublisher
                    .publish(new UserRegisteredEvent(user.getId(), user.getNickName(), phone))
                    .block(Duration.ofSeconds(EVENT_PUBLISH_TIMEOUT_SECONDS));
        } catch (Exception e) {
            log.warn("登录领域事件发布失败，已降级（不影响登录成功响应）: {}", e.getMessage(), e);
        }
    }
}
