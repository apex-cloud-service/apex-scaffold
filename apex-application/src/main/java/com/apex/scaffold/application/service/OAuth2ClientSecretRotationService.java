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
public interface OAuth2ClientSecretRotationService {

    Mono<String> rotateClientSecret(String clientId, Long operatorUserId);

    Mono<String> rotateClientSecretWithCustomExpiry(String clientId, Instant expiresAt, Long operatorUserId);

    Mono<Boolean> validateClientSecret(String clientId, String clientSecret);

    Flux<String> findClientsWithExpiredSecrets();

    Flux<String> findClientsWithExpiringSoonSecrets();

    Mono<Void> scheduleSecretRotation(String clientId, Instant scheduledTime);

    Mono<Void> notifyClientSecretExpiring(String clientId);
}
