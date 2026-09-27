package com.apex.scaffold.authcenter.infrastructure.crypto;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT密钥轮换服务
 *
 * <p>支持JWT签名密钥的定期轮换，增强安全性。支持HMAC-SHA256和RSA两种签名算法。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>密钥版本管理：支持多版本密钥共存</li>
 *   <li>自动密钥轮换：按配置周期自动生成新密钥</li>
 *   <li>平滑过渡：旧Token在宽限期内仍可验证</li>
 *   <li>分布式同步：密钥通过Redis跨实例共享</li>
 * </ul>
 *
 * <h2>密钥轮换策略</h2>
 * <ul>
 *   <li>每30天自动轮换一次（可配置）</li>
 *   <li>保留最近3个版本的密钥（current + 2 previous）</li>
 *   <li>新Token使用当前版本签名，旧Token用历史版本验证</li>
 *   <li>超过宽限期的旧密钥自动清理</li>
 * </ul>
 *
 * @author Apex Security Team
 * @version 1.0.0
 * @since 2026-09-25
 */
@Slf4j
@Service
public class JwtKeyRotationService {

    private static final String REDIS_KEY_PREFIX = "jwt:keys:";
    private static final String CURRENT_KEY_VERSION = "jwt:current:version";
    private static final int KEY_RETENTION_COUNT = 3;
    
    private final ReactiveStringRedisTemplate redisTemplate;
    private final Map<String, JwtKey> keyCache = new ConcurrentHashMap<>();
    private final String algorithm;
    private final int keyRotationDays;
    
    private volatile String currentKeyVersion;

    public JwtKeyRotationService(
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${security.jwt.algorithm:HS256}") String algorithm,
            @Value("${security.jwt.key-rotation-days:30}") int keyRotationDays) {
        this.redisTemplate = redisTemplate;
        this.algorithm = algorithm;
        this.keyRotationDays = keyRotationDays;
    }

    /**
     * 初始化密钥管理
     */
    @PostConstruct
    public void init() {
        log.info("初始化JWT密钥轮换服务: algorithm={}, rotationDays={}", algorithm, keyRotationDays);
        
        loadCurrentKeyVersion()
            .switchIfEmpty(generateAndStoreNewKey())
            .flatMap(version -> {
                this.currentKeyVersion = version;
                return loadKeyFromRedis(version);
            })
            .doOnSuccess(key -> log.info("JWT密钥加载成功: version={}", currentKeyVersion))
            .doOnError(e -> log.error("JWT密钥初始化失败", e))
            .subscribe();
    }

    /**
     * 获取当前签名密钥（用于签发新Token）
     *
     * @return 当前版本的密钥
     */
    public Mono<JwtKey> getCurrentSigningKey() {
        if (currentKeyVersion == null) {
            return Mono.error(new IllegalStateException("JWT密钥未初始化"));
        }
        
        JwtKey cachedKey = keyCache.get(currentKeyVersion);
        if (cachedKey != null && !cachedKey.isExpired()) {
            return Mono.just(cachedKey);
        }
        
        return loadKeyFromRedis(currentKeyVersion)
            .doOnNext(key -> keyCache.put(currentKeyVersion, key));
    }

    /**
     * 获取验证密钥（用于验证Token，支持多版本）
     *
     * @param keyVersion 密钥版本（从Token header中获取）
     * @return 指定版本的密钥
     */
    public Mono<JwtKey> getVerifyingKey(String keyVersion) {
        if (keyVersion == null || keyVersion.isEmpty()) {
            return getCurrentSigningKey();
        }
        
        JwtKey cachedKey = keyCache.get(keyVersion);
        if (cachedKey != null && !cachedKey.isExpired()) {
            return Mono.just(cachedKey);
        }
        
        return loadKeyFromRedis(keyVersion)
            .doOnNext(key -> keyCache.put(keyVersion, key))
            .switchIfEmpty(Mono.error(
                new SecurityException("密钥版本不存在或已过期: " + keyVersion)));
    }

    /**
     * 手动轮换密钥
     *
     * @return 新密钥版本
     */
    public Mono<String> rotateKey() {
        log.info("开始手动轮换JWT密钥");
        
        return generateAndStoreNewKey()
            .doOnSuccess(version -> {
                this.currentKeyVersion = version;
                log.info("JWT密钥轮换成功: newVersion={}", version);
            })
            .flatMap(version -> cleanupOldKeys().thenReturn(version));
    }

    /**
     * 检查密钥是否需要轮换
     *
     * @return true-需要轮换，false-不需要
     */
    public Mono<Boolean> needsRotation() {
        return getCurrentSigningKey()
            .map(key -> {
                LocalDateTime expiryDate = key.getCreatedAt().plusDays(keyRotationDays);
                return LocalDateTime.now().isAfter(expiryDate);
            })
            .defaultIfEmpty(true);
    }

    /**
     * 生成并存储新密钥
     */
    private Mono<String> generateAndStoreNewKey() {
        try {
            String version = generateKeyVersion();
            JwtKey newKey = generateKey(version);
            
            return storeKeyToRedis(newKey)
                .then(redisTemplate.opsForValue()
                    .set(CURRENT_KEY_VERSION, version, Duration.ofDays(keyRotationDays * KEY_RETENTION_COUNT)))
                .thenReturn(version)
                .doOnSuccess(v -> {
                    keyCache.put(version, newKey);
                    log.info("新JWT密钥已生成并存储: version={}", version);
                });
                
        } catch (Exception e) {
            return Mono.error(new RuntimeException("生成JWT密钥失败", e));
        }
    }

