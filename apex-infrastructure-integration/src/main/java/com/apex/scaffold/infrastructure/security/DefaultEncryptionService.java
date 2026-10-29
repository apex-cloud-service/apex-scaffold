package com.apex.scaffold.infrastructure.security;

import com.apex.scaffold.common.security.EncryptionService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class DefaultEncryptionService implements EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_SIZE = 256;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    /**
     * 从稳定保存的 Base64 配置解析 AES-256 密钥，不生成临时替代密钥。
     *
     * @param keyBase64 Base64 编码的 32 字节密钥
     * @throws IllegalArgumentException 配置缺失、为空白、编码非法或解码后长度不是 32 字节
     */
    public DefaultEncryptionService(@Value("${apex.security.data-encryption-key:}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new IllegalArgumentException("必须配置数据加密密钥");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("数据加密密钥必须为有效Base64");
        }
        if (keyBytes.length != KEY_SIZE / Byte.SIZE) {
            throw new IllegalArgumentException("数据加密密钥必须是32字节（256位）");
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        this.secureRandom = new SecureRandom();
    }

    /**
     * 订阅时以 UTF-8 编码明文并进行 AES-256-GCM 加密，每次生成独立的 12 字节 IV。
     *
     * <p>在订阅线程执行，不自行调度；IV 与含认证标签的密文拼接后以 Base64 返回。
     *
     * @param plaintext 非空明文，允许空字符串
     * @return Base64 编码结果；加密或输入处理失败以 {@link RuntimeException} 错误信号传播，并保留原始异常原因
     */
    @Override
    public Mono<String> encrypt(String plaintext) {
        return Mono.fromCallable(() -> {
            try {
                // 生成随机IV
                byte[] iv = new byte[GCM_IV_LENGTH];
                secureRandom.nextBytes(iv);

                // 初始化加密器（GCM 参数绑定本次生成的 IV）
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

    /**
     * 订阅时解析 Base64 数据的前 12 字节 IV，并使用配置密钥验证 GCM 标签后还原明文。
     *
     * <p>在订阅线程执行，不自行调度；必须使用与加密时一致且稳定保存的密钥。
     *
     * @param ciphertext 本服务加密格式的非空 Base64 数据
     * @return UTF-8 明文；编码非法、数据截断、密钥不匹配或认证失败均以
     *     {@link RuntimeException} 错误信号传播，并保留原始异常原因
     */
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

                // 初始化解密器（GCM 参数绑定密文中携带的 IV）
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

    /**
     * 在调用线程同步计算 UTF-8 数据的 SHA-256 摘要并编码为 Base64。
     *
     * <p>该摘要不带盐、不使用加密密钥，不能替代专用的密码存储算法。
     *
     * @param data 非空输入，允许空字符串
     * @return 32 字节摘要的 Base64 表示
     * @throws RuntimeException 输入处理或摘要计算失败，原始异常保留为原因
     */
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
