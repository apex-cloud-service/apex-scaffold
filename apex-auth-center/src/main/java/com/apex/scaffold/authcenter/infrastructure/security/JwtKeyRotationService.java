package com.apex.scaffold.authcenter.infrastructure.security;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import io.jsonwebtoken.security.Keys;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * JWT密钥轮换服务
 *
 * <p>实现JWT签名密钥的定期轮换，提高系统安全性。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>自动定期轮换JWT签名密钥（默认每30天）</li>
 *   <li>保留历史密钥用于验证旧Token（默认保留90天）</li>
 *   <li>支持手动触发密钥轮换</li>
 *   <li>线程安全的密钥访问</li>
 * </ul>
 *
 * <h2>安全保障</h2>
 * <ul>
 *   <li>使用ReadWriteLock保证并发安全</li>
 *   <li>历史密钥只用于验证，不用于签名</li>
 *   <li>过期密钥自动清理</li>
 *   <li>密钥轮换审计日志</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 2026-09-25
 */
@Slf4j
public class JwtKeyRotationService {

    /**
     * 当前活跃密钥（用于签名新Token）
     */
    private volatile KeyInfo currentKey;

    /**
     * 历史密钥映射（keyId -> KeyInfo），用于验证旧Token
     */
    private final Map<String, KeyInfo> historicalKeys = new ConcurrentHashMap<>();

    /**
     * 读写锁，保护密钥轮换操作
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 密钥有效期（天）
     */
    private static final int KEY_VALIDITY_DAYS = 30;

    /**
     * 历史密钥保留期（天）
     */
    private static final int HISTORICAL_KEY_RETENTION_DAYS = 90;

    /**
     * 构造服务并生成初始签名密钥
     */
    public JwtKeyRotationService() {
        // 初始化第一个密钥
        this.currentKey = generateNewKey();
        log.info("JWT密钥轮换服务初始化完成，当前密钥ID: {}", currentKey.getKeyId());
    }

    /**
     * 获取当前活跃密钥用于签名
     *
     * @return 当前密钥
     */
    public SecretKey getCurrentSigningKey() {
        lock.readLock().lock();
        try {
            return currentKey.getKey();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取当前密钥ID
     *
     * @return 密钥ID
     */
    public String getCurrentKeyId() {
        lock.readLock().lock();
        try {
            return currentKey.getKeyId();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 根据密钥ID获取验证密钥（支持历史密钥）
     *
     * @param keyId 密钥ID
     * @return 密钥，如果不存在则抛出异常
     */
    public SecretKey getVerificationKey(String keyId) {
        lock.readLock().lock();
        try {
            // 先检查当前密钥
            if (currentKey.getKeyId().equals(keyId)) {
                return currentKey.getKey();
            }

            // 再检查历史密钥
            KeyInfo historicalKey = historicalKeys.get(keyId);
            if (historicalKey != null) {
                return historicalKey.getKey();
            }

            throw new BusinessException(ResultCode.UNAUTHORIZED, "未知的密钥ID: " + keyId);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 定期轮换密钥（每天凌晨2点检查）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledKeyRotation() {
        lock.readLock().lock();
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime keyExpiry = currentKey.getCreatedAt().plusDays(KEY_VALIDITY_DAYS);

            if (now.isAfter(keyExpiry)) {
                log.info("检测到密钥已过期，开始自动轮换...");
                rotateKey();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 手动触发密钥轮换
     */
    public void rotateKey() {
        lock.writeLock().lock();
        try {
            log.info("开始JWT密钥轮换，旧密钥ID: {}", currentKey.getKeyId());

            // 将当前密钥移到历史记录
            historicalKeys.put(currentKey.getKeyId(), currentKey);

            // 生成新密钥
            KeyInfo newKey = generateNewKey();
            currentKey = newKey;

            log.info("JWT密钥轮换成功，新密钥ID: {}", newKey.getKeyId());

            // 清理过期的历史密钥
            cleanupExpiredKeys();

        } catch (Exception e) {
            log.error("JWT密钥轮换失败", e);
            throw new BusinessException(ResultCode.ERROR, "密钥轮换失败");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清理过期的历史密钥
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredKeys() {
        lock.writeLock().lock();
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusDays(HISTORICAL_KEY_RETENTION_DAYS);

            historicalKeys.entrySet().removeIf(entry -> {
                KeyInfo keyInfo = entry.getValue();
                if (keyInfo.getCreatedAt().isBefore(cutoffTime)) {
                    log.info("清理过期历史密钥: keyId={}, createdAt={}", entry.getKey(), keyInfo.getCreatedAt());
                    return true;
                }
                return false;
            });

            log.info("历史密钥清理完成，当前保留 {} 个历史密钥", historicalKeys.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 生成新的密钥
     *
     * @return 新密钥信息
     */
    private KeyInfo generateNewKey() {
        SecretKey key = Keys.secretKeyFor(io.jsonwebtoken.SignatureAlgorithm.HS256);
        String keyId = "key-" + java.util.UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        return new KeyInfo(keyId, key, createdAt);
    }

    /**
     * 获取密钥统计信息（用于监控）
     *
     * @return 统计信息
     */
    public KeyRotationStats getStats() {
        lock.readLock().lock();
        try {
            return KeyRotationStats.builder()
                    .currentKeyId(currentKey.getKeyId())
                    .currentKeyAge(java.time.Duration.between(currentKey.getCreatedAt(), LocalDateTime.now())
                            .toDays())
                    .historicalKeyCount(historicalKeys.size())
                    .nextRotationDays(KEY_VALIDITY_DAYS
                            - java.time.Duration.between(currentKey.getCreatedAt(), LocalDateTime.now())
                                    .toDays())
                    .build();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 密钥信息内部类
     */
    private static class KeyInfo {
        private final String keyId;
        private final SecretKey key;
        private final LocalDateTime createdAt;

        /**
         * 构造密钥信息。
         *
         * @param keyId 密钥ID
         * @param key 密钥对象
         * @param createdAt 创建时间
         */
        public KeyInfo(String keyId, SecretKey key, LocalDateTime createdAt) {
            this.keyId = keyId;
            this.key = key;
            this.createdAt = createdAt;
        }

        /**
         * 获取密钥ID
         *
         * @return 密钥ID
         */
        public String getKeyId() {
            return keyId;
        }

        /**
         * 获取密钥对象
         *
         * @return 用于签名和验证的密钥
         */
        public SecretKey getKey() {
            return key;
        }

        /**
         * 获取密钥创建时间
         *
         * @return 密钥的创建时间
         */
        public LocalDateTime getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * 密钥轮换统计信息
     */
    @lombok.Builder
    @lombok.Data
    public static class KeyRotationStats {
        private String currentKeyId;
        private long currentKeyAge;
        private int historicalKeyCount;
        private long nextRotationDays;
    }
}
