package com.apex.scaffold.tools.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP 敏感字段的离线加解密与格式校验。
 *
 * <p>密文格式固定为 Base64(IV[12] + AES-256-GCM ciphertext + tag[16])，与业务侧加密实现保持一致；
 * 迁移时必须先在旧密钥下解密成功，再按新密钥重新加密，未知格式一律拒绝。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public final class TotpFieldCipher {

    /** 待迁移的三个 TOTP 敏感字段。 */
    public static final List<String> FIELDS = List.of("secret", "backup_codes", "used_backup_codes");

    /** 报告与校验中使用的明文格式标识。 */
    public static final String FORMAT = "apex-totp-offline-v1";

    /** TOTP 种子字段名。 */
    public static final String SECRET = "secret";

    /** AES-GCM 变换名称。 */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** 初始向量长度。 */
    private static final int IV_LENGTH = 12;

    /** 认证标签位数。 */
    private static final int TAG_BITS = 128;

    /** 认证标签字节数。 */
    private static final int TAG_LENGTH = TAG_BITS / 8;

    /** Base64 分组长度，用于快速排除明显非法的密文。 */
    private static final int BASE64_GROUP = 4;

    /** TOTP 种子明文：32 位 Base32 字符。 */
    private static final String SECRET_PATTERN = "[A-Z2-7]{32}";

    /** 备份码明文：空串或 8 位数字的逗号分隔列表。 */
    private static final String BACKUP_CODE_PATTERN = "[0-9]{8}(,[0-9]{8})*";

    /** 报告中的密文格式描述。 */
    private static final String CIPHER_DESCRIPTION = "Base64(IV[12] + AES-256-GCM ciphertext + tag[16])";

    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpFieldCipher() {}

    /**
     * 返回报告使用的密文格式描述。
     *
     * @return 密文格式描述
     */
    public static String cipherDescription() {
        return CIPHER_DESCRIPTION;
    }

    /**
     * 使用 AES-256-GCM 加密为可存储的密文文本。
     *
     * @param plain 明文字符串
     * @param key   32 字节密钥
     * @return Base64 密文
     * @throws Exception 加密算法不可用时抛出
     */
    public static String encrypt(String plain, byte[] key) throws Exception {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
        byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] combined = ByteBuffer.allocate(iv.length + encrypted.length)
                .put(iv)
                .put(encrypted)
                .array();
        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 解密 Base64 密文，认证失败时统一报格式错误。
     *
     * @param value Base64 密文
     * @param key   32 字节密钥
     * @return 明文字符串
     * @throws Exception 解密或认证失败时抛出
     */
    public static String decrypt(String value, byte[] key) throws Exception {
        if (value.length() % BASE64_GROUP != 0) {
            throw new IllegalArgumentException("密文格式或认证校验失败");
        }
        byte[] raw = Base64.getDecoder().decode(value);
        if (raw.length < IV_LENGTH + TAG_LENGTH) {
            throw new IllegalArgumentException("密文格式或认证校验失败");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(TAG_BITS, Arrays.copyOf(raw, IV_LENGTH)));
        byte[] plain = cipher.doFinal(raw, IV_LENGTH, raw.length - IV_LENGTH);
        return StandardCharsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(plain))
                .toString();
    }

    /**
     * 判断解密结果是否符合该字段的明文格式。
     *
     * @param field 字段名
     * @param value 明文字符串
     * @return 符合格式时返回 true
     */
    public static boolean validPlaintext(String field, String value) {
        if (SECRET.equals(field)) {
            return value.matches(SECRET_PATTERN);
        }
        return value.isEmpty() || value.matches(BACKUP_CODE_PATTERN);
    }

    /**
     * 将单个字段迁移到新密钥：明文直接加密，旧密钥密文重加密，已是新密钥密文则原样保留。
     *
     * @param field  字段名
     * @param value  原始 JSON 值
     * @param key    新密钥
     * @param oldKey 旧密钥，可为 null
     * @return 迁移后的 JSON 值
     * @throws Exception 格式无法识别或加密失败时抛出
     */
    public static JsonNode transform(String field, JsonNode value, byte[] key, byte[] oldKey) throws Exception {
        if (value.isNull() && !SECRET.equals(field)) {
            return value;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("TOTP 字段类型无效");
        }
        String plain = decryptWithCandidates(value.asText(), key, oldKey);
        if (plain != null) {
            if (!validPlaintext(field, plain)) {
                throw new IllegalArgumentException("解密后的 TOTP 字段格式无效");
            }
            boolean alreadyCurrent = decryptWithCandidates(value.asText(), key, null) != null;
            return alreadyCurrent ? value : TextNode.valueOf(encrypt(plain, key));
        }
        if (!validPlaintext(field, value.asText())) {
            throw new IllegalArgumentException("未知 TOTP 格式或密钥不匹配；拒绝迁移");
        }
        return TextNode.valueOf(encrypt(value.asText(), key));
    }

    /**
     * 依次用新、旧密钥尝试解密。
     *
     * @param cipherText 密文
     * @param key        新密钥
     * @param oldKey     旧密钥，可为 null
     * @return 首个解密成功的明文；全部失败时返回 null
     */
    private static String decryptWithCandidates(String cipherText, byte[] key, byte[] oldKey) {
        if (key != null) {
            String plain = tryDecrypt(cipherText, key);
            if (plain != null) {
                return plain;
            }
        }
        if (oldKey != null) {
            return tryDecrypt(cipherText, oldKey);
        }
        return null;
    }

    /**
     * 尝试解密并吞掉认证失败异常，交由调用方判定为「不匹配」。
     *
     * @param cipherText 密文
     * @param candidate  候选密钥
     * @return 解密成功时的明文，失败时返回 null
     */
    private static String tryDecrypt(String cipherText, byte[] candidate) {
        try {
            return decrypt(cipherText, candidate);
        } catch (Exception error) {
            return null;
        }
    }
}
