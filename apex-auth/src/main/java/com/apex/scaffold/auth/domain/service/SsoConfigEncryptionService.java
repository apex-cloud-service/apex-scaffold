package com.apex.scaffold.auth.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * SSO配置加密存储服务
 *
 * <p>对SSO敏感配置进行加密存储，防止配置泄露：</p>
 * <ul>
 *   <li>加密算法：AES-256-GCM（认证加密）</li>
 *   <li>密钥管理：主密钥存储在环境变量或密钥管理服务</li>
 *   <li>加密范围：OAuth2 client_secret、SAML证书私钥、OIDC密钥</li>
 *   <li>支持字段级加密和解密</li>
 * </ul>
 *
 * <p>配置加密流程：</p>
 * <ol>
 *   <li>生成随机IV（初始化向量）</li>
 *   <li>使用AES-256-GCM加密敏感字段</li>
 *   <li>存储格式：Base64(IV) + ":" + Base64(密文 + 认证标签)</li>
 *   <li>存储到数据库或Redis</li>
 * </ol>
 *
 * <p>敏感字段标识：</p>
 * <ul>
 *   <li>OAuth2: clientSecret</li>
 *   <li>SAML: privateKey, certificate</li>
 *   <li>OIDC: clientSecret</li>
 *   <li>LDAP: bindPassword</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoConfigEncryptionService {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int AES_KEY_SIZE = 256;
    private static final int ENCRYPTED_TEXT_PARTS_COUNT = 2;

    private static final String REDIS_MASTER_KEY = "sso:encryption:master_key";

    @Value("${apex.security.sso.encryption.master-key:#{null}}")
    private String masterKeyFromEnv;

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * 加密敏感配置字段
     *
     * @param plainText 明文
     * @return 密文（Base64格式：IV:CipherText）
     */
    public Mono<String> encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return Mono.just("");
        }

        return getMasterKey()
                .flatMap(masterKey -> Mono.fromCallable(() -> {
                    try {
                        // 生成随机IV
                        byte[] iv = new byte[GCM_IV_LENGTH];
                        SecureRandom secureRandom = new SecureRandom();
                        secureRandom.nextBytes(iv);

                        // 初始化加密器（GCM 参数绑定本次生成的 IV）
                        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
                        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                        cipher.init(Cipher.ENCRYPT_MODE, masterKey, gcmSpec);

                        // 加密
                        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

                        // 组合IV和密文
                        String ivBase64 = Base64.getEncoder().encodeToString(iv);
                        String cipherBase64 = Base64.getEncoder().encodeToString(cipherText);

                        String encrypted = ivBase64 + ":" + cipherBase64;
                        log.debug("SSO配置加密成功: length={}", plainText.length());

                        return encrypted;
                    } catch (Exception e) {
                        throw new RuntimeException("SSO配置加密失败", e);
                    }
                }));
    }

    /**
     * 解密敏感配置字段
     *
     * @param encryptedText 密文（Base64格式：IV:CipherText）
     * @return 明文
     */
    public Mono<String> decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return Mono.just("");
        }

        return getMasterKey()
                .flatMap(masterKey -> Mono.fromCallable(() -> {
                    try {
                        String[] parts = encryptedText.split(":", ENCRYPTED_TEXT_PARTS_COUNT);
                        if (parts.length != ENCRYPTED_TEXT_PARTS_COUNT) {
                            throw new IllegalArgumentException("密文格式无效");
                        }

                        byte[] iv = Base64.getDecoder().decode(parts[0]);
                        byte[] cipherText = Base64.getDecoder().decode(parts[1]);

                        // 初始化解密器（GCM 参数绑定密文携带的 IV）
                        Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
                        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                        cipher.init(Cipher.DECRYPT_MODE, masterKey, gcmSpec);

                        // 解密
                        byte[] plainText = cipher.doFinal(cipherText);
                        String decrypted = new String(plainText, StandardCharsets.UTF_8);

                        log.debug("SSO配置解密成功");
                        return decrypted;
                    } catch (Exception e) {
                        throw new RuntimeException("SSO配置解密失败", e);
                    }
                }));
    }

    /**
     * 获取或生成主密钥
     *
     * <p>优先级：</p>
     * <ol>
     *   <li>环境变量 apex.security.sso.encryption.master-key</li>
     *   <li>Redis中已存储的主密钥</li>
     *   <li>生成新的主密钥并存储到Redis</li>
     * </ol>
     *
     * @return AES-256主密钥
     */
    private Mono<SecretKey> getMasterKey() {
        // 1. 优先从环境变量获取
        if (masterKeyFromEnv != null && !masterKeyFromEnv.isEmpty()) {
            return Mono.fromCallable(() -> {
                byte[] keyBytes = Base64.getDecoder().decode(masterKeyFromEnv);
                return new SecretKeySpec(keyBytes, "AES");
            });
        }

        // 2. 从Redis获取已存储的主密钥
        return redisTemplate
                .opsForValue()
                .get(REDIS_MASTER_KEY)
                .flatMap(encodedKey -> Mono.fromCallable(() -> {
                    byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
                    return (SecretKey) new SecretKeySpec(keyBytes, "AES");
                }))
                .switchIfEmpty(Mono.defer(() -> {
                    // 3. 生成新的主密钥
                    log.warn("未找到SSO加密主密钥，正在生成新密钥。生产环境应从外部密钥管理服务获取！");
                    return generateAndStoreMasterKey();
                }));
    }

    /**
     * 生成并存储新的主密钥
     *
     * @return 新生成的主密钥
     */
    private Mono<SecretKey> generateAndStoreMasterKey() {
        return Mono.fromCallable(() -> {
                    try {
                        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                        keyGen.init(AES_KEY_SIZE, new SecureRandom());
                        SecretKey masterKey = keyGen.generateKey();

                        log.info("生成新的SSO加密主密钥: algorithm=AES, keySize={}", AES_KEY_SIZE);
                        return masterKey;
                    } catch (Exception e) {
                        throw new RuntimeException("生成主密钥失败", e);
                    }
                })
                .flatMap(masterKey -> {
                    // 存储到Redis
                    String encodedKey = Base64.getEncoder().encodeToString(masterKey.getEncoded());
                    return redisTemplate
                            .opsForValue()
                            .set(REDIS_MASTER_KEY, encodedKey)
                            .thenReturn(masterKey)
                            .doOnSuccess(v -> log.info("主密钥已存储到Redis"));
                });
    }

    /**
     * 轮换主密钥（高级功能）
     *
     * <p>步骤：</p>
     * <ol>
     *   <li>生成新的主密钥</li>
     *   <li>用新密钥重新加密所有SSO配置</li>
     *   <li>更新Redis中的主密钥</li>
     * </ol>
     *
     * <p>注意：此操作需要停机维护窗口执行</p>
     *
     * @return 新主密钥ID
     */
    public Mono<String> rotateMasterKey() {
        log.warn("开始SSO配置主密钥轮换，此操作可能影响服务可用性");

        return Mono.fromCallable(() -> {
                    try {
                        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                        keyGen.init(AES_KEY_SIZE, new SecureRandom());
                        SecretKey newMasterKey = keyGen.generateKey();

                        String encodedKey = Base64.getEncoder().encodeToString(newMasterKey.getEncoded());
                        log.info("生成新的主密钥: keySize={}", AES_KEY_SIZE);

                        return encodedKey;
                    } catch (Exception e) {
                        throw new RuntimeException("生成新主密钥失败", e);
                    }
                })
                .flatMap(newKeyEncoded -> {
                    // 存储新主密钥
                    return redisTemplate
                            .opsForValue()
                            .set(REDIS_MASTER_KEY, newKeyEncoded)
                            .thenReturn(newKeyEncoded);
                })
                .doOnSuccess(newKey -> log.warn("主密钥轮换完成。请立即重新加密所有SSO配置！"))
                .doOnError(e -> log.error("主密钥轮换失败", e));
    }

    /**
     * 检查字段是否为敏感字段（需要加密）
     *
     * @param fieldName 字段名
     * @return true表示敏感字段
     */
    public boolean isSensitiveField(String fieldName) {
        if (fieldName == null) {
            return false;
        }

        return fieldName.equalsIgnoreCase("clientSecret")
                || fieldName.equalsIgnoreCase("privateKey")
                || fieldName.equalsIgnoreCase("certificate")
                || fieldName.equalsIgnoreCase("bindPassword")
                || fieldName.equalsIgnoreCase("apiKey")
                || fieldName.equalsIgnoreCase("token")
                || fieldName.toLowerCase().contains("secret")
                || fieldName.toLowerCase().contains("password")
                || fieldName.toLowerCase().contains("key");
    }
}
