package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.SubscriptionEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.SubscriptionRepository;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import com.apex.scaffold.domain.service.SmsCodePurpose;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 账号注销应用服务的完整门禁与同步失效契约。
 *
 * <p>覆盖注销验证码发送、会员门禁、验证码校验、注销执行与运行时状态同步等
 * 链路。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountDeletionServiceTest {

    private static final String OPEN_ID = "wx-account-deletion";
    private static final String PHONE = "13800138000";
    private static final Long USER_ID = 42L;
    private static final String CODE = "123456";

    @Mock
    private UserService userService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SmsVerificationCoordinator smsVerificationCoordinator;

    @Mock
    private AccountDeletionPort accountDeletionPort;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private TokenService tokenService;

    @Mock
    private CacheConsistencyPort cacheConsistencyPort;

    @Mock
    private AuthTicketService authTicketService;

    private AccountDeletionService service;
    private UserEntity user;

    /**
     * 装配被测服务并准备绑定手机号、有效验证码与正常响应的持久化桩。
     */
    @BeforeEach
    void setUp() {
        service = new AccountDeletionService(
                userService,
                subscriptionRepository,
                smsVerificationCoordinator,
                accountDeletionPort,
                tokenBlacklistService,
                tokenService,
                cacheConsistencyPort,
                authTicketService);

        user = new UserEntity();
        user.setId(USER_ID);
        user.setOpenId(OPEN_ID);
        user.setPhone(PHONE);
        user.setStatus(1);

        when(userService.getByOpenId(OPEN_ID)).thenReturn(Mono.just(user));
        when(subscriptionRepository.findUnexpiredByUserId(USER_ID)).thenReturn(Mono.empty());
        when(smsVerificationCoordinator.validate(PHONE, CODE, SmsCodePurpose.ACCOUNT_DELETION))
                .thenReturn(Mono.just(true));
        when(accountDeletionPort.deactivateAndEnqueue(any()))
                .thenReturn(Mono.just(new AccountDeletionPort.AcceptedDeletion(9001L, "request-1", "ACCEPTED")));
        when(tokenBlacklistService.revokeSubject(eq(OPEN_ID), anyLong())).thenReturn(Mono.empty());
        when(tokenService.deleteToken(OPEN_ID)).thenReturn(Mono.just(2L));
        when(authTicketService.revokeIdentity(PHONE, OPEN_ID)).thenReturn(Mono.empty());
        when(cacheConsistencyPort.evict(anyString(), anyString())).thenReturn(Mono.just(true));
    }

    /**
     * 验证发送注销验证码时使用当前账号绑定手机号且用途限定为注销。
     */
    @Test
    @DisplayName("发送注销验证码必须使用当前账号绑定手机号和注销用途")
    void sendsDeletionCodeToBoundPhoneWithDedicatedPurpose() {
        when(smsVerificationCoordinator.send(PHONE, SmsCodePurpose.ACCOUNT_DELETION))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.sendDeletionCode(OPEN_ID)).verifyComplete();

        verify(smsVerificationCoordinator).send(PHONE, SmsCodePurpose.ACCOUNT_DELETION);
    }

    /**
     * 验证未绑定手机号的账号无法发起注销验证，且不会触发任何验证码发送。
     */
    @Test
    @DisplayName("未绑定手机号不能发起注销验证")
    void rejectsAccountWithoutBoundPhone() {
        user.setPhone(null);

        StepVerifier.create(service.sendDeletionCode(OPEN_ID))
                .verifyErrorSatisfies(error -> assertBusinessCode(error, ResultCode.PHONE_NOT_BOUND));

        verify(smsVerificationCoordinator, never()).send(anyString(), any(SmsCodePurpose.class));
    }

    /**
     * 验证存在未过期会员时注销在短信校验之前被阻止，且不消耗注销验证码。
     */
    @Test
    @DisplayName("未过期会员阻止注销且不消耗注销验证码")
    void blocksActiveMembershipBeforeSmsValidation() {
        when(subscriptionRepository.findUnexpiredByUserId(USER_ID)).thenReturn(Mono.just(new SubscriptionEntity()));

        StepVerifier.create(service.deleteCurrentUser(OPEN_ID, CODE, "request-1", "127.0.0.1", "trace-1"))
                .verifyErrorSatisfies(
                        error -> assertBusinessCode(error, ResultCode.ACCOUNT_DELETION_MEMBERSHIP_ACTIVE));

        verify(smsVerificationCoordinator, never())
                .validate(anyString(), anyString(), eq(SmsCodePurpose.ACCOUNT_DELETION));
        verify(accountDeletionPort, never()).deactivateAndEnqueue(any());
    }

    /**
     * 验证错误或过期的注销验证码无法让账号失效，且不会进入注销执行。
     */
    @Test
    @DisplayName("错误或过期注销验证码不能让账号失效")
    void rejectsInvalidDeletionCode() {
        when(smsVerificationCoordinator.validate(PHONE, CODE, SmsCodePurpose.ACCOUNT_DELETION))
                .thenReturn(Mono.just(false));

        StepVerifier.create(service.deleteCurrentUser(OPEN_ID, CODE, "request-1", "127.0.0.1", "trace-1"))
                .verifyErrorSatisfies(error -> assertBusinessCode(error, ResultCode.SMS_CODE_INVALID));

        verify(accountDeletionPort, never()).deactivateAndEnqueue(any());
    }

    /**
     * 验证注销成功后返回异步任务，并立即吊销身份、令牌、票据并清除用户缓存。
     */
    @Test
    @DisplayName("注销成功后返回异步任务并同步吊销身份、令牌、票据和用户缓存")
    void acceptsDeletionAndInvalidatesRuntimeStateImmediately() {
        AtomicReference<AccountDeletionPort.DeletionCommand> command = new AtomicReference<>();
        when(accountDeletionPort.deactivateAndEnqueue(any())).thenAnswer(invocation -> {
            command.set(invocation.getArgument(0));
            return Mono.just(new AccountDeletionPort.AcceptedDeletion(9001L, "request-1", "ACCEPTED"));
        });

        StepVerifier.create(service.deleteCurrentUser(OPEN_ID, CODE, "request-1", "127.0.0.1", "trace-1"))
                .assertNext(response -> {
                    assertThat(response.getRequestId()).isEqualTo("request-1");
                    assertThat(response.getJobId()).isEqualTo("9001");
                    assertThat(response.getStatus()).isEqualTo("ACCEPTED");
                })
                .verifyComplete();

        assertThat(command.get()).satisfies(value -> {
            assertThat(value.userId()).isEqualTo(USER_ID);
            assertThat(value.openId()).isEqualTo(OPEN_ID);
            assertThat(value.phone()).isEqualTo(PHONE);
            assertThat(value.requestId()).isEqualTo("request-1");
            assertThat(value.clientIp()).isEqualTo("127.0.0.1");
            assertThat(value.traceId()).isEqualTo("trace-1");
        });
        verify(tokenBlacklistService).revokeSubject(eq(OPEN_ID), anyLong());
        verify(tokenService).deleteToken(OPEN_ID);
        verify(authTicketService).revokeIdentity(PHONE, OPEN_ID);
        verify(cacheConsistencyPort).evict("user:id", String.valueOf(USER_ID));
        verify(cacheConsistencyPort).evict("user:openId", OPEN_ID);
    }

    /**
     * 验证持久化端返回空表示重复注销，且不会重复执行运行时状态吊销。
     */
    @Test
    @DisplayName("持久化端返回空表示重复注销")
    void reportsAlreadyCompletedWhenPersistenceReturnsEmpty() {
        when(accountDeletionPort.deactivateAndEnqueue(any())).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteCurrentUser(OPEN_ID, CODE, "request-1", "127.0.0.1", "trace-1"))
                .verifyErrorSatisfies(
                        error -> assertBusinessCode(error, ResultCode.ACCOUNT_DELETION_ALREADY_COMPLETED));

        verify(tokenBlacklistService, never()).revokeSubject(anyString(), anyLong());
    }

    /**
     * 断言异常为业务异常且错误码与期望值一致。
     *
     * @param error 被测链路抛出的异常
     * @param expected 期望的业务错误码
     */
    private void assertBusinessCode(Throwable error, ResultCode expected) {
        assertThat(error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) error).getCode()).isEqualTo(expected.getCode());
    }
}
