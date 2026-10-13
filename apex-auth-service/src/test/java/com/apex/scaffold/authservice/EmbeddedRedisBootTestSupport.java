package com.apex.scaffold.authservice;

import java.io.IOException;
import java.net.ServerSocket;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

/**
 * 认证中心启动/集成测试的嵌入式 Redis 基类。
 *
 * <p>微服务迁移 Phase 2.1 之后，认证中心 Token 签名升级为 RS256，签名密钥由
 * {@code JwtKeyRotationService} 统一存放于 Redis；登录链路
 * （{@code login → TokenService.issueToken → JwtKeyMaterialService.sign →
 * JwtKeyRotationService.getCurrentPrivateKey}）强依赖 Redis 中的当前密钥。
 * 因此 {@code basic} profile 下的启动/集成测试必须提供真实可用的 Redis 连接，
 * 否则签发将因“JWT密钥未初始化”而失败，表现为登录接口返回 500。</p>
 *
 * <p>本基类复用 {@code DualServiceAuthorizationIntegrationTest} 已验证的
 * embedded-redis（com.github.codemonstur）模式：</p>
 * <ul>
 *   <li>通过 {@link DynamicPropertySource} 在 Spring 上下文创建前启动嵌入式
 *       Redis，并将其地址写入 {@code spring.data.redis.host/port}，保证
 *       Lettuce 连接工厂指向可用实例；</li>
 *   <li>Redis 进程在所有继承该基类的测试类间共享（JVM 级静态单例），并注册
 *       JVM 关闭钩子确保退出时回收；</li>
 *   <li>密钥初始化由生产侧 {@code JwtKeyBootstrapRunner} 在上下文启动时完成，
 *       测试无需重复初始化，从而与生产链路保持一致。</li>
 * </ul>
 *
 * @author Apex Team
 * @since 2026-10-08
 */
public abstract class EmbeddedRedisBootTestSupport {

    /**
     * 全局共享的嵌入式 Redis 实例（整个测试 JVM 生命周期内复用）。
     */
    private static RedisServer redisServer;

    /**
     * 嵌入式 Redis 监听端口（随机可用端口）。
     */
    private static int redisPort;

    /**
     * 启动嵌入式 Redis 并将连接地址注入 Spring 环境。
     *
     * <p>该方法由 Spring 在上下文创建前调用（{@link DynamicPropertySource} 语义），
     * 在此处完成 Redis 进程的启动与端口绑定，可确保时序正确。</p>
     *
     * @param registry 动态属性注册表
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        int port = startRedis();
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> port);
        registry.add("spring.data.redis.timeout", () -> "3s");
        registry.add("spring.data.redis.connect-timeout", () -> "3s");
    }

    /**
     * 惰性启动嵌入式 Redis（幂等，多测试类共享同一实例）。
     *
     * @return 嵌入式 Redis 监听端口
     */
    private static synchronized int startRedis() {
        if (redisServer != null && redisServer.isActive()) {
            return redisPort;
        }
        try {
            try (ServerSocket socket = new ServerSocket(0)) {
                redisPort = socket.getLocalPort();
            }
            redisServer = RedisServer.newRedisServer()
                    .bind("127.0.0.1")
                    .port(redisPort)
                    .setting("maxmemory 64mb")
                    .build();
            redisServer.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (redisServer != null && redisServer.isActive()) {
                        redisServer.stop();
                    }
                } catch (IOException ignored) {
                    // JVM 关闭阶段，忽略停止异常
                }
            }));
        } catch (IOException exception) {
            throw new IllegalStateException("启动嵌入式 Redis 失败", exception);
        }
        return redisPort;
    }

    /**
     * 防止独立实例化（仅作为测试基类使用）。
     */
    protected EmbeddedRedisBootTestSupport() {}
}
