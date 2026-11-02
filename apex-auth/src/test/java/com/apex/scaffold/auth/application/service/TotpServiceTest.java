package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.model.entity.MfaConfigEntity;
import com.apex.scaffold.auth.domain.model.entity.TotpEntity;
import com.apex.scaffold.auth.domain.repository.MfaConfigRepository;
import com.apex.scaffold.auth.domain.repository.TotpRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * TotpService 单元测试
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TotpService 单元测试")
class TotpServiceTest {
    @Test
    void shouldKeepFailureLogsSafeAndSingle() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(TotpService.class);
        var originalLevel = logger.getLevel();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        logger.addAppender(appender);
        try {
            mockTotp.setSecret("!");
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));
            StepVerifier.create(totpService.verifyCode(100L, "123456", "test-tenant"))
                    .expectNext(false)
                    .verifyComplete();
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.get(0).getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    @Mock
    private TotpRepository totpRepository;

    @Mock
    private MfaConfigRepository mfaConfigRepository;

    @InjectMocks
    private TotpService totpService;

    private TotpEntity mockTotp;

    @BeforeEach
    void setUp() {
        mockTotp = TotpEntity.builder()
                .id(1L)
                .userId(100L)
                .tenantId("test-tenant")
                .secret("JBSWY3DPEHPK3PXP")
                .verified(true)
                .enabled(true)
                .backupCodes(
                        "12345678,23456789,34567890,45678901,56789012,67890123,78901234,89012345,90123456,01234567")
                .usedBackupCodes(null)
                .version(1)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 防重放（A4）：缺省桩保持时间步消费成功
        lenient()
                .when(totpRepository.markStepUsed(anyLong(), anyString(), anyLong()))
                .thenReturn(Mono.just(Boolean.TRUE));
    }

    @Nested
    @DisplayName("generateSecret() 方法测试")
    class GenerateSecretTest {

        @Test
        @DisplayName("成功生成 TOTP 密钥、OTP Auth URL 和备份码")
        void testGenerateSecretSuccess() {
            // Given
            when(totpRepository.save(any(TotpEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            // When
            Mono<TotpService.TotpSetupResult> result =
                    totpService.generateSecret(100L, "test-tenant", "Apex Scaffold", "user@example.com");

            // Then
            StepVerifier.create(result)
                    .assertNext(setupResult -> {
                        assertThat(setupResult.secret()).isNotNull();
                        assertThat(setupResult.secret()).hasSize(32); // Base32 编码后长度
                        assertThat(setupResult.otpAuthUrl()).contains("otpauth://totp/");
                        assertThat(setupResult.otpAuthUrl()).contains("Apex Scaffold");
                        assertThat(setupResult.otpAuthUrl()).contains("user@example.com");
                        assertThat(setupResult.backupCodes()).hasSize(10);
                        assertThat(setupResult.backupCodes()).allMatch(code -> code.length() == 8);
                    })
                    .verifyComplete();

            verify(totpRepository).save(any(TotpEntity.class));
        }

        @Test
        @DisplayName("保存的 TOTP 实体包含正确的字段")
        void testGenerateSecretSavesCorrectEntity() {
            // Given
            ArgumentCaptor<TotpEntity> entityCaptor = ArgumentCaptor.forClass(TotpEntity.class);
            when(totpRepository.save(entityCaptor.capture()))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            // When
            totpService
                    .generateSecret(100L, "test-tenant", "Apex", "user@test.com")
                    .block();

            // Then
            TotpEntity savedEntity = entityCaptor.getValue();
            assertThat(savedEntity.getUserId()).isEqualTo(100L);
            assertThat(savedEntity.getTenantId()).isEqualTo("test-tenant");
            assertThat(savedEntity.getVerified()).isFalse();
            assertThat(savedEntity.getEnabled()).isFalse();
            assertThat(savedEntity.getBackupCodes()).isNotNull();
            assertThat(savedEntity.getBackupCodes().split(",")).hasSize(10);
        }
    }

    @Nested
    @DisplayName("verifyCode() 方法测试")
    class VerifyCodeTest {

        @Test
        @DisplayName("TOTP 未启用时返回 false")
        void testVerifyCodeWhenNotEnabled() {
            // Given
            mockTotp.setEnabled(false);
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // When
            Mono<Boolean> result = totpService.verifyCode(100L, "123456", "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();
        }

        @Test
        @DisplayName("TOTP 不存在时返回 false")
        void testVerifyCodeWhenTotpNotFound() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.empty());

            // When
            Mono<Boolean> result = totpService.verifyCode(100L, "123456", "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();
        }

        @Test
        @DisplayName("验证码格式错误时返回 false")
        void testVerifyCodeWithInvalidFormat() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // When & Then - 验证码长度不是 6 位
            StepVerifier.create(totpService.verifyCode(100L, "12345", "test-tenant"))
                    .expectNext(false)
                    .verifyComplete();

            StepVerifier.create(totpService.verifyCode(100L, "1234567", "test-tenant"))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("使用当前时间生成的验证码可以验证成功")
        void testVerifyCodeWithCurrentCode() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // 生成当前时间的验证码
            String currentCode = totpService.generateCurrentCode(mockTotp.getSecret());

            // When
            Mono<Boolean> result = totpService.verifyCode(100L, currentCode, "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(true).verifyComplete();
        }
    }

    @Nested
    @DisplayName("verifyAndEnable() 方法测试")
    class VerifyAndEnableTest {

        @Test
        @DisplayName("验证码错误时返回 false 且不更新状态")
        void testVerifyAndEnableWithWrongCode() {
            // Given
            mockTotp.setVerified(false);
            mockTotp.setEnabled(false);
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // When
            Mono<Boolean> result = totpService.verifyAndEnable(100L, "000000", "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();

            verify(totpRepository, never()).update(any(TotpEntity.class));
            verify(mfaConfigRepository, never()).save(any(MfaConfigEntity.class));
        }

        @Test
        @DisplayName("验证码正确时启用 TOTP 并创建 MFA 配置")
        void testVerifyAndEnableSuccess() {
            // Given
            mockTotp.setVerified(false);
            mockTotp.setEnabled(false);
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));
            when(totpRepository.update(any(TotpEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(mfaConfigRepository.save(any(MfaConfigEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            // 生成当前时间的正确验证码
            String currentCode = totpService.generateCurrentCode(mockTotp.getSecret());

            // When
            Mono<Boolean> result = totpService.verifyAndEnable(100L, currentCode, "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(true).verifyComplete();

            verify(totpRepository).update(argThat(entity -> entity.getVerified() && entity.getEnabled()));
            verify(mfaConfigRepository)
                    .save(argThat(config -> config.getMfaType().equals("TOTP") && config.getEnabled()));
        }

        @Test
        @DisplayName("TOTP 不存在时返回 false")
        void testVerifyAndEnableWhenTotpNotFound() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.empty());

            // When
            Mono<Boolean> result = totpService.verifyAndEnable(100L, "123456", "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();
        }
    }

    @Nested
    @DisplayName("disable() 方法测试")
    class DisableTest {

        @Test
        @DisplayName("成功禁用 TOTP 和 MFA 配置")
        void testDisableSuccess() {
            // Given
            MfaConfigEntity mfaConfig = MfaConfigEntity.builder()
                    .id(1L)
                    .userId(100L)
                    .tenantId("test-tenant")
                    .mfaType("TOTP")
                    .enabled(true)
                    .required(false)
                    .build();

            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));
            when(totpRepository.update(any(TotpEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(mfaConfigRepository.findByUserIdAndType(100L, "TOTP", "test-tenant"))
                    .thenReturn(Mono.just(mfaConfig));
            when(mfaConfigRepository.update(any(MfaConfigEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            // When
            Mono<Boolean> result = totpService.disable(100L, "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(true).verifyComplete();

            verify(totpRepository).update(argThat(entity -> !entity.getEnabled()));
            verify(mfaConfigRepository).update(argThat(config -> !config.getEnabled()));
        }

        @Test
        @DisplayName("TOTP 不存在时返回 false")
        void testDisableWhenTotpNotFound() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.empty());

            // When
            Mono<Boolean> result = totpService.disable(100L, "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();

            verify(totpRepository, never()).update(any());
            verify(mfaConfigRepository, never()).update(any());
        }
    }

    @Nested
    @DisplayName("verifyBackupCode() 方法测试")
    class VerifyBackupCodeTest {

        @Test
        @DisplayName("使用有效的备份码验证成功")
        void testVerifyBackupCodeSuccess() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));
            when(totpRepository.update(any(TotpEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            // When
            Mono<Boolean> result = totpService.verifyBackupCode(100L, "12345678", "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(true).verifyComplete();

            verify(totpRepository).findByUserId(100L, "test-tenant");
            verify(totpRepository).update(org.mockito.ArgumentMatchers.argThat(entity -> "12345678"
                    .equals(entity.getUsedBackupCodes())));
            verify(totpRepository).update(any(TotpEntity.class));
        }

        @Test
        @DisplayName("使用无效的备份码验证失败")
        void testVerifyBackupCodeWithInvalidCode() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // When
            Mono<Boolean> result = totpService.verifyBackupCode(100L, "99999999", "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();

            verify(totpRepository, times(1)).findByUserId(100L, "test-tenant");
            verify(totpRepository, never()).update(any());
        }

        @Test
        @DisplayName("TOTP 不存在时返回 false")
        void testVerifyBackupCodeWhenTotpNotFound() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.empty());

            // When
            Mono<Boolean> result = totpService.verifyBackupCode(100L, "12345678", "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getTotpConfig() 方法测试")
    class GetTOTPConfigTest {

        @Test
        @DisplayName("成功获取 TOTP 配置")
        void testGetTOTPConfigSuccess() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // When
            Mono<TotpEntity> result = totpService.getTotpConfig(100L, "test-tenant");

            // Then
            StepVerifier.create(result)
                    .assertNext(entity -> {
                        assertThat(entity.getUserId()).isEqualTo(100L);
                        assertThat(entity.getTenantId()).isEqualTo("test-tenant");
                        assertThat(entity.getSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("TOTP 不存在时返回空")
        void testGetTOTPConfigWhenNotFound() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.empty());

            // When
            Mono<TotpEntity> result = totpService.getTotpConfig(100L, "test-tenant");

            // Then
            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("isEnabled() 方法测试")
    class IsEnabledTest {

        @Test
        @DisplayName("TOTP 已启用时返回 true")
        void testIsEnabledWhenEnabled() {
            // Given
            mockTotp.setEnabled(true);
            mockTotp.setVerified(true);
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // When
            Mono<Boolean> result = totpService.isEnabled(100L, "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(true).verifyComplete();
        }

        @Test
        @DisplayName("TOTP 未启用时返回 false")
        void testIsEnabledWhenNotEnabled() {
            // Given
            mockTotp.setEnabled(false);
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.just(mockTotp));

            // When
            Mono<Boolean> result = totpService.isEnabled(100L, "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();
        }

        @Test
        @DisplayName("TOTP 不存在时返回 false")
        void testIsEnabledWhenNotFound() {
            // Given
            when(totpRepository.findByUserId(100L, "test-tenant")).thenReturn(Mono.empty());

            // When
            Mono<Boolean> result = totpService.isEnabled(100L, "test-tenant");

            // Then
            StepVerifier.create(result).expectNext(false).verifyComplete();
        }
    }

    @Nested
    @DisplayName("generateCurrentCode() 方法测试")
    class GenerateCurrentCodeTest {

        @Test
        @DisplayName("生成的验证码为 6 位数字")
        void testGenerateCurrentCodeFormat() {
            // Given
            String secret = "JBSWY3DPEHPK3PXP";

            // When
            String code = totpService.generateCurrentCode(secret);

            // Then
            assertThat(code).hasSize(6);
            assertThat(code).matches("\\d{6}");
        }

        @Test
        @DisplayName("相同密钥在相同时间窗口生成相同验证码")
        void testGenerateCurrentCodeConsistency() {
            // Given
            String secret = "JBSWY3DPEHPK3PXP";

            // When
            String code1 = totpService.generateCurrentCode(secret);
            String code2 = totpService.generateCurrentCode(secret);

            // Then
            assertThat(code1).isEqualTo(code2);
        }

        @Test
        @DisplayName("不同密钥生成不同验证码")
        void testGenerateCurrentCodeDifferentSecrets() {
            // Given
            String secret1 = "JBSWY3DPEHPK3PXP";
            String secret2 = "KBSWY3DPEHPK3PXQ";

            // When
            String code1 = totpService.generateCurrentCode(secret1);
            String code2 = totpService.generateCurrentCode(secret2);

            // Then
            assertThat(code1).isNotEqualTo(code2);
        }
    }
}
