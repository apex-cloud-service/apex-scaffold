package com.apex.scaffold.authcenter.infrastructure.repository;

import com.apex.scaffold.authcenter.domain.model.BiometricAuth;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 生物识别认证存储库
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
public interface BiometricAuthRepository extends ReactiveCrudRepository<BiometricAuth, Long> {

    /**
     * 根据用户和租户查询所有生物识别配置。
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Flux<BiometricAuth> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * 根据用户、类型和租户查询生物识别配置。
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<BiometricAuth> findByUserIdAndTypeAndTenantId(
            String userId, BiometricAuth.BiometricType type, String tenantId);

    /**
     * 根据设备ID查询生物识别配置。
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Flux<BiometricAuth> findByDeviceId(String deviceId);

    /**
     * 删除用户的指定类型生物识别配置。
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Long> deleteByUserIdAndType(String userId, BiometricAuth.BiometricType type);
}
