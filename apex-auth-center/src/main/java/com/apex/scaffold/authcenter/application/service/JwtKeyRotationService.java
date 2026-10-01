package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.domain.model.entity.JwtKeyEntity;
import com.apex.scaffold.authcenter.domain.repository.JwtKeyRepository;
import com.apex.scaffold.authcenter.domain.service.TokenEncryptionService;
import com.apex.scaffold.common.audit.SecurityAuditService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * JWT密钥轮换服务
 *
 * <p>负责JWT签名密钥的生成、轮换、废弃和吊销管理。
 * 通过密钥版本控制实现平滑的密钥过渡，确保在密钥切换期间不影响现有Token的验证。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>生成新的JWT签名密钥（HMAC-SHA256，256位随机密钥）</li>
 *   <li>执行密钥轮换：将当前ACTIVE密钥设为ROTATING，启用新密钥</li>
 *   <li>管理密钥生命周期：PENDING → ACTIVE → ROTATING → DEPRECATED → REVOKED</li>
 *   <li>定期检查即将过期的密钥（30天内），发送告警</li>
 *   <li>自动清理已吊销且超过保留期（365天）的密钥</li>
 * </ul>
 *
 * <h2>轮换策略</h2>
 * <ul>
 *   <li><b>定期轮换</b>：建议每90天自动轮换一次密钥</li>
 *   <li><b>平滑过渡</b>：旧密钥保持ROTATING状态24小时，用于验证已签发的Token</li>
 *   <li><b>零停机</b>：Token验证同时支持ACTIVE和ROTATING状态的密钥</li>
 *   <li><b>紧急轮换</b>：发生安全事件时，立即轮换并吊销旧密钥</li>
 * </ul>
 *
 * <h2>安全特性</h2>
 * <ul>
 *   <li>密钥使用AES-256加密后存储，加密密钥从环境变量KEY_ENCRYPTION_KEY获取</li>
 *   <li>使用SecureRandom生成高强度随机密钥（256位）</li>
 *   <li>所有密钥操作记录审计日志（生成、轮换、废弃、吊销）</li>
 *   <li>支持紧急吊销场景：密钥泄露、安全事件</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtKeyRotationService {

    private final JwtKeyRepository jwtKeyRepository;
    private final TokenEncryptionService encryptionService;
    private final SecurityAuditService auditService;

    private static final int KEY_SIZE_BITS = 256;
    private static final int KEY_SIZE_BYTES = KEY_SIZE_BITS / 8;
    private static final int DEFAULT_KEY_LIFETIME_DAYS = 90;
    private static final int ROTATING_GRACE_PERIOD_HOURS = 24;

    /**
     * 生成新的JWT密钥
     *
     * <p>生成256位高强度随机密钥，加密后存储到数据库。
     * 新密钥初始状态为PENDING，需要手动或自动激活。
     *
     * @param rotationReason 轮换原因
     * @param operatorUserId 操作人ID
     * @param operatorUsername 操作人用户名
     * @return 新生成的密钥实体
     */
    public Mono<JwtKeyEntity> generateNewKey(String rotationReason, Long operatorUserId, String operatorUsername) {
        log.info("开始生成新的JWT密钥, reason={}, operator={}", rotationReason, operatorUsername);

        return Mono.fromCallable(() -> {
                    // 使用SecureRandom生成256位随机密钥
                    SecureRandom secureRandom = new SecureRandom();
                    byte[] keyBytes = new byte[KEY_SIZE_BYTES];
                    secureRandom.nextBytes(keyBytes);
                    String rawSecret = Base64.getEncoder().encodeToString(keyBytes);

                    // 加密密钥（使用存储格式：iv:encryptedData）
                    String encryptedSecret = encryptionService.encryptForStorage(rawSecret);

                    // 生成密钥版本号（格式：v20260925_001）
                    String keyVersion = generateKeyVersion();

                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime expiresAt = now.plusDays(DEFAULT_KEY_LIFETIME_DAYS);

                    return JwtKeyEntity.builder()
                            .keyVersion(keyVersion)
                            .encryptedSecret(encryptedSecret)
                            .algorithm("HS256")
                            .status("PENDING")
                            .expiresAt(expiresAt)
                            .rotationReason(rotationReason)
                            .operatorUserId(operatorUserId)
                            .operatorUsername(operatorUsername)
                            .createdAt(now)
                            .updatedAt(now)
                            .remark("自动生成的JWT签名密钥")
                            .build();
                })
                .flatMap(jwtKeyRepository::save)
                .doOnSuccess(key -> {
                    log.info("JWT密钥生成成功: version={}", key.getKeyVersion());
                    auditService.logKeyGeneration(key.getKeyVersion(), rotationReason, operatorUsername);
                })
                .doOnError(e -> log.error("JWT密钥生成失败", e));
    }

    /**
     * 执行密钥轮换
     *
     * <p>核心轮换流程：
     * <ol>
     *   <li>生成新密钥（状态：PENDING）</li>
     *   <li>将当前ACTIVE密钥设置为ROTATING（保留24小时用于验证旧Token）</li>
     *   <li>激活新密钥（状态：ACTIVE），后续所有新Token使用新密钥签发</li>
     * </ol>
     *
     * @param rotationReason 轮换原因（SCHEDULED/SECURITY_INCIDENT/MANUAL/LEAKED）
     * @param operatorUserId 操作人ID
     * @param operatorUsername 操作人用户名
     * @return 新激活的密钥
     */
    public Mono<JwtKeyEntity> rotateKey(String rotationReason, Long operatorUserId, String operatorUsername) {
        log.info("开始JWT密钥轮换, reason={}, operator={}", rotationReason, operatorUsername);

        return generateNewKey(rotationReason, operatorUserId, operatorUsername)
                .flatMap(newKey -> jwtKeyRepository
                        .markAllActiveAsRotating()
                        .doOnSuccess(count -> log.info("已将{}个ACTIVE密钥标记为ROTATING状态", count))
                        .thenReturn(newKey))
                .flatMap(this::activateKey)
                .doOnSuccess(key -> {
                    log.info("JWT密钥轮换成功: 新密钥版本={}", key.getKeyVersion());
                    auditService.logKeyRotation(null, key.getKeyVersion(), rotationReason, operatorUsername);
                })
                .doOnError(e -> {
                    log.error("JWT密钥轮换失败: reason={}, operator={}", rotationReason, operatorUsername, e);
                    auditService.logKeyRotationFailure(rotationReason, operatorUsername, e.getMessage());
                });
    }

    /**
     * 激活待启用的密钥
     *
     * @param key 待激活的密钥
     * @return 激活后的密钥
     */
    private Mono<JwtKeyEntity> activateKey(JwtKeyEntity key) {
        LocalDateTime now = LocalDateTime.now();
        key.setStatus("ACTIVE");
        key.setActivatedAt(now);
        key.setUpdatedAt(now);

        return jwtKeyRepository
                .save(key)
                .doOnSuccess(activated -> log.info("密钥已激活: version={}", activated.getKeyVersion()));
    }

    /**
     * 紧急吊销密钥
     *
     * <p>用于密钥泄露等安全事件，立即吊销指定密钥并触发轮换。
     * 吊销后的密钥不再用于任何Token的验证。
     *
     * @param keyVersion 要吊销的密钥版本
     * @param reason 吊销原因
     * @param operatorUserId 操作人ID
     * @param operatorUsername 操作人用户名
     * @return 吊销后的密钥
     */
    public Mono<JwtKeyEntity> revokeKey(
            String keyVersion, String reason, Long operatorUserId, String operatorUsername) {
        log.warn("紧急吊销JWT密钥: version={}, reason={}, operator={}", keyVersion, reason, operatorUsername);

        return jwtKeyRepository
                .findByKeyVersion(keyVersion)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "密钥版本不存在: " + keyVersion)))
                .flatMap(key -> {
                    LocalDateTime now = LocalDateTime.now();
                    key.setStatus("REVOKED");
                    key.setRevokedAt(now);
                    key.setUpdatedAt(now);
                    key.setRemark("吊销原因: " + reason);

                    return jwtKeyRepository.save(key);
                })
                .doOnSuccess(key -> {
                    log.warn("密钥已吊销: version={}", key.getKeyVersion());
                    auditService.logKeyRevocation(keyVersion, reason, operatorUsername);
                })
                .flatMap(revokedKey -> {
                    String activeStatus = "ACTIVE";
                    if (activeStatus.equals(revokedKey.getStatus())) {
                        log.info("检测到ACTIVE密钥被吊销，自动触发密钥轮换");
                        return rotateKey("SECURITY_INCIDENT", operatorUserId, operatorUsername)
                                .thenReturn(revokedKey);
                    }
                    return Mono.just(revokedKey);
                });
    }

    /**
     * 废弃ROTATING状态的过期密钥
     *
     * <p>定时任务调用，将ROTATING状态超过24小时的密钥设置为DEPRECATED。
     * DEPRECATED密钥保留用于审计和历史Token验证（只读）。
     */
    public Mono<Integer> deprecateExpiredRotatingKeys() {
        log.info("开始废弃过期的ROTATING密钥");

        return jwtKeyRepository
                .findUsableKeys()
                .filter(key -> "ROTATING".equals(key.getStatus()))
                .filter(key -> {
                    LocalDateTime cutoffTime = LocalDateTime.now().minusHours(ROTATING_GRACE_PERIOD_HOURS);
                    return key.getActivatedAt() != null && key.getActivatedAt().isBefore(cutoffTime);
                })
                .flatMap(key -> {
                    LocalDateTime now = LocalDateTime.now();
                    key.setStatus("DEPRECATED");
                    key.setDeprecatedAt(now);
                    key.setUpdatedAt(now);
                    return jwtKeyRepository.save(key);
                })
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("已废弃{}个过期的ROTATING密钥", count));
    }

    /**
     * 查询即将过期的密钥（30天内）
     *
     * @return 即将过期的密钥列表
     */
    public Flux<JwtKeyEntity> findExpiringSoonKeys() {
        return jwtKeyRepository.findExpiringSoonKeys();
    }

    /**
     * 获取当前活跃的密钥（用于签发新Token）
     *
     * @return 当前活跃的密钥，解密后返回明文密钥
     */
    public Mono<String> getActiveKeySecret() {
        return jwtKeyRepository
                .findActiveKey()
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.ERROR, "未找到活跃的JWT密钥，请执行密钥初始化")))
                .map(key -> encryptionService.decrypt(key.getEncryptedSecret()));
    }

    /**
     * 获取所有可用于验证的密钥（ACTIVE + ROTATING）
     *
     * @return 可用密钥的明文列表（用于多版本Token验证）
     */
    public Flux<String> getUsableKeySecrets() {
        return jwtKeyRepository.findUsableKeys().map(key -> encryptionService.decrypt(key.getEncryptedSecret()));
    }

    /**
     * 清理已吊销且超过保留期的密钥
     *
     * <p>定时任务调用，删除已吊销超过365天的密钥记录。
     *
     * @return 删除的密钥数量
     */
    public Mono<Integer> cleanupRevokedKeys() {
        int retentionDays = 365;
        log.info("开始清理已吊销超过{}天的密钥", retentionDays);

        return jwtKeyRepository
                .deleteRevokedKeysOlderThan(retentionDays)
                .doOnSuccess(count -> log.info("已清理{}个过期的已吊销密钥", count));
    }

    /**
     * 初始化JWT密钥系统
     *
     * <p>系统首次启动时调用，检查是否存在活跃密钥，
     * 如果不存在则自动生成并激活一个新密钥。
     */
    public Mono<JwtKeyEntity> initializeKeyIfNeeded() {
        return jwtKeyRepository
                .findActiveKey()
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("检测到没有活跃的JWT密钥，自动初始化");
                    return generateNewKey("SYSTEM_INITIALIZATION", 0L, "SYSTEM").flatMap(this::activateKey);
                }))
                .doOnSuccess(key -> log.info("JWT密钥系统已就绪: version={}", key.getKeyVersion()));
    }

    /**
     * 生成密钥版本号
     *
     * <p>格式：v20260925_001（日期_序号）
     */
    private String generateKeyVersion() {
        String datePrefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String sequence = String.format("%03d", System.currentTimeMillis() % 1000);
        return "v" + datePrefix + "_" + sequence;
    }
}
