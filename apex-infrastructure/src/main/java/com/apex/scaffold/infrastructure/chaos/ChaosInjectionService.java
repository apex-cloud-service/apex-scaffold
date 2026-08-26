package com.apex.scaffold.infrastructure.chaos;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * 混沌工程故障注入服务。
 *
 * <p>提供运行期故障注入能力，用于主动验证系统在 Redis 断连、数据库延迟和
 * 外部 API 部分失败等异常场景下是否仍能优雅降级。
 *
 * <h2>支持的故障类型</h2>
 * <ul>
 *   <li><strong>Redis 断连</strong>：模拟 Redis 不可达，验证缓存降级到数据库回源</li>
 *   <li><strong>数据库延迟</strong>：在 SQL 执行前注入可配置的延迟，验证慢查询降级</li>
 *   <li><strong>外部 API 失败</strong>：按比例注入外部调用失败，验证熔断器和重试</li>
 * </ul>
 *
 * <h2>安全机制</h2>
 * <ul>
 *   <li>所有注入默认关闭，需通过配置或 API 显式开启</li>
 *   <li>生产环境（prod profile）禁止开启注入：配置驱动激活和手动触发均被强制拒绝</li>
 *   <li>Redis 断连注入有自动恢复时间，避免永久故障</li>
 *   <li>提供 {@link #emergencyStop()} 方法一键关闭所有注入</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChaosInjectionProperties
 */
@Slf4j
@Service
public class ChaosInjectionService {

    /** Redis 断连活跃标记 */
    private final AtomicBoolean redisDisconnectActive = new AtomicBoolean(false);

    /** Redis 断连恢复时间戳（毫秒） */
    private final AtomicLong redisDisconnectRecoverAt = new AtomicLong(0);

    /** 数据库延迟活跃标记 */
    private final AtomicBoolean databaseLatencyActive = new AtomicBoolean(false);

    /** 外部 API 失败率注入活跃标记 */
    private final AtomicBoolean remoteApiFailureActive = new AtomicBoolean(false);

    /** 外部 API 请求计数器，用于按比例计算失败 */
    private final AtomicLong remoteApiRequestCounter = new AtomicLong(0);

    /** 混沌注入配置属性 */
    private final ChaosInjectionProperties properties;

    /** Spring 环境信息，用于检测运行 profile 以拒绝生产环境激活 */
    private final Environment environment;

    /** 生产环境 profile，该环境禁止开启混沌注入 */
    private static final Profiles PRODUCTION_PROFILE = Profiles.of("prod");

    /**
     * 构造混沌工程故障注入服务。
     *
     * @param properties 混沌注入配置属性
     * @param environment Spring 环境信息，用于生产环境护栏检测
     */
    public ChaosInjectionService(ChaosInjectionProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        log.info("混沌工程注入服务初始化, 全局开关: {}", properties.isEnabled());
    }

    /**
     * 按配置自动激活故障注入。
     *
     * <p>仅当全局开关 {@code apex.chaos.enabled} 开启时生效：根据各子开关配置，
     * 自动触发对应的故障注入，实现"仅配置触发"的混沌演练激活方式。
     * 激活前先执行生产环境安全护栏，禁止在 prod profile 下开启注入。
     */
    @PostConstruct
    public void activateByConfig() {
        if (!properties.isEnabled()) {
            log.info("[CHAOS] 全局开关未开启，跳过配置驱动激活");
            return;
        }
        ensureNotProduction();
        if (properties.getRedis().isDisconnectEnabled()) {
            triggerRedisDisconnect();
        }
        if (properties.getDatabase().isLatencyEnabled()) {
            triggerDatabaseLatency();
        }
        if (properties.getRemoteApi().isFailureRateEnabled()) {
            triggerRemoteApiFailure();
        }
        log.info("[CHAOS] 配置驱动激活完成: {}", getStatusSummary());
    }

    /**
     * 生产环境安全护栏。
     *
     * <p>检测到当前运行在生产环境（prod profile）时，拒绝开启混沌注入，
     * 抛出异常使应用启动失败，防止环境变量误传到生产导致注入生效。
     */
    private void ensureNotProduction() {
        if (environment.acceptsProfiles(PRODUCTION_PROFILE)) {
            log.error("[CHAOS] 检测到生产环境（prod），禁止开启混沌注入，拒绝启动");
            throw new IllegalStateException("[CHAOS] 生产环境禁止开启混沌注入，请移除 APEX_CHAOS_ENABLED 环境变量后重启");
        }
    }

    /**
     * 判断当前是否处于 Redis 断连注入状态。
     *
     * <p>如果 Redis 断连注入已开启且未超过持续时间，返回 true。
     * 如果已超过持续时间，自动恢复并返回 false。
     *
     * @return true 表示当前 Redis 应被视为不可达
     */
    public boolean isRedisDisconnectActive() {
        if (!properties.isEnabled() || !properties.getRedis().isDisconnectEnabled()) {
            return false;
        }
        if (!redisDisconnectActive.get()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long recoverAt = redisDisconnectRecoverAt.get();
        if (now >= recoverAt) {
            log.warn("[CHAOS] Redis 断连注入已超过持续时间，自动恢复");
            redisDisconnectActive.set(false);
            return false;
        }
        return true;
    }

    /**
     * 手动触发 Redis 断连注入。
     *
     * <p>根据配置的持续时间自动恢复。如果已在活跃状态则刷新持续时间。
     *
     * @return true 表示触发成功
     */
    public boolean triggerRedisDisconnect() {
        if (!properties.isEnabled()) {
            log.warn("[CHAOS] 混沌注入全局开关未开启，无法触发 Redis 断连");
            return false;
        }
        ensureNotProduction();
        int durationSec = properties.getRedis().getDisconnectDurationSeconds();
        long recoverAt = System.currentTimeMillis() + (durationSec * 1000L);
        redisDisconnectRecoverAt.set(recoverAt);
        redisDisconnectActive.set(true);
        log.warn("[CHAOS] Redis 断连注入已触发, 持续 {} 秒, 预计恢复时间: {}", durationSec, recoverAt);
        return true;
    }

    /**
     * 判断当前是否需要注入数据库延迟。
     *
     * @return true 表示数据库操作前应注入延迟
     */
    public boolean isDatabaseLatencyActive() {
        return properties.isEnabled() && properties.getDatabase().isLatencyEnabled() && databaseLatencyActive.get();
    }

    /**
     * 获取配置的数据库延迟时间。
     *
     * @return 延迟毫秒数
     */
    public long getDatabaseLatencyMillis() {
        return properties.getDatabase().getLatencyMillis();
    }

    /**
     * 手动触发数据库延迟注入。
     *
     * @return true 表示触发成功
     */
    public boolean triggerDatabaseLatency() {
        if (!properties.isEnabled()) {
            log.warn("[CHAOS] 混沌注入全局开关未开启，无法触发数据库延迟");
            return false;
        }
        ensureNotProduction();
        databaseLatencyActive.set(true);
        log.warn("[CHAOS] 数据库延迟注入已触发, 延迟: {}ms", properties.getDatabase().getLatencyMillis());
        return true;
    }

    /**
     * 判断当前外部 API 调用是否应被注入失败。
     *
     * <p>基于配置的失败率，使用计数器取模方式近似按比例注入失败。
     *
     * @return true 表示本次外部 API 调用应模拟失败
     */
    public boolean shouldRemoteApiFail() {
        if (!properties.isEnabled() || !properties.getRemoteApi().isFailureRateEnabled()) {
            return false;
        }
        if (!remoteApiFailureActive.get()) {
            return false;
        }
        long count = remoteApiRequestCounter.incrementAndGet();
        double rate = properties.getRemoteApi().getFailureRate();
        long period = Math.max(1, Math.round(1.0 / rate));
        boolean shouldFail = (count % period == 0);
        if (shouldFail) {
            log.warn("[CHAOS] 外部 API 调用注入失败, count: {}, rate: {}", count, rate);
        }
        return shouldFail;
    }

    /**
     * 手动触发外部 API 失败率注入。
     *
     * @return true 表示触发成功
     */
    public boolean triggerRemoteApiFailure() {
        if (!properties.isEnabled()) {
            log.warn("[CHAOS] 混沌注入全局开关未开启，无法触发外部 API 失败");
            return false;
        }
        ensureNotProduction();
        remoteApiFailureActive.set(true);
        log.warn("[CHAOS] 外部 API 失败率注入已触发, rate: {}", properties.getRemoteApi().getFailureRate());
        return true;
    }

    /**
     * 一键紧急停止所有故障注入。
     *
     * <p>立即关闭所有活跃的注入状态，用于紧急恢复场景。
     */
    public void emergencyStop() {
        redisDisconnectActive.set(false);
        databaseLatencyActive.set(false);
        remoteApiFailureActive.set(false);
        remoteApiRequestCounter.set(0);
        log.info("[CHAOS] 停止：所有故障注入已关闭");
    }

    /**
     * 获取当前混沌注入状态摘要。
     *
     * @return 状态摘要字符串
     */
    public String getStatusSummary() {
        return String.format(
                "[CHAOS] 全局: %b, Redis断连: %b, DB延迟: %b, API失败: %b, API计数: %d",
                properties.isEnabled(),
                isRedisDisconnectActive(),
                isDatabaseLatencyActive(),
                remoteApiFailureActive.get(),
                remoteApiRequestCounter.get());
    }

    /**
     * 应用关闭时清理所有注入状态。
     */
    @PreDestroy
    public void shutdown() {
        emergencyStop();
        log.info("[CHAOS] 混沌工程注入服务已关闭");
    }
}
