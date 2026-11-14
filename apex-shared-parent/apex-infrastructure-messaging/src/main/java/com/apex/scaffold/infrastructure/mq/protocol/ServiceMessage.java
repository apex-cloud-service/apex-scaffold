package com.apex.scaffold.infrastructure.mq.protocol;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 服务间消息协议
 *
 * <p>定义微服务架构下服务间异步通信的标准消息格式。
 * 支持服务发现、路由、追踪和多租户隔离等企业级特性。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>messageId: 全局唯一的消息标识,用于幂等性和链路追踪</li>
 *   <li>sourceService: 消息来源服务名称</li>
 *   <li>targetService: 消息目标服务名称(可选,支持广播)</li>
 *   <li>eventType: 事件类型,如ORDER_CREATED、USER_REGISTERED</li>
 *   <li>tenantId: 租户标识,实现多租户数据隔离</li>
 *   <li>timestamp: 消息创建时间戳(毫秒)</li>
 *   <li>payload: 业务数据载荷,泛型支持任意类型</li>
 *   <li>headers: 扩展元数据,用于传递追踪ID、版本号等信息</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>订单服务通知库存服务扣减库存</li>
 *   <li>用户服务通知消息服务发送欢迎邮件</li>
 *   <li>支付服务通知订单服务更新支付状态</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>点对点: 指定targetService,消息发送到特定服务</li>
 *   <li>发布订阅: targetService为空,所有订阅该eventType的服务都能收到</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * {@code
 * // 构建订单创建事件消息
 * ServiceMessage<OrderCreatedEvent> message = ServiceMessage.<OrderCreatedEvent>builder()
 *     .messageId(messageIdGenerator.nextId())
 *     .sourceService("order-service")
 *     .targetService("inventory-service")
 *     .eventType("ORDER_CREATED")
 *     .tenantId(TenantContext.currentTenantId().orElse("default"))
 *     .timestamp(System.currentTimeMillis())
 *     .payload(orderCreatedEvent)
 *     .headers(Map.of("traceId", traceId, "version", "v1"))
 *     .build();
 *
 * // 发送消息
 * mqProducerService.sendSyncWithOutbox(
 *     MqConstant.TOPIC_SERVICE_EVENT,
 *     "ORDER_CREATED",
 *     message
 * );
 * }
 * </pre>
 *
 * @param <T> 消息载荷的泛型类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.infrastructure.mq.service.MqProducerService
 * @see com.apex.scaffold.infrastructure.mq.service.MqConsumerService
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceMessage<T> {

    private String messageId;

    private String sourceService;

    private String targetService;

    private String eventType;

    private String tenantId;

    private Long timestamp;

    private T payload;

    private Map<String, String> headers;
}
