package com.apex.scaffold.authcenter.infrastructure.service;

import com.apex.scaffold.authcenter.domain.model.BiometricAuth;
import com.apex.scaffold.authcenter.domain.service.BiometricAuthService;
import com.apex.scaffold.authcenter.infrastructure.repository.BiometricAuthRepository;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * 生物识别认证服务实现
 * 
 * 支持：
 * 1. 指纹识别
 * 2. 人脸识别
 * 3. WebAuthn/FIDO2（硬件安全密钥）
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricAuthServiceImpl implements BiometricAuthService {

    private final BiometricAuthRepository biometricAuthRepository;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;
    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String CHALLENGE_PREFIX = "biometric:challenge:";
    private static final long CHALLENGE_TIMEOUT = 60000L; // 60秒

    @Override
    public Mono<BiometricAuth> registerBiometric(String userId, String tenantId, 
                                                  BiometricAuth.BiometricType type,
                                                  String deviceId, String deviceName, 
                                                  String publicKey) {
        // 生成模板ID
        String templateId = UUID.randomUUID().toString();
        
        return encryptionService.encrypt(templateId)
            .flatMap(encryptedTemplateId -> {
                BiometricAuth auth = BiometricAuth.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .type(type)
                    .templateId(encryptedTemplateId)
                    .deviceId(deviceId)
                    .deviceName(deviceName)
                    .publicKey(publicKey)
                    .status(BiometricAuth.AuthStatus.ACTIVE)
                    .failedAttempts(0)
                    .createdAt(LocalDateTime.now())
                    .build();
                
                return biometricAuthRepository.save(auth);
            })
            .doOnSuccess(saved -> {
                auditLogService.log("BIOMETRIC_REGISTERED", userId, tenantId,
                    "注册生物识别认证: " + type + ", 设备: " + deviceName, saved.getId().toString(), null)
                    .subscribe();
                log.info("用户注册生物识别: userId={}, type={}, device={}", userId, type, deviceName);
            });
    }

    @Override
    public Mono<Boolean> verifyBiometric(String userId, BiometricAuth.BiometricType type,
                                         String challenge, String signature) {
        return biometricAuthRepository.findByUserIdAndType(userId, type)
            .flatMap(auth -> {
                if (!auth.isActive()) {
                    return Mono.just(false);
                }
                
                if (auth.isLocked()) {
                    return Mono.error(new IllegalStateException("生物识别认证已锁定"));
                }
                
                // 验证挑战是否存在且未过期
                return verifyChallenge(userId, challenge)
                    .flatMap(challengeValid -> {
                        if (!challengeValid) {
                            return Mono.just(false);
                        }
                        
                        // 验证签名
                        return verifySignature(auth.getPublicKey(), challenge, signature)
                            .flatMap(signatureValid -> {
                                if (signatureValid) {
                                    auth.updateLastAuth();
                                } else {
                                    auth.incrementFailedAttempts();
                                }
                                
                                auth.setUpdatedAt(LocalDateTime.now());
                                return biometricAuthRepository.save(auth)
                                    .thenReturn(signatureValid);
                            });
                    });
            })
            .defaultIfEmpty(false);
    }

    /**
     * 启用生物识别认证，将指定记录状态置为激活并记录审计日志
     *
     * @param id 生物识别记录ID
     * @param operator 执行操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "biometric-auth", key = "#id")
    public Mono<Void> enableBiometric(Long id, String operator) {
        return biometricAuthRepository.findById(id)
            .flatMap(auth -> {
                auth.setStatus(BiometricAuth.AuthStatus.ACTIVE);
                auth.setUpdatedAt(LocalDateTime.now());
                return biometricAuthRepository.save(auth);
            })
            .doOnSuccess(auth -> {
                auditLogService.log("BIOMETRIC_ENABLED", operator, auth.getTenantId(),
                    "启用生物识别: userId=" + auth.getUserId() + ", type=" + auth.getType(), 
                    auth.getId().toString(), null)
                    .subscribe();
                log.info("启用生物识别: id={}, operator={}", id, operator);
            })
            .then();
    }

    /**
     * 禁用生物识别认证，将指定记录状态置为禁用并记录审计日志
     *
     * @param id 生物识别记录ID
     * @param operator 执行操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "biometric-auth", key = "#id")
    public Mono<Void> disableBiometric(Long id, String operator) {
        return biometricAuthRepository.findById(id)
            .flatMap(auth -> {
                auth.setStatus(BiometricAuth.AuthStatus.DISABLED);
                auth.setUpdatedAt(LocalDateTime.now());
                return biometricAuthRepository.save(auth);
            })
            .doOnSuccess(auth -> {
                auditLogService.log("BIOMETRIC_DISABLED", operator, auth.getTenantId(),
                    "禁用生物识别: userId=" + auth.getUserId() + ", type=" + auth.getType(), 
                    auth.getId().toString(), null)
                    .subscribe();
                log.info("禁用生物识别: id={}, operator={}", id, operator);
            })
            .then();
    }

    /**
     * 删除生物识别认证记录并记录审计日志
     *
     * @param id 生物识别记录ID
     * @param operator 执行操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "biometric-auth", key = "#id")
    public Mono<Void> deleteBiometric(Long id, String operator) {
        return biometricAuthRepository.findById(id)
            .flatMap(auth -> {
                auditLogService.log("BIOMETRIC_DELETED", operator, auth.getTenantId(),
                    "删除生物识别: userId=" + auth.getUserId() + ", type=" + auth.getType(), 
                    auth.getId().toString(), null)
                    .subscribe();
                return biometricAuthRepository.deleteById(id);
            })
            .doOnSuccess(v -> 
                log.info("删除生物识别: id={}, operator={}", id, operator)
            );
    }

    /**
     * 查询用户的全部生物识别配置
     *
     * @param userId 用户ID
     * @return 该用户的生物识别配置流
     */
    @Override
    @Cacheable(value = "biometric-auth", key = "#userId")
    public Flux<BiometricAuth> getUserBiometrics(String userId) {
        return biometricAuthRepository.findByUserId(userId);
    }

    /**
     * 检查用户是否拥有处于激活状态的生物识别配置
     *
     * @param userId 用户ID
     * @return 存在激活状态的配置时为true，否则为false
     */
    @Override
    public Mono<Boolean> isBiometricEnabled(String userId) {
        return biometricAuthRepository.findByUserId(userId)
            .filter(BiometricAuth::isActive)
            .hasElements();
    }

    /**
     * 解锁被锁定的生物识别认证，重置失败尝试次数并记录审计日志
     *
     * @param id 生物识别记录ID
     * @param operator 执行操作的运维人员标识
     * @return 完成信号
     */
    @Override
    @CacheEvict(value = "biometric-auth", key = "#id")
    public Mono<Void> unlockBiometric(Long id, String operator) {
        return biometricAuthRepository.findById(id)
            .flatMap(auth -> {
                auth.resetFailedAttempts();
                auth.setUpdatedAt(LocalDateTime.now());
                return biometricAuthRepository.save(auth);
            })
            .doOnSuccess(auth -> {
                auditLogService.log("BIOMETRIC_UNLOCKED", operator, auth.getTenantId(),
                    "解锁生物识别: userId=" + auth.getUserId() + ", type=" + auth.getType(), 
                    auth.getId().toString(), null)
                    .subscribe();
                log.info("解锁生物识别: id={}, operator={}", id, operator);
            })
            .then();
    }

    /**
     * 生成WebAuthn注册挑战，写入Redis并设置60秒有效期
     *
     * @param userId 用户ID
     * @return 包含挑战串、用户ID和有效期的注册挑战对象
     */
    @Override
    public Mono<RegistrationChallenge> createRegistrationChallenge(String userId) {
        String challenge = generateChallenge();
        String key = CHALLENGE_PREFIX + userId + ":registration";
        
        return redisTemplate.opsForValue()
            .set(key, challenge, Duration.ofMillis(CHALLENGE_TIMEOUT))
            .thenReturn(new RegistrationChallenge(challenge, userId, CHALLENGE_TIMEOUT));
    }

    /**
     * 生成WebAuthn认证挑战，写入Redis并设置60秒有效期
     *
     * @param userId 用户ID
     * @return 包含挑战串和有效期的认证挑战对象
     */
    @Override
    public Mono<AuthenticationChallenge> createAuthenticationChallenge(String userId) {
        String challenge = generateChallenge();
        String key = CHALLENGE_PREFIX + userId + ":authentication";
        
        return redisTemplate.opsForValue()
            .set(key, challenge, Duration.ofMillis(CHALLENGE_TIMEOUT))
            .thenReturn(new AuthenticationChallenge(challenge, CHALLENGE_TIMEOUT));
    }

    /**
     * 生成挑战字符串
     */
    private String generateChallenge() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 验证挑战是否有效
     */
    private Mono<Boolean> verifyChallenge(String userId, String challenge) {
        String key = CHALLENGE_PREFIX + userId + ":authentication";
        
        return redisTemplate.opsForValue()
            .get(key)
            .map(storedChallenge -> storedChallenge.equals(challenge))
            .defaultIfEmpty(false)
            .doOnNext(valid -> {
                if (valid) {
                    // 验证后删除挑战
                    redisTemplate.delete(key).subscribe();
                }
            });
    }

    /**
     * 验证签名
     */
    private Mono<Boolean> verifySignature(String publicKeyStr, String challenge, String signatureStr) {
        return Mono.fromCallable(() -> {
            try {
                // 解码公钥
                byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyStr);
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PublicKey publicKey = keyFactory.generatePublic(keySpec);
                
                // 验证签名
                Signature signature = Signature.getInstance("SHA256withRSA");
                signature.initVerify(publicKey);
                signature.update(challenge.getBytes());
                
                byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
                return signature.verify(signatureBytes);
            } catch (Exception e) {
                log.error("验证签名失败", e);
                return false;
            }
        });
    }
}
