package com.apex.scaffold.authcenter.infrastructure.repository;

import com.apex.scaffold.authcenter.domain.model.IpAccessControl;
import java.time.LocalDateTime;
import org.springframework.data.r2dbc.repository.Query;
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
     * @param tenantId TODO
     * @param type TODO
     * @return TODO
     */
    Flux<IpAccessControl> findByTenantIdAndType(String tenantId, IpAccessControl.AccessControlType type);

    /**
     * 根据租户ID查询所有规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param tenantId TODO
     * @return TODO
     */
    Flux<IpAccessControl> findByTenantId(String tenantId);

    /**
     * 删除指定IP和租户的访问控制规则
     *
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @param type 控制类型
     * @return 删除的记录数量
     */
    Mono<Long> deleteByIpAddressAndTenantIdAndType(
            String ipAddress, String tenantId, IpAccessControl.AccessControlType type);

    /**
     * 删除过期的规则（方法名无法派生查询，使用显式SQL）
     */
    @Query("DELETE FROM ip_access_control WHERE expires_at < :now")
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param now TODO
     * @return TODO
     */
    Mono<Long> deleteExpired(LocalDateTime now);

    /**
     * 查询启用的规则
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    Flux<IpAccessControl> findByEnabledTrue();
}
