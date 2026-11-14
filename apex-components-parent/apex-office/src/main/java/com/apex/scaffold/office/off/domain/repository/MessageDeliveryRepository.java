package com.apex.scaffold.office.off.domain.repository;

import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 站内信原子写入端口。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface MessageDeliveryRepository {
    /**
     * 在同一同步事务中写入消息及接收人。
     * @param message 消息
     * @param recipients 接收用户
     * @return 事务提交后的消息
     */
    Mono<OffMessageEntity> save(OffMessageEntity message, List<Long> recipients);
}
