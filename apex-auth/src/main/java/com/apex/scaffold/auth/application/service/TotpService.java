package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.domain.model.entity.MfaConfigEntity;
import com.apex.scaffold.auth.domain.model.entity.TotpEntity;
import com.apex.scaffold.auth.domain.repository.MfaConfigRepository;
import com.apex.scaffold.auth.domain.repository.TotpRepository;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * TOTP（Time-based One-Time Password）服务
 *
 * <p>提供 TOTP 多因素认证的核心功能，包括：
 * <ul>
 *   <li>生成 TOTP 密钥（Base32 编码）</li>
 *   <li>验证 TOTP 验证码（6 位数字）</li>
 *   <li>生成 OTP Auth URL（用于二维码）</li>
 *   <li>生成备份码</li>
 *   <li>验证备份码</li>
 * </ul>
 *
 * <p>遵循 RFC 6238 标准，支持 30 秒时间窗口和 ±1 窗口容差。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TotpService {

    private static final int BASE32_BITS_PER_CHAR = 5;
    private static final int BASE32_PADDING_BLOCK_SIZE = 8;

    private final TotpRepository totpRepository;
    private final MfaConfigRepository mfaConfigRepository;

    /** TOTP 时间步长（秒） */
    private static final int TIME_STEP = 30;

    /** TOTP 密钥长度（字节） */
    private static final int SECRET_SIZE = 20;

    /** TOTP 验证码位数 */
    private static final int CODE_DIGITS = 6;

    /** 备份码数量 */
    private static final int BACKUP_CODE_COUNT = 10;

    /** 备份码长度 */
    private static final int BACKUP_CODE_LENGTH = 8;

    /** 时间窗口容差（前后各 1 个窗口） */
    private static final int WINDOW = 1;

    /** Base32 字符表 */
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 为用户生成新的 TOTP 密钥
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 包含密钥和 OTP Auth URL 的结果
     * @param issuer      OTP Auth URL 中的发行方标识
     * @param accountName OTP Auth URL 中的账户名
     */
    public Mono<TotpSetupResult> generateSecret(Long userId, String tenantId, String issuer, String accountName) {
        // 生成随机密钥
        byte[] secretBytes = new byte[SECRET_SIZE];
        secureRandom.nextBytes(secretBytes);
        String secret = encodeBase32(secretBytes);

        // 生成 OTP Auth URL
        String otpAuthUrl = buildOtpAuthUrl(issuer, accountName, secret);

        // 生成备份码
        List<String> backupCodes = generateBackupCodes();

        // 创建 TOTP 实体
        TotpEntity totpEntity = TotpEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .secret(secret)
                .verified(false)
                .backupCodes(String.join(",", backupCodes))
                .usedBackupCodes(null)
                .enabled(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(1)
                .deleted(false)
                .build();

        return totpRepository.save(totpEntity).map(saved -> new TotpSetupResult(secret, otpAuthUrl, backupCodes));
    }

    /**
     * 验证 TOTP 验证码
     *
     * @param userId   用户 ID
     * @param code     用户输入的 6 位验证码
     * @param tenantId 租户 ID
     * @return 验证是否成功
     */
    public Mono<Boolean> verifyCode(Long userId, String code, String tenantId) {
        return totpRepository
                .findByUserId(userId, tenantId)
                .map(totp -> {
                    if (!totp.isEnabled()) {
                        log.warn("TOTP 未启用: userId={}", userId);
                        return false;
                    }
                    return verifyTotpCode(totp.getSecret(), code);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 验证 TOTP 验证码并标记为已验证（首次设置时使用）
     *
     * @param userId   用户 ID
     * @param code     用户输入的 6 位验证码
     * @param tenantId 租户 ID
     * @return 验证是否成功
     */
    public Mono<Boolean> verifyAndEnable(Long userId, String code, String tenantId) {
        return totpRepository
                .findByUserId(userId, tenantId)
                .flatMap(totp -> {
                    boolean valid = verifyTotpCode(totp.getSecret(), code);
                    if (!valid) {
                        log.warn("TOTP 验证码错误: userId={}", userId);
                        return Mono.just(false);
                    }
                    totp.markAsVerified();
                    totp.enable();
                    return totpRepository
                            .update(totp)
                            .then(Mono.fromCallable(() -> {
                                // 同步创建 MFA 配置
                                MfaConfigEntity mfaConfig = MfaConfigEntity.builder()
                                        .tenantId(tenantId)
                                        .userId(userId)
                                        .mfaType(MfaConfigEntity.MfaType.TOTP.name())
                                        .enabled(true)
                                        .required(false)
                                        .priority(1)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .version(1)
                                        .deleted(false)
                                        .build();
                                return mfaConfig;
                            }))
                            .flatMap(mfaConfigRepository::save)
                            .thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 禁用用户的 TOTP
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 是否禁用成功
     */
    public Mono<Boolean> disable(Long userId, String tenantId) {
        return totpRepository
                .findByUserId(userId, tenantId)
                .flatMap(totp -> {
                    totp.disable();
                    return totpRepository
                            .update(totp)
                            .then(mfaConfigRepository.findByUserIdAndType(
                                    userId, MfaConfigEntity.MfaType.TOTP.name(), tenantId))
                            .flatMap(config -> {
                                config.disable();
                                return mfaConfigRepository.update(config);
                            })
                            .thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 验证备份码
     *
     * @param userId     用户 ID
     * @param backupCode 备份码
     * @param tenantId   租户 ID
     * @return 验证是否成功
     */
    public Mono<Boolean> verifyBackupCode(Long userId, String backupCode, String tenantId) {
        return totpRepository
                .findByUserId(userId, tenantId)
                .flatMap(totp -> {
                    if (!totp.isEnabled() || !totp.useBackupCode(backupCode)) {
                        return Mono.just(false);
                    }
                    return totpRepository.update(totp).thenReturn(true);
                })
                .defaultIfEmpty(false);
    }

    /**
     * 获取用户的 TOTP 配置
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return TOTP 实体
     */
    public Mono<TotpEntity> getTotpConfig(Long userId, String tenantId) {
        return totpRepository.findByUserId(userId, tenantId);
    }

    /**
     * 检查用户是否已启用 TOTP
     *
     * @param userId   用户 ID
     * @param tenantId 租户 ID
     * @return 是否已启用
     */
    public Mono<Boolean> isEnabled(Long userId, String tenantId) {
        return totpRepository
                .findByUserId(userId, tenantId)
                .map(TotpEntity::isEnabled)
                .defaultIfEmpty(false);
    }

    // ==================== TOTP 算法实现 ====================

    /**
     * 验证 TOTP 验证码
     *
     * @param secret Base32 编码的密钥
     * @param code   用户输入的验证码
     * @return 验证是否成功
     */
    private boolean verifyTotpCode(String secret, String code) {
        if (secret == null || code == null || code.length() != CODE_DIGITS) {
            return false;
        }

        long currentTime = System.currentTimeMillis() / 1000;
        long currentStep = currentTime / TIME_STEP;

        // 检查当前窗口和前后各 1 个窗口
        for (int i = -WINDOW; i <= WINDOW; i++) {
            long step = currentStep + i;
            String expectedCode = generateTotpCode(secret, step);
            if (expectedCode.isEmpty()) {
                return false;
            }
            if (code.equals(expectedCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成 TOTP 验证码
     *
     * @param secret Base32 编码的密钥
     * @param step   时间步长
     * @return 6 位验证码
     */
    private String generateTotpCode(String secret, long step) {
        byte[] key = decodeBase32(secret);
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(step).array();

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(timeBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int truncated = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int code = truncated % (int) Math.pow(10, CODE_DIGITS);
            return String.format("%0" + CODE_DIGITS + "d", code);
        } catch (Exception e) {
            log.warn("生成 TOTP 验证码失败: error={}", e.getClass().getSimpleName());
            return "";
        }
    }

    /**
     * 生成当前时间的 TOTP 验证码（用于测试）
     *
     * @param secret Base32 编码的密钥
     * @return 6 位验证码
     */
    public String generateCurrentCode(String secret) {
        long currentTime = System.currentTimeMillis() / 1000;
        long currentStep = currentTime / TIME_STEP;
        return generateTotpCode(secret, currentStep);
    }

    // ==================== 工具方法 ====================

    /**
     * 构建 OTP Auth URL
     *
     * <p>格式: otpauth://totp/Issuer:accountName?secret=SECRET&issuer=Issuer&algorithm=SHA1&digits=6&period=30
     *
     * @param issuer      发行方
     * @param accountName 账户名
     * @param secret      Base32 密钥
     * @return OTP Auth URL
     */
    private String buildOtpAuthUrl(String issuer, String accountName, String secret) {
        return String.format(
                "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
                issuer, accountName, secret, issuer, CODE_DIGITS, TIME_STEP);
    }

    /**
     * Base32 编码
     *
     * @param bytes 字节数组
     * @return Base32 编码字符串
     */
    private String encodeBase32(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : bytes) {
            buffer = (buffer << Byte.SIZE) | (b & 0xFF);
            bitsLeft += Byte.SIZE;
            while (bitsLeft >= BASE32_BITS_PER_CHAR) {
                int index = (buffer >> (bitsLeft - BASE32_BITS_PER_CHAR)) & 0x1F;
                result.append(BASE32_CHARS.charAt(index));
                bitsLeft -= BASE32_BITS_PER_CHAR;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (BASE32_BITS_PER_CHAR - bitsLeft)) & 0x1F;
            result.append(BASE32_CHARS.charAt(index));
        }

        // 补齐到 Byte.SIZE 的倍数
        while (result.length() % BASE32_PADDING_BLOCK_SIZE != 0) {
            result.append('=');
        }

        return result.toString();
    }

    /**
     * Base32 解码
     *
     * @param encoded Base32 编码字符串
     * @return 字节数组
     */
    private byte[] decodeBase32(String encoded) {
        encoded = encoded.replaceAll("=", "");
        List<Byte> bytes = new ArrayList<>();
        int buffer = 0;
        int bitsLeft = 0;

        for (char c : encoded.toCharArray()) {
            int index = BASE32_CHARS.indexOf(Character.toUpperCase(c));
            if (index < 0) {
                continue;
            }
            buffer = (buffer << BASE32_BITS_PER_CHAR) | index;
            bitsLeft += BASE32_BITS_PER_CHAR;
            if (bitsLeft >= Byte.SIZE) {
                bytes.add((byte) ((buffer >> (bitsLeft - Byte.SIZE)) & 0xFF));
                bitsLeft -= Byte.SIZE;
            }
        }

        byte[] result = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            result[i] = bytes.get(i);
        }
        return result;
    }

    /**
     * 生成备份码列表
     *
     * @return 备份码列表
     */
    private List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < BACKUP_CODE_LENGTH; j++) {
                code.append(secureRandom.nextInt(10));
            }
            codes.add(code.toString());
        }
        return codes;
    }

    /**
     * TOTP 设置结果
     */
    public record TotpSetupResult(String secret, String otpAuthUrl, List<String> backupCodes) {
        /**
         * 获取 Base64 编码的二维码图片数据
         *
         * @return data:image/png;base64,... 格式的图片数据
         */
        public String getQrCodeDataUrl() {
            // 实际项目中可以使用 ZXing 库生成二维码图片
            // 这里返回 OTP Auth URL 供前端生成二维码
            return otpAuthUrl;
        }
    }
}
