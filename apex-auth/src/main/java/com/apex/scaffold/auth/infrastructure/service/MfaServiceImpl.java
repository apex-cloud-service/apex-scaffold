package com.apex.scaffold.auth.infrastructure.service;

import com.apex.scaffold.auth.domain.model.MfaConfig;
import com.apex.scaffold.auth.domain.service.MfaService;
import com.apex.scaffold.auth.infrastructure.repository.MfaConfigRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.security.EncryptionService;
import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.infrastructure.mail.MailService;
import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多因素认证服务实现
 *
 * <p>短信通道存在两个 {@link SmsCodeService} 实现（创蓝真实发送与默认适配器），
 * 必须显式限定为 {@code defaultSmsCodeService}，否则容器装配报“expected single matching bean”。</p>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
public class MfaServiceImpl implements MfaService {

    private final MfaConfigRepository mfaConfigRepository;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final SmsCodeService smsCodeService;
    private final MailService mailService;

    /**
     * 构造多因素认证服务，显式绑定默认短信验证码实现。
     *
     * @param mfaConfigRepository MFA 配置仓储
     * @param encryptionService 加密服务
     * @param auditLogService 审计日志服务
     * @param redisTemplate Redis 模板
     * @param smsCodeService 默认短信验证码服务（转发到创蓝真实发送）
     * @param mailService 邮件服务
     */
    public MfaServiceImpl(
            MfaConfigRepository mfaConfigRepository,
            EncryptionService encryptionService,
            AuditLogService auditLogService,
            ReactiveStringRedisTemplate redisTemplate,
            @org.springframework.beans.factory.annotation.Qualifier("defaultSmsCodeService")
                    SmsCodeService smsCodeService,
            MailService mailService) {
        this.mfaConfigRepository = mfaConfigRepository;
        this.encryptionService = encryptionService;
        this.auditLogService = auditLogService;
        this.redisTemplate = redisTemplate;
        this.smsCodeService = smsCodeService;
        this.mailService = mailService;
    }

