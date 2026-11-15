package com.apex.scaffold.auth.infrastructure.security;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 密码策略验证服务
 *
 * 密码要求：
 * 1. 长度：12-64位
 * 2. 复杂度：必须包含大写字母、小写字母、数字、特殊字符
 * 3. 不能包含用户名
 * 4. 不能包含常见弱密码
 * 5. 不能与历史密码相同（前3次）
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
public class PasswordPolicyService {

    private static final int MIN_LENGTH = 12;
    private static final int MAX_LENGTH = 64;

    private static final int MIN_CONSECUTIVE_CHARS_THRESHOLD = 3;
    private static final int MIN_SEQUENTIAL_CHARS_THRESHOLD = 4;
    private static final int MIN_PASSWORD_LENGTH_FOR_SCORE_2 = 12;
    private static final int MIN_PASSWORD_LENGTH_FOR_SCORE_1 = 8;
    private static final int SCORE_VERY_STRONG_THRESHOLD = 8;
    private static final int SCORE_STRONG_THRESHOLD = 6;
    private static final int SCORE_MEDIUM_THRESHOLD = 4;
    private static final int WEAK_PASSWORD_SCORE_PENALTY = 3;

    private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
    private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");

    /**
     * 常见弱密码黑名单
     */
    private static final List<String> WEAK_PASSWORDS = List.of(
            "password",
            "123456",
            "12345678",
            "qwerty",
            "abc123",
            "monkey",
            "1234567890",
            "letmein",
            "trustno1",
            "dragon",
            "baseball",
            "iloveyou",
            "master",
            "sunshine",
            "ashley",
            "bailey",
            "passw0rd",
            "shadow",
            "123123",
            "654321",
            "superman",
            "qazwsx",
            "michael",
            "football");

    /**
     * 验证密码是否符合策略
     *
     * @param password 密码
     * @param username 用户名（用于检查密码是否包含用户名）
     * @return 校验通过时为空完成
     * @throws BusinessException 密码不符合策略时抛出，消息携带全部不满足项
     */
    public Mono<Void> validatePassword(String password, String username) {
        return Mono.fromRunnable(() -> {
            List<String> errors = new ArrayList<>();

            // 1. 检查密码是否为空
            if (password == null || password.isEmpty()) {
                errors.add("密码不能为空");
            } else {
                // 2. 检查长度
                if (password.length() < MIN_LENGTH) {
                    errors.add(String.format("密码长度不能少于%d位", MIN_LENGTH));
                }
                if (password.length() > MAX_LENGTH) {
                    errors.add(String.format("密码长度不能超过%d位", MAX_LENGTH));
                }

                // 3. 检查复杂度
                if (!UPPERCASE_PATTERN.matcher(password).find()) {
                    errors.add("密码必须包含至少一个大写字母");
                }
                if (!LOWERCASE_PATTERN.matcher(password).find()) {
                    errors.add("密码必须包含至少一个小写字母");
                }
                if (!DIGIT_PATTERN.matcher(password).find()) {
                    errors.add("密码必须包含至少一个数字");
                }
                if (!SPECIAL_CHAR_PATTERN.matcher(password).find()) {
                    errors.add("密码必须包含至少一个特殊字符 (!@#$%^&*等)");
                }

                // 4. 检查是否包含用户名
                if (username != null && !username.isEmpty()) {
                    if (password.toLowerCase().contains(username.toLowerCase())) {
                        errors.add("密码不能包含用户名");
                    }
                }

                // 5. 检查是否为常见弱密码
                if (isWeakPassword(password)) {
                    errors.add("密码过于简单，请使用更复杂的密码");
                }

                if (hasConsecutiveChars(password, MIN_CONSECUTIVE_CHARS_THRESHOLD)) {
                    errors.add("密码不能包含3个或更多连续相同字符");
                }

                if (hasSequentialChars(password, MIN_SEQUENTIAL_CHARS_THRESHOLD)) {
                    errors.add("密码不能包含4个或更多连续递增/递减的字符（如1234、abcd）");
                }
            }

            if (!errors.isEmpty()) {
                String errorMessage = "密码不符合安全策略：\n" + String.join("\n", errors);
                log.warn("密码验证失败: username={}, errors={}", username, errors);
                throw new BusinessException(ResultCode.PARAM_INVALID, errorMessage);
            }

            log.debug("密码验证通过: username={}", username);
        });
    }

