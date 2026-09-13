package com.apex.scaffold.infrastructure.chuanglan.strategy;

import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AES 手机号解密策略实现。
 *
 * <p>P0-CL-10重构：将创蓝闪验 API 返回的 mobileName 密文使用 AES/CBC/PKCS5Padding 解密，
 * 提取自原先 {@code ChuanglanApiService.decryptAes()} 方法，遵循策略模式实现
 * {@link PhoneDecryptStrategy} 接口。
 *
 * <h3>解密算法</h3>
 * <ol>
 *   <li>计算 {@code md5(appKey)} 得到 32 位十六进制字符串</li>
 *   <li>取前 16 位作为 AES 密钥</li>
 *   <li>取后 16 位作为 CBC 模式的初始化向量（IV）</li>
 *   <li>将 mobileName 十六进制字符串转换为字节数组</li>
 *   <li>使用 AES/CBC/PKCS5Padding 解密</li>
 *   <li>返回 UTF-8 解码后的明文手机号</li>
 * </ol>
 *
 * <h3>设计模式</h3>
 * <ul>
 *   <li><b>策略模式</b>：封装 AES 解密算法，与 RSA 等其他策略可互换</li>
 *   <li>Spring {@code @Component} 注册，支持依赖注入</li>
 * </ul>
 *
 * @author Apex Team
 * @version 3.1.0
 * @since 3.1.0
 * @see PhoneDecryptStrategy
 * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
 */
@Slf4j
@Component
public class AesPhoneDecryptStrategy implements PhoneDecryptStrategy {

    /** 加密类型标识：AES */
    private static final String ENCRYPT_TYPE = "0";

    /** 十六进制转换步长 */
    private static final int HEX_STEP = 2;

    /** MD5 输出长度 */
    private static final int MD5_LENGTH = 32;

    /**
     * 构造 AES 解密策略。
     */
    public AesPhoneDecryptStrategy() {}

    /**
     * {@inheritDoc}
     *
     * <p>使用 AES/CBC/PKCS5Padding 解密，密钥由 md5(appKey) 派生。</p>
     */
    @Override
    public String decrypt(String encryptedPhone, String appKey) {
        try {
            // MD5(appKey) -> 32位十六进制字符串
            String md5Key = md5(appKey);
            // 前16位作为AES密钥
            String key = md5Key.substring(0, 16);
            // 后16位作为初始化向量
            String iv = md5Key.substring(16);

            // 将十六进制字符串转换为字节数组
            byte[] encryptedBytes = hexToBytes(encryptedPhone);

            // AES/CBC/PKCS5Padding 解密
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES手机号解密失败", e);
            throw new ChuanglanApiException("DECRYPT_ERROR", "手机号解密失败: " + e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getEncryptType() {
        return ENCRYPT_TYPE;
    }

    /**
     * MD5 信息摘要算法。
     *
     * <p>对输入字符串进行 MD5 哈希，返回 32 位十六进制小写字符串。</p>
     *
     * @param data 待哈希的原始字符串
     * @return 32 位十六进制 MD5 哈希值
     * @throws ChuanglanApiException MD5 算法不可用时抛出
     */
    private String md5(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(MD5_LENGTH);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5计算失败, data: {}", data, e);
            throw new ChuanglanApiException("CRYPTO_ERROR", "MD5算法不可用");
        }
    }

    /**
     * 十六进制字符串转字节数组。
     *
     * <p>每两个十六进制字符对应一个字节，如 "1F88" → [0x1F, 0x88]。</p>
     *
     * @param hexString 十六进制字符串，长度必须为偶数
     * @return 字节数组
     */
    private byte[] hexToBytes(String hexString) {
        int len = hexString.length();
        if (len % HEX_STEP != 0) {
            throw new IllegalArgumentException("十六进制字符串长度必须为偶数: " + len);
        }
        byte[] data = new byte[len / HEX_STEP];
        for (int i = 0; i < len; i += HEX_STEP) {
            data[i / HEX_STEP] = (byte)
                    ((Character.digit(hexString.charAt(i), 16) << 4) + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }
}