    private static final String ISSUER = "Apex Scaffold";
    private static final String OTP_KEY_PREFIX = "mfa:otp:";
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final RedisScript<Long> VERIFY_OTP_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if not value then return 0; end; "
                    + "if value == ARGV[1] then redis.call('DEL', KEYS[1]); return 1; end; "
                    + "return -1;",
            Long.class);
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
    public Mono<MfaActivationResult> verifyAndActivateTotp(String userId, String code, String tenantId) {
        return mfaConfigRepository
                .findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId)
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
     * @param tenantId 租户ID
     */
    @Override
    @CacheEvict(value = "mfa-config", key = "#tenantId + ':' + #userId")
    public Mono<Void> disableMfa(String userId, MfaConfig.MfaType type, String tenantId) {
        return mfaConfigRepository
                .findByUserIdAndTypeAndTenantId(userId, type, tenantId)
                .flatMap(config -> {
                    config.setStatus(MfaConfig.MfaStatus.DISABLED);
                    config.setUpdatedAt(LocalDateTime.now());
                    return mfaConfigRepository.save(config);
                })
                .doOnNext(config -> {
                    auditLogService.log(
                            userId, config.getTenantId(), "禁用多因素认证: " + type, "MFA_DISABLED", "SUCCESS", null);
                    log.info("用户禁用MFA: userId={}, type={}", userId, type);
                })
                .then();
    }

    @Override
    public Mono<Boolean> verifyMfaCode(String userId, String code, MfaConfig.MfaType type, String tenantId) {
        return mfaConfigRepository
                .findByUserIdAndTypeAndTenantId(userId, type, tenantId)
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
        return encryptionService.decrypt(config.getSecret()).flatMap(secret -> {
            CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
            boolean isValid = verifier.isValidCode(secret, code);
            updateVerificationState(config, isValid);
            return mfaConfigRepository.save(config).thenReturn(isValid);
        });
    }

    /**
     * 验证OTP代码（SMS/Email）
     */
    private Mono<Boolean> verifyOtpCode(MfaConfig config, String code) {
        if (config.getDeviceInfo() == null || config.getDeviceInfo().isBlank()) {
            return Mono.just(false);
        }
        Mono<Boolean> validation = config.getType() == MfaConfig.MfaType.SMS
                ? smsCodeService.validate(config.getDeviceInfo(), code)
                : redisTemplate
                        .execute(VERIFY_OTP_SCRIPT, List.of(otpKey(config)), code)
                        .next()
                        .defaultIfEmpty(0L)
                        .map(result -> result == 1L);
        return validation.flatMap(valid -> {
            updateVerificationState(config, valid);
            return mfaConfigRepository.save(config).thenReturn(valid);
        });
    }

    private void updateVerificationState(MfaConfig config, boolean valid) {
        if (valid) {
            config.updateLastVerified();
        } else {
            config.incrementFailedAttempts();
        }
        config.setUpdatedAt(LocalDateTime.now());
    }

    private String otpKey(MfaConfig config) {
        return OTP_KEY_PREFIX + config.getTenantId() + ":" + config.getUserId() + ":" + config.getType();
    }

    /**
     * 验证备用验证码
     */
    private Mono<Boolean> verifyBackupCodeInternal(MfaConfig config, String code) {
        return encryptionService.decrypt(config.getBackupCodes()).flatMap(backupCodesStr -> {
            List<String> codes = new ArrayList<>(Arrays.asList(backupCodesStr.split(",")));
            if (codes.contains(code)) {
                codes.remove(code);
                String updatedCodes = String.join(",", codes);
                return encryptionService.encrypt(updatedCodes).flatMap(encrypted -> {
                    config.setBackupCodes(encrypted);
                    config.updateLastVerified();
                    config.setUpdatedAt(LocalDateTime.now());
                    return mfaConfigRepository.save(config).thenReturn(true);
                });
            }

            config.incrementFailedAttempts();
            config.setUpdatedAt(LocalDateTime.now());
            return mfaConfigRepository.save(config).thenReturn(false);
        });
    }

    /**
     * 发送MFA验证码（SMS/Email），生成6位数字验证码并按类型下发
     *
     * @param userId 用户ID
     * @param type MFA类型
     * @param tenantId 租户ID
     */
    @Override
    public Mono<Void> sendMfaCode(String userId, MfaConfig.MfaType type, String tenantId) {
        return mfaConfigRepository
                .findByUserIdAndTypeAndTenantId(userId, type, tenantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("MFA配置不存在")))
                .flatMap(config -> {
                    if (!config.isActive()
                            || config.getDeviceInfo() == null
                            || config.getDeviceInfo().isBlank()) {
                        return Mono.error(new IllegalStateException("MFA配置不可用"));
                    }
                    if (type == MfaConfig.MfaType.SMS) {
                        return smsCodeService.sendAndStore(config.getDeviceInfo());
                    }
                    String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
                    return redisTemplate
                            .opsForValue()
                            .set(otpKey(config), code, OTP_TTL)
                            .flatMap(stored -> Boolean.TRUE.equals(stored)
                                    ? Mono.fromRunnable(() -> mailService.sendSimple(
                                                    config.getDeviceInfo(), "MFA验证码", "您的验证码是：" + code))
                                            .subscribeOn(BlockingIoSchedulers.remote())
                                            .then()
                                    : Mono.error(new IllegalStateException("验证码安全存储失败")));
                })
                .doOnSuccess(ignored -> log.info("MFA验证码发送完成: userId={}, type={}", userId, type));
    }

    /**
     * 查询用户的全部MFA配置
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 该用户的MFA配置流
     */
    @Override
    @Cacheable(value = "mfa-config", key = "#tenantId + ':' + #userId")
    public Flux<MfaConfig> getUserMfaConfigs(String userId, String tenantId) {
        return mfaConfigRepository.findByUserIdAndTenantId(userId, tenantId);
    }

    /**
     * 检查用户是否启用了任一处于激活状态的MFA
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 存在激活状态的MFA配置时为true，否则为false
     */
    @Override
    public Mono<Boolean> isMfaEnabled(String userId, String tenantId) {
        return mfaConfigRepository
                .findByUserIdAndTenantId(userId, tenantId)
                .filter(MfaConfig::isActive)
                .hasElements();
    }

    /**
     * 重新生成TOTP备用验证码，覆盖原有备用码并记录审计日志
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 新的备用验证码结果
     */
    @Override
    public Mono<BackupCodesResult> regenerateBackupCodes(String userId, String tenantId) {
        return mfaConfigRepository
                .findByUserIdAndTypeAndTenantId(userId, MfaConfig.MfaType.TOTP, tenantId)
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
     * @param tenantId 租户ID
     * @return 验证通过返回true，验证失败或配置不存在返回false
     */
    @Override
    public Mono<Boolean> verifyBackupCode(String userId, String code, String tenantId) {
        return verifyMfaCode(userId, code, MfaConfig.MfaType.BACKUP_CODE, tenantId);
    }

    /**
     * 解锁被锁定的MFA，重置失败尝试次数并记录审计日志
     *
     * @param userId 被解锁的用户ID
     * @param type 要解锁的MFA类型
     * @param operator 执行解锁操作的运维人员标识
     * @param tenantId 租户ID
     */
    @Override
    @CacheEvict(value = "mfa-config", key = "#tenantId + ':' + #userId")
    public Mono<Void> unlockMfa(String userId, MfaConfig.MfaType type, String operator, String tenantId) {
        return mfaConfigRepository
                .findByUserIdAndTypeAndTenantId(userId, type, tenantId)
                .flatMap(config -> {
                    config.resetFailedAttempts();
                    config.setUpdatedAt(LocalDateTime.now());
                    return mfaConfigRepository.save(config);
                })
                .doOnNext(config -> {
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
