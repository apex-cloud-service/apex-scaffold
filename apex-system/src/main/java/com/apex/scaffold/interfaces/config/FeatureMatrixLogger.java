package com.apex.scaffold.interfaces.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 功能矩阵启动日志记录器
 *
 * <p>监听 {@link ApplicationReadyEvent} 事件,在应用启动完成后输出当前构建版本启用的功能清单。
 * 功能开关由 {@code apex.features.*} 配置项控制,与 License 授权模块联动,实现分版本功能差异化。
 *
 * <h2>记录的功能维度</h2>
 * <ul>
 *   <li>JWT 认证 - {@code apex.features.jwt-auth}</li>
 *   <li>RBAC 权限 - {@code apex.features.rbac}</li>
 *   <li>多级缓存 - {@code apex.features.cache}</li>
 *   <li>消息队列 - {@code apex.features.message-queue}</li>
 *   <li>监控告警 - {@code apex.features.monitoring}</li>
 *   <li>链路追踪 - {@code apex.features.tracing}</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>售前演示：快速向客户展示当前版本的功能范围</li>
 *   <li>版本核验：客户验收时确认已购买功能是否全部启用</li>
 *   <li>故障排查：通过启动日志快速确认功能开关状态</li>
 *   <li>合规审计：记录生产环境实际启用的功能模块</li>
 * </ul>
 *
 * <h2>版本差异示例</h2>
 * <table>
 *   <tr><th>版本</th><th>JWT 认证</th><th>RBAC 权限</th><th>多级缓存</th><th>消息队列</th></tr>
 *   <tr><td>Basic</td><td>✓</td><td>✗</td><td>✗</td><td>✗</td></tr>
 *   <tr><td>Standard</td><td>✓</td><td>✓</td><td>✓</td><td>✗</td></tr>
 *   <tr><td>Enterprise</td><td>✓</td><td>✓</td><td>✓</td><td>✓</td></tr>
 * </table>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see FeatureProperties
 * @see ApplicationReadyEvent
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureMatrixLogger {

    private final FeatureProperties features;

    /**
     * 应用启动完成后打印功能矩阵
     *
     * <p>监听 {@link ApplicationReadyEvent},在 Spring 容器完全初始化且应用可对外服务时,
     * 将当前版本的功能开关状态以表格形式输出到日志。
     *
     * <h3>日志输出示例</h3>
     * <pre>
     * ============================================================
     * Apex Scaffold 功能矩阵
     * ------------------------------------------------------------
     * JWT 认证      : true
     * RBAC 权限     : true
     * 多级缓存      : true
     * 消息队列      : false
     * 监控告警      : true
     * 链路追踪      : true
     * ------------------------------------------------------------
     * 提示: 功能开关由 apex.features.* 配置驱动,与 License 授权联动。
     * ============================================================
     * </pre>
     *
     * <p>此方法为幂等操作,多次调用结果一致。日志级别为 INFO,
     * 确保在默认配置下可见,便于运维人员快速确认版本功能。
     *
     * @param event 应用就绪事件（由 Spring 容器自动注入）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logFeatureMatrix() {
        log.info(
                """
                ============================================================
                Apex Scaffold 功能矩阵
                ------------------------------------------------------------
                JWT 认证      : {}
                RBAC 权限     : {}
                多级缓存      : {}
                消息队列      : {}
                监控告警      : {}
                链路追踪      : {}
                ------------------------------------------------------------
                提示: 功能开关由 apex.features.* 配置驱动，与 License 授权联动。
                ============================================================""",
                features.isJwtAuth(),
                features.isRbac(),
                features.isCache(),
                features.isMessageQueue(),
                features.isMonitoring(),
                features.isTracing());
    }
}