    /**
     * 生成密钥对象
     */
    private JwtKey generateKey(String version) throws Exception {
        JwtKey jwtKey = new JwtKey();
        jwtKey.setVersion(version);
        jwtKey.setAlgorithm(algorithm);
        jwtKey.setCreatedAt(LocalDateTime.now());
        jwtKey.setExpiresAt(LocalDateTime.now().plusDays(keyRotationDays * KEY_RETENTION_COUNT));

        if (algorithm.startsWith("HS")) {
            KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
            keyGen.init(256);
            SecretKey secretKey = keyGen.generateKey();
            jwtKey.setSecretKey(Base64.getEncoder().encodeToString(secretKey.getEncoded()));
            
        } else if (algorithm.startsWith("RS")) {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            
            jwtKey.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
            jwtKey.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        }

        return jwtKey;
    }

    /**
     * 生成密钥版本号（时间戳 + 随机数）
     */
    private String generateKeyVersion() {
        return "v" + System.currentTimeMillis() + "_" + 
            Integer.toHexString(new SecureRandom().nextInt(0xFFFF));
    }

    /**
     * 从Redis加载当前密钥版本
     */
    private Mono<String> loadCurrentKeyVersion() {
        return redisTemplate.opsForValue().get(CURRENT_KEY_VERSION);
    }

    /**
     * 从Redis加载密钥
     */
    private Mono<JwtKey> loadKeyFromRedis(String version) {
        String redisKey = REDIS_KEY_PREFIX + version;
        
        return redisTemplate.opsForHash().entries(redisKey)
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .map(map -> {
                JwtKey key = new JwtKey();
                key.setVersion(version);
                key.setAlgorithm((String) map.get("algorithm"));
                key.setSecretKey((String) map.get("secretKey"));
                key.setPrivateKey((String) map.get("privateKey"));
                key.setPublicKey((String) map.get("publicKey"));
                key.setCreatedAt(LocalDateTime.parse((String) map.get("createdAt")));
                key.setExpiresAt(LocalDateTime.parse((String) map.get("expiresAt")));
                return key;
            });
    }

    /**
     * 存储密钥到Redis
     */
    private Mono<Boolean> storeKeyToRedis(JwtKey key) {
        String redisKey = REDIS_KEY_PREFIX + key.getVersion();
        
        Map<String, String> keyData = new ConcurrentHashMap<>();
        keyData.put("algorithm", key.getAlgorithm());
        keyData.put("createdAt", key.getCreatedAt().toString());
        keyData.put("expiresAt", key.getExpiresAt().toString());
        
        if (key.getSecretKey() != null) {
            keyData.put("secretKey", key.getSecretKey());
        }
        if (key.getPrivateKey() != null) {
            keyData.put("privateKey", key.getPrivateKey());
        }
        if (key.getPublicKey() != null) {
            keyData.put("publicKey", key.getPublicKey());
        }
        
        return redisTemplate.opsForHash().putAll(redisKey, keyData)
            .then(redisTemplate.expire(redisKey, 
                Duration.between(LocalDateTime.now(), key.getExpiresAt())))
            .thenReturn(true);
    }

    /**
     * 清理过期的旧密钥
     */
    private Mono<Void> cleanupOldKeys() {
        return redisTemplate.keys(REDIS_KEY_PREFIX + "*")
            .collectList()
            .flatMap(keys -> {
                if (keys.size() <= KEY_RETENTION_COUNT) {
                    return Mono.empty();
                }
                
                keys.sort(String::compareTo);
                int toRemove = keys.size() - KEY_RETENTION_COUNT;
                
                return redisTemplate.delete(keys.subList(0, toRemove).toArray(new String[0]))
                    .doOnSuccess(count -> {
                        log.info("已清理{}个过期JWT密钥", count);
                        keys.subList(0, toRemove).forEach(k -> 
                            keyCache.remove(k.replace(REDIS_KEY_PREFIX, "")));
                    })
                    .then();
            });
    }

    /**
     * JWT密钥对象
     */
    @Data
    public static class JwtKey {
        private String version;
        private String algorithm;
        private String secretKey;
        private String privateKey;
        private String publicKey;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;

        /**
         * 判断密钥是否已过期
         *
         * @return 当前时间晚于过期时间时返回true
         */
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt);
        }

        /**
         * 获取签名密钥（用于签发Token）
         *
         * @return HMAC算法返回HMAC-SHA256密钥，RSA算法返回RSA私钥
         * @throws Exception 密钥解码失败或算法不支持时抛出
         */
        public Key getSigningKey() throws Exception {
            if (algorithm.startsWith("HS")) {
                byte[] keyBytes = Base64.getDecoder().decode(secretKey);
                return new SecretKeySpec(keyBytes, "HmacSHA256");
            } else if (algorithm.startsWith("RS")) {
                byte[] keyBytes = Base64.getDecoder().decode(privateKey);
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePrivate(spec);
            }
            throw new IllegalArgumentException("不支持的算法: " + algorithm);
        }

        /**
         * 获取验证密钥（用于验证Token签名）
         *
         * @return HMAC算法与签名密钥相同，RSA算法返回RSA公钥
         * @throws Exception 密钥解码失败或算法不支持时抛出
         */
        public Key getVerifyingKey() throws Exception {
            if (algorithm.startsWith("HS")) {
                return getSigningKey();
            } else if (algorithm.startsWith("RS")) {
                byte[] keyBytes = Base64.getDecoder().decode(publicKey);
                X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
                KeyFactory kf = KeyFactory.getInstance("RSA");
                return kf.generatePublic(spec);
            }
            throw new IllegalArgumentException("不支持的算法: " + algorithm);
        }
    }
}
