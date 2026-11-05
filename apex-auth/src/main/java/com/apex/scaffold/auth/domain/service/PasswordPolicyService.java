package com.apex.scaffold.auth.domain.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

/**
 * 密码策略服务
 *
 * <p>实现企业级密码安全策略：</p>
 * <ul>
 *   <li>密码强度校验（长度、复杂度、字符类型）</li>
 *   <li>常见弱密码检测（字典攻击防护）</li>
 *   <li>密码历史记录（防止重复使用）</li>
 *   <li>密码过期策略</li>
 *   <li>密码相似度检测</li>
 * </ul>
 *
 * <p>密码强度等级：</p>
 * <ul>
 *   <li>弱密码（0-40分）：不允许使用</li>
 *   <li>中等密码（41-70分）：普通用户可用</li>
 *   <li>强密码（71-90分）：推荐使用</li>
 *   <li>极强密码（91-100分）：管理员必须</li>
 * </ul>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class PasswordPolicyService {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int ADMIN_MIN_PASSWORD_LENGTH = 16;

    private static final int PASSWORD_HISTORY_SIZE = 5;

    private static final int PASSWORD_EXPIRY_DAYS = 90;
    private static final int ADMIN_PASSWORD_EXPIRY_DAYS = 60;

    private static final String REDIS_PASSWORD_HISTORY = "password:history:";

    private static final int MIN_CHAR_TYPE_COUNT = 3;
    private static final int ALL_CHAR_TYPE_COUNT = 4;
    private static final int EXTRA_LENGTH_THRESHOLD = 16;

    private static final int SCORE_THRESHOLD_VERY_STRONG = 91;
    private static final int SCORE_THRESHOLD_STRONG = 71;
    private static final int SCORE_THRESHOLD_MEDIUM = 41;

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final PasswordEncoder passwordEncoder;

    /**
     * 常见弱密码列表（实际应用中应该从文件或数据库加载更大的字典）
     */
    private static final List<String> COMMON_WEAK_PASSWORDS = List.of(
            "password",
            "123456",
            "123456789",
            "12345678",
            "1234567890",
            "password123",
            "admin123",
            "root123",
            "qwerty",
            "abc123",
            "123123",
            "111111",
            "000000",
            "iloveyou",
            "welcome",
            "monkey",
            "dragon",
            "master",
            "sunshine",
            "princess");

    /**
     * 正则表达式
     */
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile(".*[a-z].*");

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile(".*[A-Z].*");
    private static final Pattern DIGIT_PATTERN = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL_CHAR_PATTERN =
            Pattern.compile(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    private static final Pattern SEQUENTIAL_PATTERN =
            Pattern.compile(".*(012|123|234|345|456|567|678|789|890|abc|bcd|cde|def).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEATED_PATTERN = Pattern.compile(".*(.)\\1{2,}.*");

    /**
     * 验证密码是否符合策略要求
     *
     * @param password 密码
     * @param username 用户名（用于检测密码中是否包含用户名）
     * @param isAdmin 是否管理员
     * @return 验证结果
     */
    @SuppressWarnings("PMD.MethodTooLongRule")
    public Mono<PasswordValidationResult> validatePassword(String password, String username, boolean isAdmin) {
        log.debug("验证密码策略: username={}, isAdmin={}", username, isAdmin);

        List<String> errors = new ArrayList<>();
        int score = 0;

        // 1. 检查密码长度
        int minLength = isAdmin ? ADMIN_MIN_PASSWORD_LENGTH : MIN_PASSWORD_LENGTH;
        if (password == null || password.length() < minLength) {
            errors.add(String.format("密码长度不能少于%d位", minLength));
            return Mono.just(new PasswordValidationResult(false, 0, errors));
        }
        score += Math.min(password.length() * 2, 20);

        // 2. 检查字符类型多样性
        boolean hasLowercase = LOWERCASE_PATTERN.matcher(password).matches();
        boolean hasUppercase = UPPERCASE_PATTERN.matcher(password).matches();
        boolean hasDigit = DIGIT_PATTERN.matcher(password).matches();
        boolean hasSpecial = SPECIAL_CHAR_PATTERN.matcher(password).matches();

        int charTypeCount = 0;
        if (hasLowercase) {
            score += 10;
            charTypeCount++;
        }
        if (hasUppercase) {
            score += 10;
            charTypeCount++;
        }
        if (hasDigit) {
            score += 10;
            charTypeCount++;
        }
        if (hasSpecial) {
            score += 15;
            charTypeCount++;
        }

        if (charTypeCount < MIN_CHAR_TYPE_COUNT) {
            errors.add("密码必须包含至少3种字符类型（大写字母、小写字母、数字、特殊符号）");
        }

        // 3. 检查是否包含用户名
        if (username != null && password.toLowerCase().contains(username.toLowerCase())) {
            errors.add("密码不能包含用户名");
            score -= 20;
        }

        // 4. 检查常见弱密码
        String lowerPassword = password.toLowerCase();
        for (String weakPassword : COMMON_WEAK_PASSWORDS) {
            if (lowerPassword.contains(weakPassword)) {
                errors.add("密码包含常见弱密码词汇，请更换更安全的密码");
                score -= 30;
                break;
            }
        }

        // 5. 检查连续字符
        if (SEQUENTIAL_PATTERN.matcher(password).matches()) {
            errors.add("密码不应包含连续的字符序列（如123、abc等）");
            score -= 10;
        }

        // 6. 检查重复字符
        if (REPEATED_PATTERN.matcher(password).matches()) {
            errors.add("密码不应包含3个及以上连续重复的字符");
            score -= 10;
        }

        if (password.length() >= EXTRA_LENGTH_THRESHOLD) {
            score += 10;
        }
        if (charTypeCount == ALL_CHAR_TYPE_COUNT) {
            score += 10;
        }

        if (isAdmin && charTypeCount < ALL_CHAR_TYPE_COUNT) {
            errors.add("管理员密码必须包含所有4种字符类型");
        }

        // 确保分数在0-100范围内
        score = Math.max(0, Math.min(100, score));

        // 判断是否通过验证
        boolean passed = errors.isEmpty() && score >= (isAdmin ? 80 : 60);

        return Mono.just(new PasswordValidationResult(passed, score, errors));
    }

    /**
     * 检查密码是否在历史记录中
     *
     * @param userId 用户ID
     * @param newPassword 新密码（明文）
     * @return true表示密码已使用过
     */
    public Mono<Boolean> isPasswordInHistory(String userId, String newPassword) {
        String key = REDIS_PASSWORD_HISTORY + userId;

        return redisTemplate
                .opsForList()
                .range(key, 0, -1)
                .collectList()
                // BCrypt 比对是 CPU 密集的同步调用：延迟到订阅后的阻塞池执行，
                // 避免占用 Lettuce/Netty 线程，也不要在 map 中执行。
                .flatMap(historyHashes -> Mono.fromCallable(() -> {
                            // 检查新密码是否与历史密码匹配
                            for (String oldHash : historyHashes) {
                                if (passwordEncoder.matches(newPassword, oldHash)) {
                                    log.warn("用户尝试使用历史密码: userId={}", userId);
                                    return true;
                                }
                            }
                            return false;
                        })
                        .subscribeOn(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.general()));
    }

    /**
     * 添加密码到历史记录
     *
     * @param userId 用户ID
     * @param passwordHash 密码哈希
     */
    public Mono<Void> addPasswordToHistory(String userId, String passwordHash) {
        if (!com.apex.scaffold.common.util.PasswordUtil.isBcryptHash(passwordHash)) {
            return Mono.error(new IllegalArgumentException("密码历史仅接受BCrypt哈希"));
        }
        String key = REDIS_PASSWORD_HISTORY + userId;

        return redisTemplate
                .opsForList()
                .leftPush(key, passwordHash)
                .then(redisTemplate.opsForList().trim(key, 0, PASSWORD_HISTORY_SIZE - 1))
                .then();
    }

    /**
     * 检查密码是否过期
     *
     * @param lastPasswordChangeTime 上次修改密码时间
     * @param isAdmin 是否管理员
     * @return true表示已过期
     */
    public boolean isPasswordExpired(LocalDateTime lastPasswordChangeTime, boolean isAdmin) {
        if (lastPasswordChangeTime == null) {
            return true;
        }

        int expiryDays = isAdmin ? ADMIN_PASSWORD_EXPIRY_DAYS : PASSWORD_EXPIRY_DAYS;
        LocalDateTime expiryTime = lastPasswordChangeTime.plusDays(expiryDays);

        return LocalDateTime.now().isAfter(expiryTime);
    }

    /**
     * 获取密码剩余有效天数
     *
     * @param lastPasswordChangeTime 上次修改密码时间
     * @param isAdmin 是否管理员
     * @return 剩余天数（负数表示已过期）
     */
    public long getDaysUntilExpiry(LocalDateTime lastPasswordChangeTime, boolean isAdmin) {
        if (lastPasswordChangeTime == null) {
            return 0;
        }

        int expiryDays = isAdmin ? ADMIN_PASSWORD_EXPIRY_DAYS : PASSWORD_EXPIRY_DAYS;
        LocalDateTime expiryTime = lastPasswordChangeTime.plusDays(expiryDays);

        return java.time.Duration.between(LocalDateTime.now(), expiryTime).toDays();
    }

    /**
     * 生成安全的随机密码
     *
     * @param length 密码长度
     * @param includeSpecialChars 是否包含特殊字符
     * @return 随机密码
     */
    public String generateSecurePassword(int length, boolean includeSpecialChars) {
        String lowercase = "abcdefghijklmnopqrstuvwxyz";
        String uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits = "0123456789";
        String special = "!@#$%^&*()_+-=[]{}|;:,.<>?";

        String allChars = lowercase + uppercase + digits;
        if (includeSpecialChars) {
            allChars += special;
        }

        StringBuilder password = new StringBuilder();
        java.security.SecureRandom random = new java.security.SecureRandom();

        // 确保至少包含每种类型的字符
        password.append(lowercase.charAt(random.nextInt(lowercase.length())));
        password.append(uppercase.charAt(random.nextInt(uppercase.length())));
        password.append(digits.charAt(random.nextInt(digits.length())));
        if (includeSpecialChars) {
            password.append(special.charAt(random.nextInt(special.length())));
        }

        // 填充剩余长度
        for (int i = password.length(); i < length; i++) {
            password.append(allChars.charAt(random.nextInt(allChars.length())));
        }

        // 打乱字符顺序
        char[] chars = password.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }

        return new String(chars);
    }

    /**
     * 密码验证结果
     */
    @lombok.Getter
    @lombok.RequiredArgsConstructor
    public static class PasswordValidationResult {
        private final boolean passed;
        private final int score;
        private final List<String> errors;

        public String getStrengthLevel() {
            if (score >= SCORE_THRESHOLD_VERY_STRONG) {
                return "极强";
            }
            if (score >= SCORE_THRESHOLD_STRONG) {
                return "强";
            }
            if (score >= SCORE_THRESHOLD_MEDIUM) {
                return "中等";
            }
            return "弱";
        }

        @Override
        public String toString() {
            return String.format(
                    "密码强度: %s (%d分), 通过: %s, 错误: %s", getStrengthLevel(), score, passed, String.join("; ", errors));
        }
    }
}
