package com.apex.scaffold.auth.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.dto.PasswordRecoveryConfirmRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryInitiateRequest;
import com.apex.scaffold.auth.application.dto.PasswordRecoveryVerifyRequest;
import com.apex.scaffold.auth.domain.model.entity.PasswordRecoveryEntity;
import com.apex.scaffold.auth.domain.repository.PasswordRecoveryAccountDirectory;
import com.apex.scaffold.auth.domain.repository.PasswordRecoveryRepository;
import com.apex.scaffold.auth.domain.service.PasswordPolicyService;
import com.apex.scaffold.auth.domain.service.RecoveryChallengeSender;
import com.apex.scaffold.auth.domain.service.RecoveryCredentialStore;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link PasswordRecoveryApplicationService} 单元测试。
 *
 * <p>覆盖 T14 关键场景：账号存在性不泄露（占位恢复单）、多命中人工恢复、
 * 验证码一次性消费与失败上限、确认令牌原子消费、密码策略、并发确认最多一次成功、
 * 撤销失败返回 PENDING。仅验证编排逻辑，不含真实 Redis/MySQL/邮件投递。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class PasswordRecoveryApplicationServiceTest {

    private static final String RECOVERY_ID = "b1e0a6d4-6f2c-4a5e-9d3a-1f2b3c4d5e6f";

    @Mock
    private PasswordRecoveryAccountDirectory accountDirectory;

    @Mock
    private PasswordRecoveryRepository recoveryRepository;

    @Mock
    private RecoveryCredentialStore credentialStore;

    @Mock
    private RecoveryChallengeSender challengeSender;

    @Mock
    private PasswordPolicyService passwordPolicyService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private PasswordRecoveryApplicationService service;

    private PasswordRecoveryEntity requestedEntity;

    private PasswordRecoveryEntity verifiedEntity;

    @BeforeEach
    void setUp() {
        requestedEntity = PasswordRecoveryEntity.builder()
                .id(RECOVERY_ID)
                .userId(100L)
                .tenantId("tenant-1")
                .username("alice")
                .channel(PasswordRecoveryEntity.CHANNEL_EMAIL)
                .contactHash("c0ffee")
                .status(PasswordRecoveryEntity.STATUS_REQUESTED)
                .attemptCount(0)
                .requestIpHash("ip-hash")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .updatedAt(LocalDateTime.now().minusMinutes(1))
                .build();
        verifiedEntity = PasswordRecoveryEntity.builder()
                .id(requestedEntity.getId())
                .userId(requestedEntity.getUserId())
                .tenantId(requestedEntity.getTenantId())
                .username(requestedEntity.getUsername())
                .channel(requestedEntity.getChannel())
                .contactHash(requestedEntity.getContactHash())
                .status(PasswordRecoveryEntity.STATUS_VERIFIED)
                .attemptCount(requestedEntity.getAttemptCount())
                .requestIpHash(requestedEntity.getRequestIpHash())
                .expiresAt(requestedEntity.getExpiresAt())
                .createdAt(requestedEntity.getCreatedAt())
                .updatedAt(requestedEntity.getUpdatedAt())
                .build();
    }

    private PasswordPolicyService.PasswordValidationResult passed() {
        return new PasswordPolicyService.PasswordValidationResult(true, 95, List.of());
    }

    private PasswordPolicyService.PasswordValidationResult failed() {
        return new PasswordPolicyService.PasswordValidationResult(false, 20, List.of("密码强度不足"));
    }

    @Nested
    @DisplayName("发起申请")
    class Initiate {

        @Test
        @DisplayName("限流超限时拒绝申请")
        void rateLimited() {
            when(credentialStore.tryAcquireRateLimit(anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(false));

            PasswordRecoveryInitiateRequest request = new PasswordRecoveryInitiateRequest();
            request.setChannel("EMAIL");
            request.setContact("user@example.com");

            StepVerifier.create(service.initiate(request, "1.2.3.4"))
                    .expectErrorSatisfies(
                            ex -> org.assertj.core.api.Assertions.assertThat(ex).isInstanceOf(BusinessException.class))
                    .verify();

            verify(recoveryRepository, never()).save(any());
        }

        @Test
        @DisplayName("账号不存在时返回同形态占位恢复单，不落库不发码")
        void unknownAccountReturnsDecoy() {
            when(credentialStore.tryAcquireRateLimit(anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(true));
            when(recoveryRepository.countRecentByContactHash(anyString(), any()))
                    .thenReturn(Mono.just(0L));
            when(accountDirectory.resolveByEmail("user@example.com"))
                    .thenReturn(Mono.just(PasswordRecoveryAccountDirectory.Resolution.none()));

            PasswordRecoveryInitiateRequest request = new PasswordRecoveryInitiateRequest();
            request.setChannel("EMAIL");
            request.setContact("user@example.com");

            StepVerifier.create(service.initiate(request, "1.2.3.4"))
                    .assertNext(resp -> {
                        org.assertj.core.api.Assertions.assertThat(resp.getRecoveryId())
                                .isNotBlank();
                        org.assertj.core.api.Assertions.assertThat(resp.getExpiresIn())
                                .isEqualTo(600L);
                    })
                    .verifyComplete();

            verify(recoveryRepository, never()).save(any());
            verify(challengeSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("联系方式命中多个账号时落人工恢复单，不发码")
        void multipleAccountsGoManual() {
            when(credentialStore.tryAcquireRateLimit(anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(true));
            when(recoveryRepository.countRecentByContactHash(anyString(), any()))
                    .thenReturn(Mono.just(0L));
            when(accountDirectory.resolveByEmail("user@example.com"))
                    .thenReturn(Mono.just(PasswordRecoveryAccountDirectory.Resolution.multiple()));
            when(recoveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

            PasswordRecoveryInitiateRequest request = new PasswordRecoveryInitiateRequest();
            request.setChannel("EMAIL");
            request.setContact("user@example.com");

            StepVerifier.create(service.initiate(request, "1.2.3.4"))
                    .assertNext(resp -> org.assertj.core.api.Assertions.assertThat(resp.getRecoveryId())
                            .isNotBlank())
                    .verifyComplete();

            verify(recoveryRepository)
                    .save(org.mockito.ArgumentMatchers.argThat(
                            entity -> PasswordRecoveryEntity.STATUS_MANUAL_REQUIRED.equals(entity.getStatus())));
            verify(challengeSender, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("唯一命中时创建恢复单、存储验证码摘要并投递")
        void uniqueAccountSendsChallenge() {
            when(credentialStore.tryAcquireRateLimit(anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(true));
            when(recoveryRepository.countRecentByContactHash(anyString(), any()))
                    .thenReturn(Mono.just(0L));
            when(accountDirectory.resolveByEmail("user@example.com"))
                    .thenReturn(
                            Mono.just(PasswordRecoveryAccountDirectory.Resolution.unique(100L, "tenant-1", "alice")));
            when(recoveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(credentialStore.storeCode(anyString(), anyString(), any())).thenReturn(Mono.just(true));
            when(challengeSender.send(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

            PasswordRecoveryInitiateRequest request = new PasswordRecoveryInitiateRequest();
            request.setChannel("EMAIL");
            request.setContact("user@example.com");

            StepVerifier.create(service.initiate(request, "1.2.3.4"))
                    .assertNext(resp -> {
                        org.assertj.core.api.Assertions.assertThat(resp.getRecoveryId())
                                .isNotBlank();
                        org.assertj.core.api.Assertions.assertThat(resp.getExpiresIn())
                                .isEqualTo(600L);
                    })
                    .verifyComplete();

            verify(recoveryRepository)
                    .save(org.mockito.ArgumentMatchers.argThat(
                            entity -> PasswordRecoveryEntity.STATUS_REQUESTED.equals(entity.getStatus())));
            verify(credentialStore).storeCode(anyString(), anyString(), any());
            verify(challengeSender).send(eq("EMAIL"), eq("user@example.com"), anyString());
        }

        @Test
        @DisplayName("SMS 通道：唯一命中手机号时按 SMS 投递")
        void smsChannelSendsChallenge() {
            when(credentialStore.tryAcquireRateLimit(anyString(), anyInt(), any()))
                    .thenReturn(Mono.just(true));
            when(recoveryRepository.countRecentByContactHash(anyString(), any()))
                    .thenReturn(Mono.just(0L));
            when(accountDirectory.resolveByPhone("13800138000"))
                    .thenReturn(
                            Mono.just(PasswordRecoveryAccountDirectory.Resolution.unique(100L, "tenant-1", "alice")));
            when(recoveryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
            when(credentialStore.storeCode(anyString(), anyString(), any())).thenReturn(Mono.just(true));
            when(challengeSender.send(anyString(), anyString(), anyString())).thenReturn(Mono.empty());

            PasswordRecoveryInitiateRequest request = new PasswordRecoveryInitiateRequest();
            request.setChannel("SMS");
            request.setContact("13800138000");

            StepVerifier.create(service.initiate(request, "1.2.3.4"))
                    .assertNext(resp -> org.assertj.core.api.Assertions.assertThat(resp.getRecoveryId())
                            .isNotBlank())
                    .verifyComplete();

            verify(challengeSender).send(eq("SMS"), eq("13800138000"), anyString());
        }

        @Test
        @DisplayName("SMS 通道：手机号格式非法时拒绝且不落库")
        void smsChannelRejectsInvalidPhone() {
            PasswordRecoveryInitiateRequest request = new PasswordRecoveryInitiateRequest();
            request.setChannel("SMS");
            request.setContact("12345");

            StepVerifier.create(Mono.defer(() -> service.initiate(request, "1.2.3.4")))
                    .expectError(BusinessException.class)
                    .verify();

            verify(recoveryRepository, never()).save(any());
            verify(challengeSender, never()).send(anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("校验验证码")
    class Verify {

        @Test
        @DisplayName("验证码错误计入失败次数并返回通用错误")
        void wrongCodeCountsAttempt() {
            when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Mono.just(requestedEntity));
            when(credentialStore.verifyAndConsumeCode(eq(RECOVERY_ID), anyString()))
                    .thenReturn(Mono.just(false));
            when(recoveryRepository.incrementAttemptCount(RECOVERY_ID)).thenReturn(Mono.just(1));

            PasswordRecoveryVerifyRequest request = new PasswordRecoveryVerifyRequest();
            request.setRecoveryId(RECOVERY_ID);
            request.setCode("000000");

            StepVerifier.create(service.verify(request))
                    .expectErrorSatisfies(
                            ex -> org.assertj.core.api.Assertions.assertThat(ex).isInstanceOf(BusinessException.class))
                    .verify();

            verify(recoveryRepository).incrementAttemptCount(RECOVERY_ID);
        }

        @Test
        @DisplayName("失败达到上限后恢复单作废")
        void maxAttemptsInvalidates() {
            when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Mono.just(requestedEntity));
            when(credentialStore.verifyAndConsumeCode(eq(RECOVERY_ID), anyString()))
                    .thenReturn(Mono.just(false));
            when(recoveryRepository.incrementAttemptCount(RECOVERY_ID)).thenReturn(Mono.just(5));
            when(recoveryRepository.transition(
                            RECOVERY_ID,
                            PasswordRecoveryEntity.STATUS_REQUESTED,
                            PasswordRecoveryEntity.STATUS_EXPIRED))
                    .thenReturn(Mono.just(true));

            PasswordRecoveryVerifyRequest request = new PasswordRecoveryVerifyRequest();
            request.setRecoveryId(RECOVERY_ID);
            request.setCode("000000");

            StepVerifier.create(service.verify(request))
                    .expectError(BusinessException.class)
                    .verify();

            verify(recoveryRepository)
                    .transition(
                            RECOVERY_ID,
                            PasswordRecoveryEntity.STATUS_REQUESTED,
                            PasswordRecoveryEntity.STATUS_EXPIRED);
        }

        @Test
        @DisplayName("验证码正确时颁发一次性确认令牌并跃迁状态")
        void correctCodeIssuesConfirmToken() {
            when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Mono.just(requestedEntity));
            when(credentialStore.verifyAndConsumeCode(eq(RECOVERY_ID), anyString()))
                    .thenReturn(Mono.just(true));
            when(credentialStore.storeConfirmToken(anyString(), anyString(), any()))
                    .thenReturn(Mono.just(true));
            when(recoveryRepository.transition(
                            RECOVERY_ID,
                            PasswordRecoveryEntity.STATUS_REQUESTED,
                            PasswordRecoveryEntity.STATUS_VERIFIED))
                    .thenReturn(Mono.just(true));

            PasswordRecoveryVerifyRequest request = new PasswordRecoveryVerifyRequest();
            request.setRecoveryId(RECOVERY_ID);
            request.setCode("482913");

            StepVerifier.create(service.verify(request))
                    .assertNext(resp -> {
                        org.assertj.core.api.Assertions.assertThat(resp.getConfirmToken())
                                .isNotBlank();
                        org.assertj.core.api.Assertions.assertThat(resp.getExpiresIn())
                                .isEqualTo(600L);
                    })
                    .verifyComplete();

            verify(credentialStore).storeConfirmToken(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("确认重设")
    class Confirm {

        private PasswordRecoveryConfirmRequest confirmRequest() {
            PasswordRecoveryConfirmRequest request = new PasswordRecoveryConfirmRequest();
            request.setRecoveryId(RECOVERY_ID);
            request.setConfirmToken("confirm-token");
            request.setNewPassword("Str0ng!Passw0rd#2026");
            return request;
        }

        @Test
        @DisplayName("确认令牌不匹配时拒绝，不修改密码")
        void wrongTokenRejected() {
            when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Mono.just(verifiedEntity));
            when(credentialStore.consumeConfirmToken(eq(RECOVERY_ID), anyString()))
                    .thenReturn(Mono.just(false));

            StepVerifier.create(service.confirm(confirmRequest()))
                    .expectError(BusinessException.class)
                    .verify();

            verify(accountDirectory, never()).updatePasswordHash(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("密码策略不通过时拒绝，不修改密码")
        void weakPasswordRejected() {
            when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Mono.just(verifiedEntity));
            when(credentialStore.consumeConfirmToken(eq(RECOVERY_ID), anyString()))
                    .thenReturn(Mono.just(true));
            when(passwordPolicyService.validatePassword(anyString(), anyString(), anyBoolean()))
                    .thenReturn(Mono.just(failed()));

            StepVerifier.create(service.confirm(confirmRequest()))
                    .expectError(BusinessException.class)
                    .verify();

            verify(accountDirectory, never()).updatePasswordHash(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("确认成功后重设密码、终结恢复单并撤销全部会话")
        void successfulConfirmResetsAndRevokes() {
            when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Mono.just(verifiedEntity));
            when(credentialStore.consumeConfirmToken(eq(RECOVERY_ID), anyString()))
                    .thenReturn(Mono.just(true));
            when(passwordPolicyService.validatePassword(anyString(), anyString(), anyBoolean()))
                    .thenReturn(Mono.just(passed()));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
            when(accountDirectory.updatePasswordHash(100L, "tenant-1", "$2a$10$hashed"))
                    .thenReturn(Mono.just(true));
            when(recoveryRepository.transition(
                            RECOVERY_ID,
                            PasswordRecoveryEntity.STATUS_VERIFIED,
                            PasswordRecoveryEntity.STATUS_CONSUMED))
                    .thenReturn(Mono.just(true));
            when(tokenService.revokeAllUserTokens(100L, "tenant-1")).thenReturn(Mono.empty());

            StepVerifier.create(service.confirm(confirmRequest()))
                    .assertNext(resp -> {
                        org.assertj.core.api.Assertions.assertThat(resp.getStatus())
                                .isEqualTo("PASSWORD_RESET");
                        org.assertj.core.api.Assertions.assertThat(resp.getSessionRevocation())
                                .isEqualTo("COMPLETED");
                    })
                    .verifyComplete();

            verify(accountDirectory).updatePasswordHash(100L, "tenant-1", "$2a$10$hashed");
            verify(tokenService).revokeAllUserTokens(100L, "tenant-1");
            verify(domainEventPublisher).publish(any());
        }

        @Test
        @DisplayName("撤销会话失败时返回 PENDING，不伪称全端退出")
        void revocationFailureReturnsPending() {
            when(recoveryRepository.findById(RECOVERY_ID)).thenReturn(Mono.just(verifiedEntity));
            when(credentialStore.consumeConfirmToken(eq(RECOVERY_ID), anyString()))
                    .thenReturn(Mono.just(true));
            when(passwordPolicyService.validatePassword(anyString(), anyString(), anyBoolean()))
                    .thenReturn(Mono.just(passed()));
            when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
            when(accountDirectory.updatePasswordHash(100L, "tenant-1", "$2a$10$hashed"))
                    .thenReturn(Mono.just(true));
            when(recoveryRepository.transition(
                            RECOVERY_ID,
                            PasswordRecoveryEntity.STATUS_VERIFIED,
                            PasswordRecoveryEntity.STATUS_CONSUMED))
                    .thenReturn(Mono.just(true));
            when(tokenService.revokeAllUserTokens(100L, "tenant-1"))
                    .thenReturn(Mono.error(new IllegalStateException("redis down")));

            StepVerifier.create(service.confirm(confirmRequest()))
                    .assertNext(resp -> {
                        org.assertj.core.api.Assertions.assertThat(resp.getStatus())
                                .isEqualTo("PASSWORD_RESET");
                        org.assertj.core.api.Assertions.assertThat(resp.getSessionRevocation())
                                .isEqualTo("PENDING");
                    })
                    .verifyComplete();

            verify(domainEventPublisher, never()).publish(any());
        }
    }
}
