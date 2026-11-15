package com.apex.scaffold.auth.infrastructure.repository;

import com.apex.scaffold.auth.domain.model.BiometricAuth;
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
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 匹配的配置流，无匹配时为空流
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Flux<BiometricAuth> findByUserIdAndTenantId(String userId, String tenantId);

    /**
     * 根据用户、类型和租户查询生物识别配置。
     *
     * @param userId 用户ID
     * @param type 生物识别类型
     * @param tenantId 租户ID
     * @return 匹配的配置，不存在时为空
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<BiometricAuth> findByUserIdAndTypeAndTenantId(
            String userId, BiometricAuth.BiometricType type, String tenantId);

    /**
     * 根据设备ID查询生物识别配置。
     *
     * @param deviceId 设备ID
     * @return 匹配的配置流，无匹配时为空流
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Flux<BiometricAuth> findByDeviceId(String deviceId);

    /**
     * 删除用户的指定类型生物识别配置。
     *
     * @param userId 用户ID
     * @param type 生物识别类型
     * @return 删除的记录数
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Long> deleteByUserIdAndType(String userId, BiometricAuth.BiometricType type);
}
