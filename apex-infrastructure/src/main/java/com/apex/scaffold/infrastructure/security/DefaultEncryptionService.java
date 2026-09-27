package com.apex.scaffold.infrastructure.security;

import com.apex.scaffold.common.security.EncryptionService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 加密服务默认实现
 *
 * 使用AES-256-GCM进行加密
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Service
public class DefaultEncryptionService implements EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_SIZE = 256;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public DefaultEncryptionService() {
        try {
            // 生产环境应从安全配置或密钥管理服务获取密钥
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE);
            this.secretKey = keyGenerator.generateKey();
            this.secureRandom = new SecureRandom();

            log.warn("使用默认生成的密钥，生产环境请配置外部密钥管理服务");
        } catch (Exception e) {
            throw new RuntimeException("初始化加密服务失败", e);
        }
    }

    @Override
    public Mono<String> encrypt(String plaintext) {
        return Mono.fromCallable(() -> {
            try {
                // 生成随机IV
                byte[] iv = new byte[GCM_IV_LENGTH];
                secureRandom.nextBytes(iv);

                // 初始化加密器
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

                // 加密
                byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

                // 组合IV和密文
                ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + ciphertext.length);
                byteBuffer.put(iv);
                byteBuffer.put(ciphertext);

                // Base64编码
                return Base64.getEncoder().encodeToString(byteBuffer.array());
            } catch (Exception e) {
                throw new RuntimeException("加密失败", e);
            }
        });
    }

    @Override
    public Mono<String> decrypt(String ciphertext) {
        return Mono.fromCallable(() -> {
            try {
                // Base64解码
                byte[] decoded = Base64.getDecoder().decode(ciphertext);

                // 提取IV和密文
                ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
                byte[] iv = new byte[GCM_IV_LENGTH];
                byteBuffer.get(iv);
                byte[] encryptedData = new byte[byteBuffer.remaining()];
                byteBuffer.get(encryptedData);

                // 初始化解密器
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

                // 解密
                byte[] plaintext = cipher.doFinal(encryptedData);
                return new String(plaintext, StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new RuntimeException("解密失败", e);
            }
        });
    }

    @Override
    public String hash(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("哈希计算失败", e);
        }
    }
}
