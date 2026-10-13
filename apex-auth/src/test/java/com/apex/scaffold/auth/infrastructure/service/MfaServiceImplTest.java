package com.apex.scaffold.auth.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.model.MfaConfig;
import com.apex.scaffold.auth.domain.service.MfaService;
import com.apex.scaffold.auth.infrastructure.repository.MfaConfigRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.security.EncryptionService;
import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.infrastructure.mail.MailService;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("MfaServiceImpl 单元测试")
class MfaServiceImplTest {

    @Mock
    private MfaConfigRepository mfaConfigRepository;

    @Mock
    private EncryptionService encryptionService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private SmsCodeService smsCodeService;

    @Mock
    private MailService mailService;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private MfaServiceImpl mfaService;

    @BeforeEach
    void setUp() {
        mfaService = new MfaServiceImpl(
                mfaConfigRepository, encryptionService, auditLogService, redisTemplate, smsCodeService, mailService);
    }

    @Test
    @DisplayName("启用TOTP - 成功生成密钥和二维码")
    void enableTotp_success() {
        String userId = "user123";
        String tenantId = "tenant1";
        String encryptedSecret = "encrypted_secret";

        MfaConfig savedConfig = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.PENDING)
                .secret(encryptedSecret)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(encryptionService.encrypt(anyString())).thenReturn(Mono.just(encryptedSecret));
        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(savedConfig));

        Mono<MfaService.TotpSetupInfo> result = mfaService.enableTotp(userId, tenantId);

        StepVerifier.create(result)
                .assertNext(info -> {
                    assertThat(info).isNotNull();
                    assertThat(info.secret()).isNotEmpty();
                    assertThat(info.qrCodeUrl()).contains("otpauth://totp/");
                    assertThat(info.manualEntryKey()).isNotEmpty();
                })
                .verifyComplete();

        verify(encryptionService).encrypt(anyString());
        verify(mfaConfigRepository).save(any(MfaConfig.class));
    }

    @Test
    @DisplayName("验证并激活TOTP - 按指定租户查询配置")
    void verifyAndActivateTotp_usesCurrentTenant() {
        String userId = "user123";
        String tenantId = "tenant1";
        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId))
                .thenReturn(Mono.empty());

        StepVerifier.create(mfaService.verifyAndActivateTotp(userId, "123456", tenantId))
                .verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId);
        verify(mfaConfigRepository, never()).findByUserIdAndType(userId, MfaConfig.MfaType.TOTP);
    }

    @Test
    @DisplayName("验证并激活TOTP - 跨租户隔离：同userId不同租户不查询无租户仓储方法")
    void verifyAndActivateTotp_isolatesTenants() {
        String userId = "user123";
        String tenantA = "tenantA";
        String tenantB = "tenantB";

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantA))
                .thenReturn(Mono.empty());
        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantB))
                .thenReturn(Mono.empty());

        StepVerifier.create(mfaService.verifyAndActivateTotp(userId, "123456", tenantA))
                .verifyComplete();
        StepVerifier.create(mfaService.verifyAndActivateTotp(userId, "123456", tenantB))
                .verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantA);
        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantB);
        verify(mfaConfigRepository, never()).findByUserIdAndType(anyString(), any());
    }

    @Test
    @DisplayName("验证并激活TOTP - TOTP已激活")
    void verifyAndActivateTotp_alreadyActive() {
        String userId = "user123";
        String code = "123456";
        String tenantId = "tenant1";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId))
                .thenReturn(Mono.just(config));

        Mono<MfaService.MfaActivationResult> result = mfaService.verifyAndActivateTotp(userId, code, tenantId);

        StepVerifier.create(result).expectError(IllegalStateException.class).verify();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId);
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("启用短信MFA - 成功")
    void enableSms_success() {
        String userId = "user123";
        String tenantId = "tenant1";
        String phoneNumber = "+1234567890";

        MfaConfig savedConfig = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.SMS)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .deviceInfo(phoneNumber)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(savedConfig));

        Mono<MfaConfig> result = mfaService.enableSms(userId, tenantId, phoneNumber);

        StepVerifier.create(result)
                .assertNext(config -> {
                    assertThat(config).isNotNull();
                    assertThat(config.getUserId()).isEqualTo(userId);
                    assertThat(config.getType()).isEqualTo(MfaConfig.MfaType.SMS);
                    assertThat(config.getStatus()).isEqualTo(MfaConfig.MfaStatus.ACTIVE);
                    assertThat(config.getDeviceInfo()).isEqualTo(phoneNumber);
                })
                .verifyComplete();

        verify(mfaConfigRepository).save(any(MfaConfig.class));
    }

    @Test
    @DisplayName("启用邮箱MFA - 成功")
    void enableEmail_success() {
        String userId = "user123";
        String tenantId = "tenant1";
        String email = "user@example.com";

        MfaConfig savedConfig = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.EMAIL)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .deviceInfo(email)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(savedConfig));

        Mono<MfaConfig> result = mfaService.enableEmail(userId, tenantId, email);

        StepVerifier.create(result)
                .assertNext(config -> {
                    assertThat(config).isNotNull();
                    assertThat(config.getUserId()).isEqualTo(userId);
                    assertThat(config.getType()).isEqualTo(MfaConfig.MfaType.EMAIL);
                    assertThat(config.getStatus()).isEqualTo(MfaConfig.MfaStatus.ACTIVE);
                    assertThat(config.getDeviceInfo()).isEqualTo(email);
                })
                .verifyComplete();

        verify(mfaConfigRepository).save(any(MfaConfig.class));
    }

    @Test
    @DisplayName("禁用MFA - 成功")
    void disableMfa_success() {
        String userId = "user123";
        String tenantId = "tenant1";
        MfaConfig.MfaType type = MfaConfig.MfaType.TOTP;

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(type)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .build();

        MfaConfig disabledConfig = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(type)
                .status(MfaConfig.MfaStatus.DISABLED)
                .secret("encrypted_secret")
                .updatedAt(LocalDateTime.now())
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, type, tenantId))
                .thenReturn(Mono.just(config));
        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(disabledConfig));

        Mono<Void> result = mfaService.disableMfa(userId, type, tenantId);

        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<MfaConfig> captor = ArgumentCaptor.forClass(MfaConfig.class);
        verify(mfaConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MfaConfig.MfaStatus.DISABLED);
    }

    @Test
    @DisplayName("验证MFA代码 - TOTP验证成功")
    void verifyMfaCode_totpValid() {
        String userId = "user123";
        String tenantId = "tenant1";
        String code = "123456";
        String secret = "JBSWY3DPEHPK3PXP";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .failedAttempts(0)
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId))
                .thenReturn(Mono.just(config));
        when(encryptionService.decrypt("encrypted_secret")).thenReturn(Mono.just(secret));
        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(config));

        Mono<Boolean> result = mfaService.verifyMfaCode(userId, code, MfaConfig.MfaType.TOTP, tenantId);

        StepVerifier.create(result).expectNextCount(1).verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId);
        verify(encryptionService).decrypt("encrypted_secret");
    }

    @Test
    @DisplayName("验证MFA代码 - MFA未激活")
    void verifyMfaCode_notActive() {
        String userId = "user123";
        String tenantId = "tenant1";
        String code = "123456";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.PENDING)
                .secret("encrypted_secret")
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId))
                .thenReturn(Mono.just(config));

        Mono<Boolean> result = mfaService.verifyMfaCode(userId, code, MfaConfig.MfaType.TOTP, tenantId);

        StepVerifier.create(result).expectNext(false).verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId);
        verify(encryptionService, never()).decrypt(anyString());
    }

    @Test
    @DisplayName("验证MFA代码 - MFA已锁定")
    void verifyMfaCode_locked() {
        String userId = "user123";
        String tenantId = "tenant1";
        String code = "123456";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .failedAttempts(6)
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId))
                .thenReturn(Mono.just(config));

        Mono<Boolean> result = mfaService.verifyMfaCode(userId, code, MfaConfig.MfaType.TOTP, tenantId);

        StepVerifier.create(result).expectError().verify();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId);
    }

    @Test
    @DisplayName("发送MFA验证码 - SMS类型")
    void sendMfaCode_sms() {
        String userId = "user123";
        String tenantId = "tenant1";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.SMS)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .deviceInfo("+1234567890")
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.SMS, tenantId))
                .thenReturn(Mono.just(config));
        when(smsCodeService.sendAndStore(config.getDeviceInfo())).thenReturn(Mono.empty());

        Mono<Void> result = mfaService.sendMfaCode(userId, MfaConfig.MfaType.SMS, tenantId);

        StepVerifier.create(result).verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.SMS, tenantId);
    }

    @Test
    @DisplayName("验证短信OTP - 成功后原子核销并更新配置")
    void verifyMfaCode_smsValid() {
        String userId = "user123";
        String tenantId = "tenant1";
        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.SMS)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .deviceInfo("+1234567890")
                .failedAttempts(1)
                .build();
        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.SMS, tenantId))
                .thenReturn(Mono.just(config));
        when(smsCodeService.validate("+1234567890", "123456")).thenReturn(Mono.just(true));
        when(mfaConfigRepository.save(config)).thenReturn(Mono.just(config));

        StepVerifier.create(mfaService.verifyMfaCode(userId, "123456", MfaConfig.MfaType.SMS, tenantId))
                .expectNext(true)
                .verifyComplete();

        assertThat(config.getFailedAttempts()).isEqualTo(0);
        verify(mfaConfigRepository).save(config);
    }

    @Test
    @DisplayName("验证短信OTP - 失败后增加失败次数")
    void verifyMfaCode_smsInvalid() {
        String userId = "user123";
        String tenantId = "tenant1";
        MfaConfig config = MfaConfig.builder()
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.SMS)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .deviceInfo("+1234567890")
                .failedAttempts(0)
                .build();
        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.SMS, tenantId))
                .thenReturn(Mono.just(config));
        when(smsCodeService.validate("+1234567890", "000000")).thenReturn(Mono.just(false));
        when(mfaConfigRepository.save(config)).thenReturn(Mono.just(config));

        StepVerifier.create(mfaService.verifyMfaCode(userId, "000000", MfaConfig.MfaType.SMS, tenantId))
                .expectNext(false)
                .verifyComplete();

        assertThat(config.getFailedAttempts()).isEqualTo(1);
        verify(mfaConfigRepository).save(config);
    }

    @Test
    @DisplayName("发送邮箱OTP - 写入五分钟Redis并发送邮件")
    void sendMfaCode_email() {
        String userId = "user123";
        String tenantId = "tenant1";
        MfaConfig config = MfaConfig.builder()
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.EMAIL)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .deviceInfo("user@example.com")
                .build();
        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.EMAIL, tenantId))
                .thenReturn(Mono.just(config));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), eq(Duration.ofMinutes(5))))
                .thenReturn(Mono.just(true));

        StepVerifier.create(mfaService.sendMfaCode(userId, MfaConfig.MfaType.EMAIL, tenantId))
                .verifyComplete();

        verify(valueOperations)
                .set(eq("mfa:otp:" + tenantId + ":" + userId + ":EMAIL"), matches("\\d{6}"), eq(Duration.ofMinutes(5)));
        verify(mailService).sendSimple(eq("user@example.com"), anyString(), contains("验证码"));
    }

    @Test
    @DisplayName("获取用户MFA配置")
    void getUserMfaConfigs() {
        String userId = "user123";
        String tenantId = "tenant1";

        MfaConfig config1 = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .build();

        MfaConfig config2 = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.SMS)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .build();

        when(mfaConfigRepository.findByUserIdAndTenantId(userId, tenantId)).thenReturn(Flux.just(config1, config2));

        Flux<MfaConfig> result = mfaService.getUserMfaConfigs(userId, tenantId);

        StepVerifier.create(result).expectNext(config1).expectNext(config2).verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTenantId(userId, tenantId);
    }

    @Test
    @DisplayName("检查MFA是否启用 - 已启用")
    void isMfaEnabled_true() {
        String userId = "user123";
        String tenantId = "tenant1";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .build();

        when(mfaConfigRepository.findByUserIdAndTenantId(userId, tenantId)).thenReturn(Flux.just(config));

        Mono<Boolean> result = mfaService.isMfaEnabled(userId, tenantId);

        StepVerifier.create(result).expectNext(true).verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTenantId(userId, tenantId);
    }

    @Test
    @DisplayName("检查MFA是否启用 - 未启用")
    void isMfaEnabled_false() {
        String userId = "user123";
        String tenantId = "tenant1";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.PENDING)
                .build();

        when(mfaConfigRepository.findByUserIdAndTenantId(userId, tenantId)).thenReturn(Flux.just(config));

        Mono<Boolean> result = mfaService.isMfaEnabled(userId, tenantId);

        StepVerifier.create(result).expectNext(false).verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTenantId(userId, tenantId);
    }

    @Test
    @DisplayName("重新生成备用验证码 - 成功")
    void regenerateBackupCodes_success() {
        String userId = "user123";
        String tenantId = "tenant1";
        String encryptedBackupCodes = "encrypted_backup_codes";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .backupCodes("old_encrypted_backup_codes")
                .build();

        MfaConfig savedConfig = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.TOTP)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .backupCodes(encryptedBackupCodes)
                .updatedAt(LocalDateTime.now())
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId))
                .thenReturn(Mono.just(config));
        when(encryptionService.encrypt(anyString())).thenReturn(Mono.just(encryptedBackupCodes));
        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(savedConfig));

        Mono<MfaService.BackupCodesResult> result = mfaService.regenerateBackupCodes(userId, tenantId);

        StepVerifier.create(result)
                .assertNext(backupCodesResult -> {
                    assertThat(backupCodesResult).isNotNull();
                    assertThat(backupCodesResult.codes()).hasSize(10);
                    for (String code : backupCodesResult.codes()) {
                        assertThat(code).hasSize(8).matches("\\d{8}");
                    }
                })
                .verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId);
        verify(encryptionService).encrypt(anyString());
        verify(mfaConfigRepository).save(any(MfaConfig.class));
    }

    @Test
    @DisplayName("验证备用验证码 - 成功")
    void verifyBackupCode_success() {
        String userId = "user123";
        String tenantId = "tenant1";
        String code = "12345678";
        String backupCodes = "12345678,87654321,11111111";

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.BACKUP_CODE)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .backupCodes("encrypted_backup_codes")
                .failedAttempts(0)
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.BACKUP_CODE, tenantId))
                .thenReturn(Mono.just(config));
        when(encryptionService.decrypt("encrypted_backup_codes")).thenReturn(Mono.just(backupCodes));
        when(encryptionService.encrypt(anyString())).thenReturn(Mono.just("new_encrypted_backup_codes"));
        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(config));

        Mono<Boolean> result = mfaService.verifyBackupCode(userId, code, tenantId);

        StepVerifier.create(result).expectNext(true).verifyComplete();

        verify(mfaConfigRepository).findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.BACKUP_CODE, tenantId);
        verify(encryptionService).decrypt("encrypted_backup_codes");
        verify(encryptionService).encrypt("87654321,11111111");
        verify(mfaConfigRepository).save(config);
        assertThat(config.getBackupCodes()).isEqualTo("new_encrypted_backup_codes");
    }

    @Test
    @DisplayName("验证备用验证码 - 失败并持久化失败次数")
    void verifyBackupCode_invalid() {
        String userId = "user123";
        String tenantId = "tenant1";
        MfaConfig config = MfaConfig.builder()
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.BACKUP_CODE)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .backupCodes("encrypted_backup_codes")
                .failedAttempts(0)
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.BACKUP_CODE, tenantId))
                .thenReturn(Mono.just(config));
        when(encryptionService.decrypt("encrypted_backup_codes")).thenReturn(Mono.just("12345678,87654321"));
        when(mfaConfigRepository.save(config)).thenReturn(Mono.just(config));

        StepVerifier.create(mfaService.verifyBackupCode(userId, "00000000", tenantId))
                .expectNext(false)
                .verifyComplete();

        assertThat(config.getFailedAttempts()).isEqualTo(1);
        verify(mfaConfigRepository).save(config);
        verify(encryptionService, never()).encrypt(anyString());
    }

    @Test
    @DisplayName("解锁MFA - 成功")
    void unlockMfa_success() {
        String userId = "user123";
        String operator = "admin";
        MfaConfig.MfaType type = MfaConfig.MfaType.TOTP;

        MfaConfig config = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId("tenant1")
                .type(type)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .failedAttempts(6)
                .build();

        MfaConfig unlockedConfig = MfaConfig.builder()
                .id(1L)
                .userId(userId)
                .tenantId("tenant1")
                .type(type)
                .status(MfaConfig.MfaStatus.ACTIVE)
                .secret("encrypted_secret")
                .failedAttempts(0)
                .updatedAt(LocalDateTime.now())
                .build();

        when(mfaConfigRepository.findByUserIdAndTypeAndTenantId(userId, type, "tenant1"))
                .thenReturn(Mono.just(config));
        when(mfaConfigRepository.save(any(MfaConfig.class))).thenReturn(Mono.just(unlockedConfig));

        Mono<Void> result = mfaService.unlockMfa(userId, type, operator, "tenant1");

        StepVerifier.create(result).verifyComplete();

        ArgumentCaptor<MfaConfig> captor = ArgumentCaptor.forClass(MfaConfig.class);
        verify(mfaConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getFailedAttempts()).isEqualTo(0);
    }
}
