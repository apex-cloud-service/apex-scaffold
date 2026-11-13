package com.apex.scaffold.office.application.port;

import reactor.core.publisher.Mono;

/**
 * 办公模块站内信统计查询出站端口
 *
 * <p>为集群内只读统计端点（{@code GET /internal/v1/office/messages/stats}）提供租户维度
 * 站内信总量查询。内部调用无租户上下文，租户条件由调用方显式传入并已校验，
 * 由基础设施适配层以显式 SQL 实现，不依赖租户行级拦截器自动改写。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>端口位于应用层，接口层不得直接依赖基础设施统计实现</li>
 *   <li>统计口径固定为排除逻辑删除行、包含全部发送状态</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface OfficeMessageStatsQueryPort {

    /**
     * 统计指定租户的站内信总量
     *
     * @param tenantId 租户标识（调用方保证非空且已校验）
     * @return 消息总量
     */
    Mono<Long> countMessages(String tenantId);
}
