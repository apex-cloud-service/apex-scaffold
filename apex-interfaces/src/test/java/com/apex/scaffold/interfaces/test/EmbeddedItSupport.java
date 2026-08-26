package com.apex.scaffold.interfaces.test;

import java.io.IOException;
import java.net.ServerSocket;
import redis.embedded.RedisServer;

/**
 * EmbeddedItSupport 测试支持配置。
 *
 * <p>为相关集成测试提供隔离的依赖、环境和安全配置，避免测试行为影响生产配置。</p>
 *
 * @author Apex Team
 */
final class EmbeddedItSupport {

    private static RedisServer redisServer;
    private static Integer redisPort;

    /**
     * 创建 {@code EmbeddedItSupport} 实例。
     */
    private EmbeddedItSupport() {}

    /**
     * 确保集成测试共享的嵌入式 Redis 已在随机端口启动。
     *
     * <p>方法幂等且使用类级锁保护；首次启动后同步写入测试专用系统属性。
     * 端口分配或 Redis 进程启动失败时抛出包含根因的非受检异常。</p>
     *
     * @return 嵌入式 Redis 实际监听端口
     */
    static synchronized int ensureRedisStarted() {
        if (redisServer != null && redisServer.isActive()) {
            return redisPort;
        }

        redisPort = findFreePort();
        try {
            redisServer = new RedisServer(redisPort);
            redisServer.start();
        } catch (IOException startupError) {
            redisServer = null;
            redisPort = null;
            throw new IllegalStateException("Failed to start embedded redis", startupError);
        }

        System.setProperty("REDIS_HOST", "127.0.0.1");
        System.setProperty("REDIS_PORT", String.valueOf(redisPort));
        System.setProperty("REDIS_DATABASE", "8");

        return redisPort;
    }

    /**
     * 停止集成测试 Redis 并清除进程内端口状态。
     *
     * <p>未启动时方法直接返回；停止失败时抛出非受检异常，
     * 使测试基础设施泄漏不会被静默忽略。</p>
     */
    static synchronized void stopRedis() {
        if (redisServer != null) {
            try {
                redisServer.stop();
            } catch (IOException shutdownError) {
                throw new IllegalStateException("Failed to stop embedded redis", shutdownError);
            } finally {
                redisServer = null;
                redisPort = null;
            }
        }
    }

    /**
     * 由操作系统分配一个当前可用的本地 TCP 端口。
     *
     * @return 可供嵌入式 Redis 监听的端口号
     */
    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to find free port for embedded redis", e);
        }
    }
}
