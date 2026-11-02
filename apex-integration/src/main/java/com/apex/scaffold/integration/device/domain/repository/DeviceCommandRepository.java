package com.apex.scaffold.integration.device.domain.repository;

import com.apex.scaffold.integration.device.domain.DeviceCommand;
import reactor.core.publisher.Mono;

/**
 * 设备下行命令仓储端口（方案 M5 / 批次 E3）。
 *
 * <p>状态机迁移以条件更新实现（仅从预期状态迁出），0 行更新视为状态已推进或
 * 不存在，由调用方按语义处理。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface DeviceCommandRepository {

    /**
     * 按业务命令 ID 查询（租户限定）。
     *
     * @param tenantId  租户编码
     * @param commandId 业务命令 ID
     * @return 命令；不存在为空
     */
    Mono<DeviceCommand> findByCommandId(String tenantId, String commandId);

    /**
     * 新增命令（PENDING；租户内 commandId 唯一）。
     *
     * @param command 命令实体（含租户）
     * @return 落库后的命令
     */
    Mono<DeviceCommand> insert(DeviceCommand command);

    /**
     * PENDING → SENT（幂等条件更新）。
     *
     * @param tenantId  租户编码
     * @param commandId 业务命令 ID
     * @return 是否发生迁移
     */
    Mono<Boolean> markSent(String tenantId, String commandId);

    /**
     * PENDING/SENT → ACK（幂等条件更新）。
     *
     * @param tenantId  租户编码
     * @param commandId 业务命令 ID
     * @return 是否发生迁移
     */
    Mono<Boolean> markAcked(String tenantId, String commandId);

    /**
     * 将超时截止时间已过且仍处于给定状态的命令置为 TIMEOUT。
     *
     * @param command 待超时命令（须携带租户与业务命令 ID）
     * @return 是否发生迁移
     */
    Mono<Boolean> markTimeout(DeviceCommand command);

    /**
     * 扫描超时未确认命令（PENDING/SENT 且 timeout_at 已过）。
     *
     * @param limit 单批上限
     * @return 超时命令列表（跨租户，含租户归属）
     */
    reactor.core.publisher.Flux<DeviceCommand> findDueTimeout(int limit);
}
