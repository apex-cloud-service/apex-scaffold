package com.apex.scaffold.integration.connector.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.integration.connector.domain.repository.WebhookDedupRepository;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.WebhookDedupMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.WebhookDedupPO;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link WebhookDedupRepository} 的 MyBatis-Plus 实现（方案 M5 / 批次 E2）。
 *
 * <p>唯一键 {@code (tenant_id, dedup_key)} 冲突即重复投递，返回 false 供幂等成功响应。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class WebhookDedupRepositoryImpl implements WebhookDedupRepository {

    private final WebhookDedupMapper mapper;

    @Override
    public Mono<Boolean> tryInsert(String tenantId, String dedupKey, LocalDateTime expireAt) {
        return Mono.fromCallable(() -> {
                    WebhookDedupPO po = new WebhookDedupPO();
                    po.setTenantId(tenantId);
                    po.setDedupKey(dedupKey);
                    po.setExpireAt(expireAt);
                    try {
                        mapper.insert(po);
                        return true;
                    } catch (DuplicateKeyException duplicate) {
                        return false;
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .onErrorResume(e -> {
                    // 去重登记技术性失败时放行为首次投递，由下游事件消费方幂等兜底
                    log.warn("webhook 去重登记失败，按首次投递处理: tenant={}, key={}", tenantId, dedupKey, e);
                    return Mono.just(true);
                });
    }
}
