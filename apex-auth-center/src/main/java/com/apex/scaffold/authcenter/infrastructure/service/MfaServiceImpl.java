package com.apex.scaffold.authcenter.infrastructure.service;

import com.apex.scaffold.authcenter.domain.model.MfaConfig;
import com.apex.scaffold.authcenter.domain.service.MfaService;
import com.apex.scaffold.authcenter.infrastructure.repository.MfaConfigRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.security.EncryptionService;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多因素认证服务实现
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private final MfaConfigRepository mfaConfigRepository;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    private static final String ISSUER = "Apex Scaffold";
    private static final int BACKUP_CODES_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 8;

    @Override
    public Mono<TotpSetupInfo> enableTotp(String userId, String tenantId) {
        // 生成TOTP密钥
        DefaultSecretGenerator secretGenerator = new DefaultSecretGenerator();
        String secret = secretGenerator.generate();

        // 加密存储密钥
        return encryptionService
                .encrypt(secret)
                .flatMap(encryptedSecret -> {
                    // 创建MFA配置
                    MfaConfig config = MfaConfig.builder()
                            .userId(userId)
                            .tenantId(tenantId)
                            .type(MfaConfig.MfaType.TOTP)
                            .status(MfaConfig.MfaStatus.PENDING)
                            .secret(encryptedSecret)
                            .failedAttempts(0)
                            .createdAt(LocalDateTime.now())
                            .build();

                    return mfaConfigRepository.save(config);
                })
                .map(config -> {
                    // 生成二维码URL
                    QrData qrData = new QrData.Builder()
                            .label(userId)
                            .secret(secret)
                            .issuer(ISSUER)
                            .algorithm(HashingAlgorithm.SHA1)
                            .digits(6)
                            .period(30)
                            .build();

                    String qrCodeUrl = qrData.getUri();

                    log.info("用户启用TOTP MFA: userId={}, tenantId={}", userId, tenantId);

                    return new TotpSetupInfo(secret, qrCodeUrl, secret);
                });
    }

    @Override
    public Mono<MfaActivationResult> verifyAndActivateTotp(String userId, String code) {
        return mfaConfigRepository
                .findByUserIdAndType(userId, MfaConfig.MfaType.TOTP)
                .flatMap(config -> {
                    if (config.isActive()) {
                        return Mono.error(new IllegalStateException("TOTP已经激活"));
                    }

                    // 解密密钥
                    return encryptionService.decrypt(config.getSecret()).flatMap(secret -> {
                        // 验证TOTP代码
                        CodeVerifier verifier =
                                new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

                        boolean isValid = verifier.isValidCode(secret, code);

                        if (!isValid) {
                            config.incrementFailedAttempts();
                            return mfaConfigRepository
                                    .save(config)
                                    .then(Mono.error(new IllegalArgumentException("验证码错误")));
                        }

                        // 激活TOTP
                        config.setStatus(MfaConfig.MfaStatus.ACTIVE);
                        config.updateLastVerified();

                        // 生成备用验证码
                        String[] backupCodes = generateBackupCodes();
                        String backupCodesStr = String.join(",", backupCodes);

                        return encryptionService
                                .encrypt(backupCodesStr)
                                .flatMap(encryptedBackupCodes -> {
                                    config.setBackupCodes(encryptedBackupCodes);
                                    config.setUpdatedAt(LocalDateTime.now());
                                    return mfaConfigRepository.save(config);
                                })
                                .map(saved -> {
                                    auditLogService.log(
                                            userId,
                                            config.getTenantId(),
                                            "激活TOTP多因素认证",
                                            "MFA_TOTP_ACTIVATED",
                                            "SUCCESS",
                                            null);
                                    log.info("用户激活TOTP MFA: userId={}", userId);
                                    return new MfaActivationResult(true, backupCodes);
                                });
                    });
                });
    }

    /**
     * 启用短信MFA，创建短信类型的MFA配置并记录审计日志
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param phoneNumber 接收验证码的手机号
     * @return 新创建的短信MFA配置
     */
    @Override
    public Mono<MfaConfig> enableSms(String userId, String tenantId, String phoneNumber) {
        MfaConfig config = MfaConfig.builder()
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.SMS)
                .status(MfaConfig.MfaStatus.ACTIVE)
                // 存储手机号
                .deviceInfo(phoneNumber)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        return mfaConfigRepository.save(config).doOnSuccess(saved -> {
            auditLogService.log(userId, tenantId, "启用短信多因素认证", "MFA_SMS_ENABLED", "SUCCESS", null);
            log.info("用户启用SMS MFA: userId={}, phone={}", userId, phoneNumber);
        });
    }

    /**
     * 启用邮箱MFA，创建邮箱类型的MFA配置并记录审计日志
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param email 接收验证码的邮箱地址
     * @return 新创建的邮箱MFA配置
     */
    @Override
    public Mono<MfaConfig> enableEmail(String userId, String tenantId, String email) {
        MfaConfig config = MfaConfig.builder()
                .userId(userId)
                .tenantId(tenantId)
                .type(MfaConfig.MfaType.EMAIL)
                .status(MfaConfig.MfaStatus.ACTIVE)
                // 存储邮箱
                .deviceInfo(email)
                .failedAttempts(0)
                .createdAt(LocalDateTime.now())
                .build();

        return mfaConfigRepository.save(config).doOnSuccess(saved -> {
            auditLogService.log(userId, tenantId, "启用邮箱多因素认证", "MFA_EMAIL_ENABLED", "SUCCESS", null);
            log.info("用户启用EMAIL MFA: userId={}, email={}", userId, email);
        });
    }

    /**
     * 禁用指定类型的MFA，将配置状态置为禁用并记录审计日志
     *
     * @param userId 用户ID
     * @param type 要禁用的MFA类型
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "mfa-config", key = "#userId")
    public Mono<Void> disableMfa(String userId, MfaConfig.MfaType type) {
        return mfaConfigRepository
                .findByUserIdAndType(userId, type)
                .flatMap(config -> {
                    config.setStatus(MfaConfig.MfaStatus.DISABLED);
                    config.setUpdatedAt(LocalDateTime.now());
                    return mfaConfigRepository.save(config);
                })
                .doOnSuccess(config -> {
                    auditLogService.log(
                            userId, config.getTenantId(), "禁用多因素认证: " + type, "MFA_DISABLED", "SUCCESS", null);
                    log.info("用户禁用MFA: userId={}, type={}", userId, type);
                })
                .then();
    }

    @Override
    public Mono<Boolean> verifyMfaCode(String userId, String code, MfaConfig.MfaType type) {
        return mfaConfigRepository
                .findByUserIdAndType(userId, type)
                .flatMap(config -> {
                    if (!config.isActive()) {
                        return Mono.just(false);
                    }

                    if (config.isLocked()) {
                        return Mono.error(new IllegalStateException("MFA已锁定，请联系管理员"));
                    }

                    return switch (type) {
                        case TOTP -> verifyTotpCode(config, code);
                        case SMS, EMAIL -> verifyOtpCode(config, code);
                        case BACKUP_CODE -> verifyBackupCodeInternal(config, code);
                    };
                })
                .defaultIfEmpty(false);
    }

    /**
     * 验证TOTP代码
     */
    private Mono<Boolean> verifyTotpCode(MfaConfig config, String code) {
        return encryptionService.decrypt(config.getSecret()).map(secret -> {
            CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

            boolean isValid = verifier.isValidCode(secret, code);

            if (isValid) {
                config.updateLastVerified();
            } else {
                config.incrementFailedAttempts();
            }

            config.setUpdatedAt(LocalDateTime.now());
            mfaConfigRepository.save(config).subscribe();

            return isValid;
        });
    }

    /**
     * 验证OTP代码（SMS/Email）
     */
    private Mono<Boolean> verifyOtpCode(MfaConfig config, String code) {
        // 从Redis或缓存中获取验证码进行比对
        // 这里简化实现，实际应该从缓存中读取
        // TODO: 实现OTP验证逻辑
        return Mono.just(false);
    }

    /**
     * 验证备用验证码
     */
    private Mono<Boolean> verifyBackupCodeInternal(MfaConfig config, String code) {
        return encryptionService.decrypt(config.getBackupCodes()).map(backupCodesStr -> {
            List<String> codes = new ArrayList<>(Arrays.asList(backupCodesStr.split(",")));

            if (codes.contains(code)) {
                // 使用后移除该验证码
                codes.remove(code);
                String updatedCodes = String.join(",", codes);

                encryptionService
                        .encrypt(updatedCodes)
                        .doOnNext(encrypted -> {
                            config.setBackupCodes(encrypted);
                            config.updateLastVerified();
                            config.setUpdatedAt(LocalDateTime.now());
                            mfaConfigRepository.save(config).subscribe();
                        })
                        .subscribe();

                return true;
            }

            config.incrementFailedAttempts();
            config.setUpdatedAt(LocalDateTime.now());
            mfaConfigRepository.save(config).subscribe();

            return false;
        });
    }

    /**
     * 发送MFA验证码（SMS/Email），生成6位数字验证码并按类型下发
     *
     * @param userId 用户ID
     * @param type MFA类型
     * @return 完成信号
     */
    @Override
    public Mono<Void> sendMfaCode(String userId, MfaConfig.MfaType type) {
        return mfaConfigRepository.findByUserIdAndType(userId, type).flatMap(config -> {
            // 生成6位数字验证码
            String code = String.format("%06d", new SecureRandom().nextInt(999999));

            // 存储到Redis，5分钟有效期
            // TODO: 实现验证码发送逻辑（SMS/Email）

            log.info("发送MFA验证码: userId={}, type={}", userId, type);
            return Mono.empty();
        });
    }

    /**
     * 查询用户的全部MFA配置
     *
     * @param userId 用户ID
     * @return 该用户的MFA配置流
     */
    @Override
    @Cacheable(value = "mfa-config", key = "#userId")
    public Flux<MfaConfig> getUserMfaConfigs(String userId) {
        return mfaConfigRepository.findByUserId(userId);
    }

    /**
     * 检查用户是否启用了任一处于激活状态的MFA
     *
     * @param userId 用户ID
     * @return 存在激活状态的MFA配置时为true，否则为false
     */
    @Override
    public Mono<Boolean> isMfaEnabled(String userId) {
        return mfaConfigRepository
                .findByUserId(userId)
                .filter(MfaConfig::isActive)
                .hasElements();
    }

    /**
     * 重新生成TOTP备用验证码，覆盖原有备用码并记录审计日志
     *
     * @param userId 用户ID
     * @return 新的备用验证码结果
     */
    @Override
    public Mono<BackupCodesResult> regenerateBackupCodes(String userId) {
        return mfaConfigRepository
                .findByUserIdAndType(userId, MfaConfig.MfaType.TOTP)
                .flatMap(config -> {
                    String[] backupCodes = generateBackupCodes();
                    String backupCodesStr = String.join(",", backupCodes);

                    return encryptionService
                            .encrypt(backupCodesStr)
                            .flatMap(encrypted -> {
                                config.setBackupCodes(encrypted);
                                config.setUpdatedAt(LocalDateTime.now());
                                return mfaConfigRepository.save(config);
                            })
                            .map(saved -> {
                                auditLogService.log(
                                        userId,
                                        config.getTenantId(),
                                        "重新生成备用验证码",
                                        "MFA_BACKUP_CODES_REGENERATED",
                                        "SUCCESS",
                                        null);
                                return new BackupCodesResult(backupCodes);
                            });
                });
    }

    /**
     * 验证备用验证码，验证通过后该备用码将被作废
     *
     * @param userId 用户ID
     * @param code 备用验证码
     * @return 验证通过返回true，验证失败或配置不存在返回false
     */
    @Override
    public Mono<Boolean> verifyBackupCode(String userId, String code) {
        return verifyMfaCode(userId, code, MfaConfig.MfaType.BACKUP_CODE);
    }

    /**
     * 解锁被锁定的MFA，重置失败尝试次数并记录审计日志
     *
     * @param userId 被解锁的用户ID
     * @param type 要解锁的MFA类型
     * @param operator 执行解锁操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "mfa-config", key = "#userId")
    public Mono<Void> unlockMfa(String userId, MfaConfig.MfaType type, String operator) {
        return mfaConfigRepository
                .findByUserIdAndType(userId, type)
                .flatMap(config -> {
                    config.resetFailedAttempts();
                    config.setUpdatedAt(LocalDateTime.now());
                    return mfaConfigRepository.save(config);
                })
                .doOnSuccess(config -> {
                    auditLogService.log(
                            operator, config.getTenantId(), "解锁MFA账户: " + userId, "MFA_UNLOCKED", "SUCCESS", null);
                    log.info("管理员解锁MFA账户: userId={}, operator={}", userId, operator);
                })
                .then();
    }

    /**
     * 生成备用验证码
     */
    private String[] generateBackupCodes() {
        SecureRandom random = new SecureRandom();
        return IntStream.range(0, BACKUP_CODES_COUNT)
                .mapToObj(i -> {
                    StringBuilder code = new StringBuilder();
                    for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                        code.append(random.nextInt(10));
                    }
                    return code.toString();
                })
                .toArray(String[]::new);
    }
}
