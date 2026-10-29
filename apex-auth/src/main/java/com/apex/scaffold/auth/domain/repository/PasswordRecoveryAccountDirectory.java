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

    /**
     * 按邮箱摘要跨租户解析目标账号，多命中时不返回账号明细。
     *
     * @param email 规范化邮箱
     * @return 唯一命中 / 无命中 / 多命中三种解析结果之一
     */
    Mono<Resolution> resolveByEmail(String email);

    /**
     * 按手机号摘要跨租户解析目标账号，多命中时不返回账号明细。
     *
     * @param phone 规范化手机号
     * @return 唯一命中 / 无命中 / 多命中三种解析结果之一
     */
    Mono<Resolution> resolveByPhone(String phone);

    /** 以显式 userId + tenantId 条件更新密码哈希，返回是否更新成功。 */
    Mono<Boolean> updatePasswordHash(Long userId, String tenantId, String passwordHash);

    /**
     * 账号解析结果。
     *
     * <p>{@code kind} 取值为 {@link #KIND_NONE}、{@link #KIND_UNIQUE}、{@link #KIND_MULTIPLE} 之一；
     * 仅 {@code KIND_UNIQUE} 携带账号明细，其余两种场景下 {@code userId}、{@code tenantId}、
     * {@code username} 均为 {@code null}，避免向调用方泄露候选账号。
     *
     * @param kind 解析结果类别
     * @param userId 目标账号用户标识，仅唯一命中时非空
     * @param tenantId 目标账号所属租户，仅唯一命中时非空
     * @param username 目标账号用户名，仅唯一命中时非空
     */
    record Resolution(String kind, Long userId, String tenantId, String username) {

        /** 无命中：没有任何账号绑定该联系方式。 */
        public static final String KIND_NONE = "NONE";

        /** 唯一命中：可安全继续密码找回流程。 */
        public static final String KIND_UNIQUE = "UNIQUE";

        /** 多命中：同一联系方式关联多个账号，转人工恢复。 */
        public static final String KIND_MULTIPLE = "MULTIPLE";

        /**
         * 构造无命中结果。
         *
         * @return 无命中解析结果
         */
        public static Resolution none() {
            return new Resolution(KIND_NONE, null, null, null);
        }

        /**
         * 构造多命中结果。
         *
         * @return 多命中解析结果
         */
        public static Resolution multiple() {
            return new Resolution(KIND_MULTIPLE, null, null, null);
        }

        /**
         * 构造唯一命中结果。
         *
         * @param userId 目标账号用户标识
         * @param tenantId 目标账号所属租户
         * @param username 目标账号用户名
         * @return 唯一命中解析结果
         */
        public static Resolution unique(Long userId, String tenantId, String username) {
            return new Resolution(KIND_UNIQUE, userId, tenantId, username);
        }
    }
}
