package com.apex.scaffold.auth.infrastructure.repository;

import com.apex.scaffold.auth.domain.model.IpAccessControl;
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
     *
     * @param tenantId 租户ID
     * @param type 控制类型
     * @return 访问控制规则列表
     */
    Flux<IpAccessControl> findByTenantIdAndType(String tenantId, IpAccessControl.AccessControlType type);

    /**
     * 按ID和租户ID查询访问控制规则
     *
     * @param id 规则ID
     * @param tenantId 租户ID
     * @return 访问控制规则，不存在时为空
     */
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<IpAccessControl> findByIdAndTenantId(Long id, String tenantId);

    /**
     * 按ID和租户ID删除访问控制规则
     *
     * @param id 规则ID
     * @param tenantId 租户ID
     * @return 删除的记录数量
     */
    @Query("DELETE FROM ip_access_control WHERE id = :id AND tenant_id = :tenantId")
    @SuppressWarnings("PMD.AbstractMethodOrInterfaceMethodMustUseJavadocRule")
    Mono<Long> deleteByIdAndTenantId(Long id, String tenantId);

    /**
     * 根据租户ID查询所有规则
     *
     * @param tenantId 租户ID
     * @return 访问控制规则列表
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
     *
     * @param now 当前时间
     * @return 删除的记录数量
     */
    @Query("DELETE FROM ip_access_control WHERE expires_at < :now")
    Mono<Long> deleteExpired(LocalDateTime now);

    /**
     * 查询启用的规则
     *
     * @return 已启用的访问控制规则列表
     */
    Flux<IpAccessControl> findByEnabledTrue();
}
