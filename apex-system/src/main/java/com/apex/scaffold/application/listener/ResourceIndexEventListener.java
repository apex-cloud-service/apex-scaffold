package com.apex.scaffold.application.listener;

import com.apex.scaffold.application.event.ResourceIndexEvent;
import com.apex.scaffold.domain.search.IndexSyncPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 资源索引事件监听器
 *
 * <p>监听业务资源变更事件，自动同步搜索索引。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>异步执行（{@code @Async}），不阻塞业务流程</li>
 *   <li>失败仅记录日志，不影响业务操作</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>CREATE/UPDATE → 调用 {@link IndexSyncPort#indexResource} 创建或更新索引</li>
 *   <li>DELETE → 调用 {@link IndexSyncPort#removeIndex} 删除索引</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceIndexEventListener {

    private final IndexSyncPort indexSyncPort;

    /**
     * 处理资源索引事件
     *
     * @param event 资源索引事件
     */
    @Async
    @EventListener
    public void onResourceIndexEvent(ResourceIndexEvent event) {
        log.debug("Received resource index event: {}", event);

        try {
            switch (event.getAction()) {
                case CREATE, UPDATE -> {
                    // 创建或更新索引
                    indexSyncPort
                            .indexResource(event.getResourceType(), event.getResourceId())
                            .doOnSuccess(success -> {
                                if (Boolean.TRUE.equals(success)) {
                                    log.info(
                                            "Indexed resource: type={}, id={}, action={}",
                                            event.getResourceType(),
                                            event.getResourceId(),
                                            event.getAction());
                                } else {
                                    log.warn(
                                            "Index resource failed: type={}, id={}, action={}",
                                            event.getResourceType(),
                                            event.getResourceId(),
                                            event.getAction());
                                }
                            })
                            .doOnError(e -> log.error(
                                    "Index resource error: type={}, id={}, action={}",
                                    event.getResourceType(),
                                    event.getResourceId(),
                                    event.getAction(),
                                    e))
                            .subscribe();
                }
                case DELETE -> {
                    // 删除索引
                    indexSyncPort
                            .removeIndex(event.getResourceType(), event.getResourceId())
                            .doOnSuccess(success -> {
                                if (Boolean.TRUE.equals(success)) {
                                    log.info(
                                            "Removed index: type={}, id={}",
                                            event.getResourceType(),
                                            event.getResourceId());
                                } else {
                                    log.warn(
                                            "Remove index failed: type={}, id={}",
                                            event.getResourceType(),
                                            event.getResourceId());
                                }
                            })
                            .doOnError(e -> log.error(
                                    "Remove index error: type={}, id={}",
                                    event.getResourceType(),
                                    event.getResourceId(),
                                    e))
                            .subscribe();
                }
                default -> log.warn("Unknown action: {}", event.getAction());
            }
        } catch (Exception e) {
            // 捕获所有异常，确保不影响业务流程
            log.error("Failed to handle resource index event: {}", event, e);
        }
    }
}
