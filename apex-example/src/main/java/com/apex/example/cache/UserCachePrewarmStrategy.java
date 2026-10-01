package com.apex.example.cache;

import com.apex.scaffold.infrastructure.cache.prewarm.CachePrewarmStrategy;
import com.apex.scaffold.infrastructure.cache.service.MultiLevelCacheService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 用户缓存预热策略。
 *
 * <p>实现应用启动时预热用户相关缓存的策略,提高首次访问性能。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.cache.prewarm", name = "enabled", havingValue = "true")
public class UserCachePrewarmStrategy implements CachePrewarmStrategy {

    private final MultiLevelCacheService cacheService;

    @Override
    public String getName() {
        return "user-cache-prewarm";
    }

    @Override
    public Mono<Integer> prewarm() {
        log.info("开始预热用户缓存");

        return Mono.fromCallable(() -> {
            int count = 0;

            String[] hotUserIds = {"1", "2", "3", "100", "101"};

            for (String userId : hotUserIds) {
                try {
                    String cacheKey = "user:" + userId;
                    String mockUserData = "{\"id\":" + userId + ",\"name\":\"User" + userId + "\"}";

                    cacheService
                            .set(cacheKey, mockUserData, Duration.ofMinutes(30))
                            .block();

                    count++;
                } catch (Exception e) {
                    log.warn("预热用户缓存失败, userId: {}", userId, e);
                }
            }

            log.info("用户缓存预热完成，预热条目数: {}", count);
            return count;
        });
    }
}
