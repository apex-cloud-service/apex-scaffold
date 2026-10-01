package com.apex.scaffold.authcenter.domain.service;

import com.apex.scaffold.authcenter.domain.model.BiometricAuth;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 生物识别认证服务接口
 * @author Apex Team
 * @since 1.2.0
 */
public interface BiometricAuthService {

    /**
     * 注册生物识别认证
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param type 生物识别类型
     * @param deviceId 设备ID
     * @param deviceName 设备名称
     * @param publicKey 公钥（WebAuthn）
     * @return 注册结果
     */
    Mono<BiometricAuth> registerBiometric(
            String userId,
            String tenantId,
            BiometricAuth.BiometricType type,
            String deviceId,
            String deviceName,
            String publicKey);

    /**
     * 验证生物识别
     *
     * @param userId 用户ID
     * @param type 生物识别类型
     * @param challenge 挑战数据
     * @param signature 签名
     * @return 验证是否成功
     */
    Mono<Boolean> verifyBiometric(String userId, BiometricAuth.BiometricType type, String challenge, String signature);

    /**
     * 启用生物识别认证
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param id TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> enableBiometric(Long id, String operator);

    /**
     * 禁用生物识别认证
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param id TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> disableBiometric(Long id, String operator);

    /**
     * 删除生物识别认证
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param id TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> deleteBiometric(Long id, String operator);

    /**
     * 查询用户的生物识别配置
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Flux<BiometricAuth> getUserBiometrics(String userId);

    /**
     * 检查用户是否启用了生物识别
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Mono<Boolean> isBiometricEnabled(String userId);

    /**
     * 解锁生物识别
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param id TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> unlockBiometric(Long id, String operator);

    /**
     * 生成WebAuthn注册挑战
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Mono<RegistrationChallenge> createRegistrationChallenge(String userId);

    /**
     * 生成WebAuthn认证挑战
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Mono<AuthenticationChallenge> createAuthenticationChallenge(String userId);

    /**
     * WebAuthn注册挑战
     */
    record RegistrationChallenge(String challenge, String userId, long timeout) {}

    /**
     * WebAuthn认证挑战
     */
    record AuthenticationChallenge(String challenge, long timeout) {}
}
