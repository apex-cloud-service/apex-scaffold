package com.apex.scaffold.auth.domain.repository;

import com.apex.scaffold.auth.domain.model.entity.AuditLogEntity;
import java.time.LocalDateTime;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 审计日志仓储接口
 *
 * <p>定义审计日志实体的持久化操作，遵循DDD仓储模式。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
public interface AuditLogRepository {

    /**
     * 保存审计日志
     *
     * @param auditLog 审计日志实体
     * @return 保存后的审计日志实体
     */
    Mono<AuditLogEntity> save(AuditLogEntity auditLog);

    /**
     * 根据ID查找审计日志
     *
     * @param id 审计日志ID
     * @return 审计日志实体，如果不存在返回空
     */
    Mono<AuditLogEntity> findById(Long id);

    /**
     * 根据用户ID查询审计日志
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> findByUserId(Long userId, String tenantId);

    /**
     * 根据事件类型查询审计日志
     *
     * @param eventType 事件类型
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> findByEventType(String eventType, String tenantId);

    /**
     * 根据时间范围查询审计日志
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> findByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId);

    /**
     * 根据用户ID和事件类型查询审计日志
     *
     * @param userId 用户ID
     * @param eventType 事件类型
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> findByUserIdAndEventType(Long userId, String eventType, String tenantId);

    /**
     * 查询失败的审计日志
     *
     * @param tenantId 租户ID
     * @return 失败的审计日志实体流
     */
    Flux<AuditLogEntity> findFailedEvents(String tenantId);

    /**
     * 查询高风险审计日志
     *
     * @param tenantId 租户ID
     * @return 高风险的审计日志实体流
     */
    Flux<AuditLogEntity> findHighRiskEvents(String tenantId);

    /**
     * 根据IP地址查询审计日志
     *
     * @param ipAddress IP地址
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> findByIpAddress(String ipAddress, String tenantId);

    /**
     * 查询指定时间之前的审计日志
     *
     * @param beforeTime 截止时间
     * @param tenantId 租户ID
     * @return 审计日志实体流
     */
    Flux<AuditLogEntity> findByCreatedAtBefore(LocalDateTime beforeTime, String tenantId);

    /**
     * 删除指定时间之前的审计日志
     *
     * @param beforeTime 截止时间
     * @param tenantId 租户ID
     * @return 删除的日志数量
     */
    Mono<Long> deleteByCreatedAtBefore(LocalDateTime beforeTime, String tenantId);

    /**
     * 统计指定时间范围内的审计日志数量
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param tenantId 租户ID
     * @return 日志数量
     */
    Mono<Long> countByTimeRange(LocalDateTime startTime, LocalDateTime endTime, String tenantId);
}
