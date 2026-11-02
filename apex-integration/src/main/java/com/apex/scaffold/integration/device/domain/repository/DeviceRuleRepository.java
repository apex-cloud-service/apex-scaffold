package com.apex.scaffold.integration.device.domain.repository;

import com.apex.scaffold.integration.device.domain.DeviceRule;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 轻量规则仓储端口（方案 M5 / 批次 E3）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface DeviceRuleRepository {

    /**
     * 查询租户启用中的规则。
     *
     * @param tenantId 租户编码
     * @return 启用规则列表
     */
    Mono<List<DeviceRule>> findEnabled(String tenantId);

    /**
     * 按 ID 查询（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @return 规则；跨租户访问视为不存在
     */
    Mono<DeviceRule> findById(String tenantId, Long id);

    /**
     * 新增规则（租户内 ruleName 唯一）。
     *
     * @param rule 规则实体（含租户）
     * @return 落库后的规则
     */
    Mono<DeviceRule> insert(DeviceRule rule);

    /**
     * 更新启停（租户限定）。
     *
     * @param tenantId 租户编码
     * @param id       主键
     * @param enabled  是否启用
     * @return 是否更新成功（0 行视为不存在）
     */
    Mono<Boolean> updateEnabled(String tenantId, Long id, boolean enabled);
}
