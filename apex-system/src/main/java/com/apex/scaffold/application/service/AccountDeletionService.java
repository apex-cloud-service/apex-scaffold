package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.AccountDeletionAcceptedResponse;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.SubscriptionRepository;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import com.apex.scaffold.domain.service.SmsCodePurpose;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 账号注销应用服务。
 *
 * <p>这里明确区分同步和异步边界：短信验证、会员门禁、账号失效、身份令牌吊销和
 * 清理任务入队完成后才返回；照片对象、偏爱关系、算力流水等大范围数据清理交给
 * {@link AccountDeletionPort#cleanup(AccountDeletionPort.DeletionTask)}。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
public class AccountDeletionService {

    /** 注销验证短信的固定用途。 */
    public static final SmsCodePurpose SMS_PURPOSE = SmsCodePurpose.ACCOUNT_DELETION;

    /** refresh token 默认寿命，也是身份吊销标记的保留时间。 */
    private static final long DEFAULT_REVOCATION_TTL_MS = 604_800_000L;

    private final UserService userService;
    private final SubscriptionRepository subscriptionRepository;
    private final SmsVerificationCoordinator smsVerificationCoordinator;
    private final AccountDeletionPort accountDeletionPort;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenService tokenService;
    private final CacheConsistencyPort cacheConsistencyPort;
    private final AuthTicketService authTicketService;

    @Value("${jwt.refresh-token-expire:" + DEFAULT_REVOCATION_TTL_MS + "}")
    private long revocationTtlMs = DEFAULT_REVOCATION_TTL_MS;

    /**
     * 创建账号注销服务。
     *
     * @param userService 用户应用服务
     * @param subscriptionRepository 订阅仓储
     * @param smsVerificationCoordinator 短信验证码协调器
     * @param accountDeletionPort 账号注销端口
     * @param tokenBlacklistService 令牌黑名单服务
     * @param tokenService 令牌服务
     * @param cacheConsistencyPort 缓存一致性端口
     * @param authTicketService 认证票据服务
     */
    public AccountDeletionService(
            UserService userService,
            SubscriptionRepository subscriptionRepository,
            SmsVerificationCoordinator smsVerificationCoordinator,
            AccountDeletionPort accountDeletionPort,
            TokenBlacklistService tokenBlacklistService,
            TokenService tokenService,
            CacheConsistencyPort cacheConsistencyPort,
            AuthTicketService authTicketService) {
        this.userService = userService;
        this.subscriptionRepository = subscriptionRepository;
        this.smsVerificationCoordinator = smsVerificationCoordinator;
        this.accountDeletionPort = accountDeletionPort;
        this.tokenBlacklistService = tokenBlacklistService;
        this.tokenService = tokenService;
        this.cacheConsistencyPort = cacheConsistencyPort;
        this.authTicketService = authTicketService;
    }

    /**
     * 向当前账号已经绑定的手机号发送注销验证码。
     *
     * @param openId 当前认证身份
     */
    public Mono<Void> sendDeletionCode(String openId) {
        return findCurrentUser(openId)
                .flatMap(this::requirePhone)
                .flatMap(phone -> smsVerificationCoordinator.send(phone, SMS_PURPOSE));
    }

    /**
     * 校验验证码并同步注销账号。
     *
     * @param openId 当前认证身份
     * @param code 注销短信验证码
     * @param requestId 客户端/链路请求标识，可为空
     * @param clientIp 客户端 IP
     * @param traceId 链路 ID
     * @return 异步清理受理信息
     */
    public Mono<AccountDeletionAcceptedResponse> deleteCurrentUser(
            String openId, String code, String requestId, String clientIp, String traceId) {
        String normalizedRequestId = requestId == null || requestId.isBlank()
                ? "account-deletion-" + UUID.randomUUID().toString().replace("-", "")
                : requestId;
        return findCurrentUser(openId).flatMap(user -> requirePhone(user).flatMap(phone -> verifyMembershipAndCode(
                        user, phone, code)
                .then(Mono.defer(() -> deactivate(user, phone, openId, normalizedRequestId, clientIp, traceId)))));
    }

    /**
     * 按 openId 加载当前认证用户，用户不存在时直接失败。
     *
     * @param openId 当前认证身份，不能为空
     * @return 当前用户实体
     */
    private Mono<UserEntity> findCurrentUser(String openId) {
        return userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND)));
    }

    /**
     * 校验用户已绑定手机号并返回该手机号。
     *
     * @param user 当前用户实体
     * @return 已绑定的手机号
     */
    private Mono<String> requirePhone(UserEntity user) {
        String phone = user.getPhone();
        if (phone == null || phone.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PHONE_NOT_BOUND));
        }
        return Mono.just(phone);
    }

    /**
     * 校验注销门禁与短信验证码：会员未过期时拒绝注销，验证码错误时拒绝注销。
     *
     * <p>验证码校验失败（含校验服务不可用时的兜底 false）统一按验证码无效处理，
     * 不区分具体失败原因。</p>
     *
     * @param user 当前用户实体
     * @param phone 用户绑定的手机号
     * @param code 注销短信验证码
     */
    private Mono<Void> verifyMembershipAndCode(UserEntity user, String phone, String code) {
        return Mono.defer(() -> {
                    Mono<com.apex.scaffold.domain.model.entity.SubscriptionEntity> lookup =
                            subscriptionRepository.findUnexpiredByUserId(user.getId());
                    return lookup == null ? Mono.empty() : lookup;
                })
                .flatMap(subscription ->
                        Mono.<Void>error(new BusinessException(ResultCode.ACCOUNT_DELETION_MEMBERSHIP_ACTIVE)))
                .switchIfEmpty(Mono.defer(() -> {
                    Mono<Boolean> validation = smsVerificationCoordinator.validate(phone, code, SMS_PURPOSE);
                    Mono<Boolean> safeValidation = validation == null ? Mono.empty() : validation;
                    return safeValidation.defaultIfEmpty(false).flatMap(valid -> {
                        if (Boolean.TRUE.equals(valid)) {
                            return Mono.empty();
                        }
                        return Mono.error(new BusinessException(ResultCode.SMS_CODE_INVALID));
                    });
                }));
    }

    /**
     * 提交账号失效与清理任务入队，并在受理成功后吊销运行时状态。
     *
     * @param user 当前用户实体
     * @param phone 用户绑定的手机号
     * @param openId 当前认证身份
     * @param requestId 客户端/链路请求标识
     * @param clientIp 客户端 IP
     * @param traceId 链路 ID
     * @return 注销受理信息
     */
    private Mono<AccountDeletionAcceptedResponse> deactivate(
            UserEntity user, String phone, String openId, String requestId, String clientIp, String traceId) {
        AccountDeletionPort.DeletionCommand command =
                new AccountDeletionPort.DeletionCommand(user.getId(), openId, phone, requestId, clientIp, traceId);
        return accountDeletionPort
                .deactivateAndEnqueue(command)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.ACCOUNT_DELETION_ALREADY_COMPLETED)))
                .flatMap(accepted -> invalidateRuntimeState(user, phone, openId)
                        .thenReturn(new AccountDeletionAcceptedResponse(
                                accepted.requestId(), String.valueOf(accepted.jobId()), accepted.status())));
    }

    /**
     * 吊销身份令牌并逐项清理运行时状态。
     *
     * <p>身份级吊销标记失败必须让请求失败，防止注销后旧 token 继续污染新账号；
     * token 删除、临时票据吊销与缓存逐项清理失败则仅记录日志不阻断返回。</p>
     *
     * @param user 当前用户实体
     * @param phone 用户绑定的手机号
     * @param openId 当前认证身份
     */
    private Mono<Void> invalidateRuntimeState(UserEntity user, String phone, String openId) {
        // 身份级吊销标记是“注销后旧 token 不能污染新账号”的核心门闩，失败时必须让请求失败，
        // 不能把它和缓存逐项清理一样吞掉。
        Mono<Void> revokeSubject = Mono.defer(() -> {
            Mono<Void> operation = tokenBlacklistService.revokeSubject(openId, revocationTtlMs);
            return operation == null ? Mono.empty() : operation;
        });
        Mono<Void> deleteTokens = safeVoid(tokenService.deleteToken(openId));
        // 临时注册/绑定票据不应在注销后继续推进旧身份流程；票据清理失败不回滚已完成的账号失效。
        Mono<Void> revokeTickets = safeVoid(authTicketService.revokeIdentity(phone, openId));
        Mono<Void> evictById =
                safeVoid(cacheConsistencyPort.evictWithDelayedDoubleDelete("user:id", String.valueOf(user.getId())));
        Mono<Void> evictByOpenId = safeVoid(cacheConsistencyPort.evictWithDelayedDoubleDelete("user:openId", openId));
        return revokeSubject
                .then(deleteTokens)
                .then(revokeTickets)
                .then(evictById)
                .then(evictByOpenId);
    }

    /**
     * 吞掉清理类操作的所有失败并记录日志，保证后续清理继续执行。
     *
     * @param operation 可空的异步操作
     */
    private Mono<Void> safeVoid(Mono<?> operation) {
        return Mono.justOrEmpty(operation).then().onErrorResume(error -> {
            log.error("注销后的运行时状态清理失败，账号已同步失效: {}", error.getMessage(), error);
            return Mono.empty();
        });
    }
}
