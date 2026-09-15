package com.apex.scaffold.domain.office.message;

import reactor.core.publisher.Mono;

/**
 * 消息仓储接口
 *
 * <p>定义消息持久化相关的操作规范，遵循 DDD 仓储模式。
 * 实现类位于基础设施层。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface OfficeMessageRepository {
    /**
     * 保存消息
     *
     * @param message 消息实体
     * @return 保存后的消息（包含生成的ID）
     */
    Mono<OfficeMessage> save(OfficeMessage message);
    
    /**
     * 根据ID查询消息
     *
     * @param id 消息ID
     * @return 消息实体
     */
    Mono<OfficeMessage> findById(Long id);
    
    /**
     * 根据平台消息ID查询
     *
     * @param platformMessageId 平台消息ID
     * @return 消息实体
     */
    Mono<OfficeMessage> findByPlatformMessageId(String platformMessageId);
    
    /**
     * 更新消息状态
     *
     * @param message 消息实体
     * @return 更新后的消息
     */
    Mono<OfficeMessage> updateStatus(OfficeMessage message);
}
