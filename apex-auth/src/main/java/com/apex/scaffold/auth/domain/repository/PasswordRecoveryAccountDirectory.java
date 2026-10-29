package com.apex.scaffold.auth.domain.repository;

import reactor.core.publisher.Mono;

/**
 * 密码找回账号目录端口（服务端可信入口）。
 *
 * <p>按已验证联系方式跨租户解析目标账号。解析结果只允许为唯一命中 / 无命中 / 多命中三种；
 * 命中多个账号时不向调用方暴露任何账号明细，该场景落受审计的人工恢复。
 * 密码重设同样经由本端口以显式 userId + tenantId 条件执行，客户端无法选择或影响目标租户。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
public interface PasswordRecoveryAccountDirectory {

    Mono<Resolution> resolveByEmail(String email);

    Mono<Resolution> resolveByPhone(String phone);

    /** 以显式 userId + tenantId 条件更新密码哈希，返回是否更新成功。 */
    Mono<Boolean> updatePasswordHash(Long userId, String tenantId, String passwordHash);

    /**
     * 账号解析结果。
     */
    record Resolution(String kind, Long userId, String tenantId, String username) {

        public static final String KIND_NONE = "NONE";

        public static final String KIND_UNIQUE = "UNIQUE";

        public static final String KIND_MULTIPLE = "MULTIPLE";

        public static Resolution none() {
            return new Resolution(KIND_NONE, null, null, null);
        }

        public static Resolution multiple() {
            return new Resolution(KIND_MULTIPLE, null, null, null);
        }

        public static Resolution unique(Long userId, String tenantId, String username) {
            return new Resolution(KIND_UNIQUE, userId, tenantId, username);
        }
    }
}
