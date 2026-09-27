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
     * 根据用户ID查询所有生物识别配置
     */
    Flux<BiometricAuth> findByUserId(String userId);

    /**
     * 根据用户ID和类型查询生物识别配置
     */
    Mono<BiometricAuth> findByUserIdAndType(String userId, BiometricAuth.BiometricType type);

    /**
     * 根据设备ID查询生物识别配置
     */
    Flux<BiometricAuth> findByDeviceId(String deviceId);

    /**
     * 删除用户的指定类型生物识别配置
     */
    Mono<Long> deleteByUserIdAndType(String userId, BiometricAuth.BiometricType type);
}
