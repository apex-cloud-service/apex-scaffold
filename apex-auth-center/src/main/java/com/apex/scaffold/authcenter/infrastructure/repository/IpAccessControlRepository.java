package com.apex.scaffold.authcenter.infrastructure.repository;

import com.apex.scaffold.authcenter.domain.model.IpAccessControl;
import java.time.LocalDateTime;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * IP访问控制存储库
 * @author Apex Team
 * @since 1.2.0
 */
@Repository
public interface IpAccessControlRepository extends ReactiveCrudRepository<IpAccessControl, Long> {

    /**
     * 根据租户ID和类型查询规则
     */
    Flux<IpAccessControl> findByTenantIdAndType(String tenantId, IpAccessControl.AccessControlType type);

    /**
     * 根据租户ID查询所有规则
     */
    Flux<IpAccessControl> findByTenantId(String tenantId);

    /**
     * 删除指定的IP规则
     */
    Mono<Long> deleteByIpAddressAndTenantIdAndType(
            String ipAddress, String tenantId, IpAccessControl.AccessControlType type);

    /**
     * 删除过期的规则
     */
    Mono<Long> deleteExpired(LocalDateTime now);

    /**
     * 查询启用的规则
     */
    Flux<IpAccessControl> findByEnabledTrue();
}
