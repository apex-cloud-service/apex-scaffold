package com.apex.scaffold.infrastructure.mq.listener;

/**
 * 业务消费组的死信路由声明。
 *
 * <p>RocketMQ 消费者重试耗尽后由 Broker 投递到 {@code %DLQ% + 消费组名}，自然死信消息只保留
 * 消费组维度信息。监听器在装配阶段通过本值对象把「原始业务 Topic → 实际消费组」告知
 * {@link DeadLetterQueueProcessor}，使其在 Broker 未写入重试前 Topic 时仍能唯一反查业务 Topic。
 *
 * @param originalTopic 消费组订阅的原始业务 Topic
 * @param consumerGroup 监听器实际使用的消费组名
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see DeadLetterQueueProcessor#registerConsumerGroup(String, String)
 */
public record ConsumerGroupRegistration(String originalTopic, String consumerGroup) {}