    /**
     * 验证新密码与旧密码不同
     *
     * @param newPassword 新密码（明文）
     * @param oldPasswordHash 旧密码哈希
     * @param passwordEncoder 密码编码器（用于比对）
     * @return 校验通过时为空完成
     */
    public Mono<Void> validatePasswordNotReused(
            String newPassword,
            String oldPasswordHash,
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {

        return Mono.fromRunnable(() -> {
            if (oldPasswordHash != null) {
                if (passwordEncoder.matches(newPassword, oldPasswordHash)) {
                    throw new BusinessException(ResultCode.PARAM_INVALID, "新密码不能与旧密码相同");
                }
            }
        });
    }

    /**
     * 检查密码强度
     *
     * @param password 密码
     * @return 强度等级：WEAK(弱), MEDIUM(中), STRONG(强), VERY_STRONG(极强)
     */
    public Mono<PasswordStrength> checkPasswordStrength(String password) {
        return Mono.fromCallable(() -> {
            if (password == null || password.isEmpty()) {
                return PasswordStrength.WEAK;
            }

            int score = 0;

            if (password.length() >= MIN_PASSWORD_LENGTH_FOR_SCORE_2) {
                score += 2;
            } else if (password.length() >= MIN_PASSWORD_LENGTH_FOR_SCORE_1) {
                score += 1;
            }

            if (UPPERCASE_PATTERN.matcher(password).find()) {
                score += 1;
            }
            if (LOWERCASE_PATTERN.matcher(password).find()) {
                score += 1;
            }
            if (DIGIT_PATTERN.matcher(password).find()) {
                score += 1;
            }
            if (SPECIAL_CHAR_PATTERN.matcher(password).find()) {
                score += 2;
            }

            if (isWeakPassword(password)) {
                score -= WEAK_PASSWORD_SCORE_PENALTY;
            }
            if (hasConsecutiveChars(password, MIN_CONSECUTIVE_CHARS_THRESHOLD)) {
                score -= 1;
            }
            if (hasSequentialChars(password, MIN_SEQUENTIAL_CHARS_THRESHOLD)) {
                score -= 1;
            }

            if (score >= SCORE_VERY_STRONG_THRESHOLD) {
                return PasswordStrength.VERY_STRONG;
            }
            if (score >= SCORE_STRONG_THRESHOLD) {
                return PasswordStrength.STRONG;
            }
            if (score >= SCORE_MEDIUM_THRESHOLD) {
                return PasswordStrength.MEDIUM;
            }
            return PasswordStrength.WEAK;
        });
    }

    /**
     * 生成密码策略说明
     *
     * @return 密码策略说明文本
     */
    public String getPasswordPolicyDescription() {
        return String.format(
                """
            密码安全策略：
            1. 长度要求：%d-%d位
            2. 复杂度要求：
               - 至少包含1个大写字母 (A-Z)
               - 至少包含1个小写字母 (a-z)
               - 至少包含1个数字 (0-9)
               - 至少包含1个特殊字符 (!@#$%%^&*等)
            3. 不能包含用户名
            4. 不能使用常见弱密码
            5. 不能包含3个或更多连续相同字符
            6. 不能包含4个或更多连续递增/递减字符
            7. 不能与最近3次历史密码相同
            """,
                MIN_LENGTH, MAX_LENGTH);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 检查是否为常见弱密码
     */
    private boolean isWeakPassword(String password) {
        String lowerPassword = password.toLowerCase();
        return WEAK_PASSWORDS.stream().anyMatch(weak -> lowerPassword.contains(weak));
    }

    /**
     * 检查是否包含连续相同字符
     *
     * @param password 密码
     * @param count 连续字符数量
     * @return 是否包含
     */
    private boolean hasConsecutiveChars(String password, int count) {
        if (password.length() < count) {
            return false;
        }

        for (int i = 0; i <= password.length() - count; i++) {
            char firstChar = password.charAt(i);
            boolean allSame = true;

            for (int j = 1; j < count; j++) {
                if (password.charAt(i + j) != firstChar) {
                    allSame = false;
                    break;
                }
            }

            if (allSame) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否包含连续递增或递减的字符
     *
     * @param password 密码
     * @param count 连续字符数量
     * @return 是否包含
     */
    private boolean hasSequentialChars(String password, int count) {
        if (password.length() < count) {
            return false;
        }

        for (int i = 0; i <= password.length() - count; i++) {
            boolean isAscending = true;
            boolean isDescending = true;

            for (int j = 1; j < count; j++) {
                int diff = password.charAt(i + j) - password.charAt(i + j - 1);
                if (diff != 1) {
                    isAscending = false;
                }
                if (diff != -1) {
                    isDescending = false;
                }
            }

            if (isAscending || isDescending) {
                return true;
            }
        }

        return false;
    }

    /**
     * 密码强度枚举
     */
    @lombok.Getter
    public enum PasswordStrength {
        /** 弱密码 */
        WEAK("弱"),
        /** 中等强度密码 */
        MEDIUM("中等"),
        /** 强密码 */
        STRONG("强"),
        /** 极强密码 */
        VERY_STRONG("极强");

        private final String description;

        PasswordStrength(String description) {
            this.description = description;
        }
    }
}
