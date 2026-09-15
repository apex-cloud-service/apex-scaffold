package com.apex.scaffold.application.office;

import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.login.LoginResponseHelper;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.office.OfficeOAuthClient;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.OfficePlatformApp;
import com.apex.scaffold.domain.office.OfficeRepository;
import com.apex.scaffold.domain.office.OfficeState;
import com.apex.scaffold.domain.service.TokenService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 办公平台 OAuth 登录应用服务
 *
 * <p>编排办公平台 OAuth 认证流程，包括授权 URL 生成、回调处理和 Apex JWT 签发。
 * 实现租户级应用配置、管理员审批绑定和多重安全校验。
 *
 * <p>核心流程：
 * <ol>
 *   <li>生成授权 URL：查询租户应用配置 → 签发租户绑定 state → 返回平台授权地址</li>
 *   <li>OAuth 回调：消费 state → 兑换授权码 → 查询已审批绑定 → 校验用户 → 签发 Apex JWT</li>
 * </ol>
 *
 * <p>安全保障：
 * <ul>
 *   <li>state 绑定租户、平台和回调地址，防止 CSRF 和跨租户攻击</li>
 *   <li>只允许 APPROVED 状态的绑定登录</li>
 *   <li>校验用户属于同一租户且未删除</li>
 *   <li>平台 access token 不返回给浏览器</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Service
@RequiredArgsConstructor
public class OfficeApplicationService {
    private final List<OfficeOAuthClient> clients;
    private final OfficeState stateService;
    private final OfficeRepository repository;
    private final UserService users;
    private final TokenService tokens;
    private final LoginResponseHelper responses;

    /**
     * 生成办公平台 OAuth 授权地址
     *
     * <p>步骤：
     * <ol>
     *   <li>从上下文获取租户标识</li>
     *   <li>查询租户的平台应用配置</li>
     *   <li>生成绑定租户和平台的 state（有效期 5 分钟）</li>
     *   <li>调用平台客户端生成授权 URL</li>
     * </ol>
     *
     * @param platform 办公平台类型
     * @return 平台授权地址，用户点击后跳转到平台授权页
     * @throws BusinessException 租户未配置应用、应用未启用或平台客户端不存在时
     */
    public Mono<String> authorizationUrl(OfficePlatform platform) {
        return tenant().flatMap(tenantId -> app(tenantId, platform)
                .flatMap(app -> client(platform).flatMap(client -> stateService
                        .issue(tenantId, platform.name(), app.getRedirectUri(), Duration.ofMinutes(5))
                        .flatMap(state -> client.authorizationUrl(app, state)))));
    }

