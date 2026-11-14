package com.apex.scaffold.office.off.domain.repository;

import com.apex.scaffold.office.off.domain.entity.OffMessageDeliveryEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息渠道投递记录仓储接口。
 *
 * <p>按（消息、接收人、渠道）唯一维度管理投递记录，站内已读状态不属于本仓储职责。
 * 所有方法返回 Mono/Flux，数据库访问在 JDBC 调度器执行。</p>
 *
 * @author Apex Team
 * @since 1.3.2
 * @see OffMessageDeliveryEntity
 */
public interface OffMessageDeliveryRepository {

    /**
     * 按（消息、接收人、渠道）插入或更新投递记录。
     *
     * @param entity 投递记录实体
     * @return 持久化后的实体（含主键）
     */
    Mono<OffMessageDeliveryEntity> upsert(OffMessageDeliveryEntity entity);

    /**
     * 查询消息的全部投递记录（发送人视角）。
     *
     * @param messageId 消息 ID
     * @return 投递记录流
     */
    Flux<OffMessageDeliveryEntity> findByMessageId(Long messageId);

    /**
     * 查询指定接收人在某消息下的投递记录（接收人视角）。
     *
     * @param messageId 消息 ID
     * @param recipientId 接收人 ID
     * @return 投递记录流
     */
    Flux<OffMessageDeliveryEntity> findByMessageIdAndRecipientId(Long messageId, Long recipientId);
}
