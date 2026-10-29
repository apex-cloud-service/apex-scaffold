package com.apex.scaffold.application.service;

import java.time.Instant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * OAuth2 客户端密钥轮换应用服务接口。
 *
 * <p>定义 OAuth2 客户端机密（client secret）全生命周期管理的用例契约，
 * 包括密钥轮换（支持自定义有效期）、密钥校验（支持旧密钥宽限期）、
 * 过期与临期客户端筛查、轮换计划编排以及临期通知。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public interface OAuth2ClientSecretRotationService {

    /**
     * 轮换客户端密钥。
     *
     * @param clientId 客户端ID
     * @param operatorUserId 操作人用户ID
     * @return 轮换结果（新密钥）
     */
    Mono<String> rotateClientSecret(String clientId, Long operatorUserId);

    /**
     * 使用自定义过期时间轮换客户端密钥。
     *
     * @param clientId 客户端ID
     * @param expiresAt 过期时间
     * @param operatorUserId 操作人用户ID
     * @return 轮换结果（新密钥）
     */
    Mono<String> rotateClientSecretWithCustomExpiry(String clientId, Instant expiresAt, Long operatorUserId);

    /**
     * 验证客户端密钥。
     *
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @return 验证结果（true表示有效）
     */
    Mono<Boolean> validateClientSecret(String clientId, String clientSecret);

    /**
     * 查找密钥已过期的客户端。
     *
     * @return 密钥已过期的客户端 ID 流
     */
    Flux<String> findClientsWithExpiredSecrets();

    /**
     * 查找密钥即将过期的客户端。
     *
     * <p>临期阈值由客户端实体的密钥过期策略决定，不通过本方法参数传入。
     *
     * @return 密钥即将过期的客户端 ID 流
     */
    Flux<String> findClientsWithExpiringSoonSecrets();

    /**
     * 调度密钥轮换。
     *
     * @param clientId 客户端ID
     * @param scheduledTime 调度时间
     */
    Mono<Void> scheduleSecretRotation(String clientId, Instant scheduledTime);

    /**
     *
     *
     * 通知客户端密钥即将过期。
     *
     *
     *
     *
     *
     * @param clientId 客户端ID
     */
    Mono<Void> notifyClientSecretExpiring(String clientId);
}