    /**
     * 处理办公平台 OAuth 回调并签发 Apex 登录令牌
     *
     * <p>完整的安全校验链：
     * <ol>
     *   <li>参数非空校验：platform、code、state</li>
     *   <li>租户上下文校验：必须存在有效租户</li>
     *   <li>应用配置校验：租户已配置、已启用、未删除</li>
     *   <li>state 原子消费：校验租户、平台、回调地址一致性，防止重放</li>
     *   <li>平台认证：兑换授权码获取外部用户标识</li>
     *   <li>绑定校验：APPROVED 状态、已启用、未删除、租户和平台匹配</li>
     *   <li>用户校验：用户存在、已启用、未删除、租户匹配、openId 非空</li>
     *   <li>签发令牌：生成 Apex access token 和 refresh token</li>
     *   <li>更新登录信息：记录最后登录 IP</li>
     * </ol>
     *
     * <p>任一环节校验失败均返回登录失败错误，不泄露具体失败原因。
     *
     * @param platform 办公平台类型
     * @param code 平台授权码（一次性有效）
     * @param state 服务端签发的状态值
     * @param deviceId 设备标识
     * @param clientIp 客户端 IP 地址
     * @return 登录响应，包含 Apex access token 和 refresh token
     * @throws BusinessException 任一校验失败时返回 LOGIN_FAILED
     */
    public Mono<LoginResponse> login(
            OfficePlatform platform, String code, String state, String deviceId, String clientIp) {
        return Mono.defer(() -> {
            if (platform == null || blank(code) || blank(state)) return denied();
            return tenant().flatMap(tenantId -> app(tenantId, platform).flatMap(app -> stateService
                    .consume(state, tenantId, platform.name(), app.getRedirectUri())
                    .filter(Boolean.TRUE::equals)
                    .switchIfEmpty(denied())
                    .flatMap(consumed -> client(platform))
                    .flatMap(client -> client.authenticate(app, code))
                    .filter(external -> !blank(external))
                    .flatMap(external -> repository
                            .findBinding(tenantId, platform, external)
                            .filter(binding -> binding.isEnabled()
                                    && Integer.valueOf(0).equals(binding.getDeleted())
                                    && "APPROVED".equals(binding.getStatus())
                                    && tenantId.equals(binding.getTenantId())
                                    && platform == binding.getPlatform()
                                    && external.equals(binding.getExternalUserId())
                                    && binding.getUserId() != null))
                    .flatMap(binding -> users.getById(binding.getUserId())
                            .filter(user -> binding.getUserId().equals(user.getId())))
                    .filter(user -> user.isEnabled()
                            && Integer.valueOf(0).equals(user.getDeleted())
                            && tenantId.equals(user.getTenantId())
                            && !blank(user.getOpenId()))
                    .switchIfEmpty(denied())
                    .flatMap(user -> issue(user, deviceId, clientIp))
                    .contextWrite(TenantContext.withTenant(tenantId))));
        });
    }

    /**
     * 签发 Apex 登录令牌并构建登录响应
     *
     * @param user Apex 用户实体
     * @param deviceId 设备标识
     * @param clientIp 客户端 IP
     * @return 登录响应
     */
    private Mono<LoginResponse> issue(UserEntity user, String deviceId, String clientIp) {
        return Mono.defer(() -> {
            String subject = user.getOpenId();
            String access = tokens.generateAccessToken(subject, deviceId);
            String refresh = tokens.generateRefreshToken(subject, deviceId);
            return tokens.storeToken(subject, access, refresh)
                    .then(Mono.defer(() -> users.updateLoginInfo(user.getId(), clientIp)))
                    .then(Mono.fromSupplier(
                            () -> responses.build(user, access, refresh, false, user.getPhone(), clientIp)));
        });
    }

    /**
     * 查询租户的平台应用配置并校验状态
     *
     * @param tenantId 租户标识
     * @param platform 平台类型
     * @return 已启用的应用配置
     */
    private Mono<OfficePlatformApp> app(String tenantId, OfficePlatform platform) {
        return repository
                .findApp(tenantId, platform)
                .filter(app -> app.isEnabled()
                        && Integer.valueOf(0).equals(app.getDeleted())
                        && tenantId.equals(app.getTenantId())
                        && platform == app.getPlatform()
                        && !blank(app.getRedirectUri()))
                .switchIfEmpty(denied());
    }

    /**
     * 从上下文获取当前租户标识
     *
     * @return 租户标识
     */
    private Mono<String> tenant() {
        return Mono.defer(() -> Mono.justOrEmpty(TenantContext.currentTenantId())
                .filter(value -> !value.isBlank())
                .switchIfEmpty(denied()));
    }

    /**
     * 查找指定平台的 OAuth 客户端
     *
     * @param platform 平台类型
     * @return OAuth 客户端
     */
    private Mono<OfficeOAuthClient> client(OfficePlatform platform) {
        return Mono.defer(() -> Mono.justOrEmpty(clients.stream()
                        .filter(value -> value.platform() == platform)
                        .findFirst())
                .switchIfEmpty(denied()));
    }

    /**
     * 判断字符串是否为空或空白
     *
     * @param value 待检查字符串
     * @return true-空或空白，false-非空
     */
    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 返回登录失败错误
     *
     * @param <T> 响应类型
     * @return 登录失败异常
     */
    private <T> Mono<T> denied() {
        return Mono.error(new BusinessException(ResultCode.LOGIN_FAILED));
    }
}
