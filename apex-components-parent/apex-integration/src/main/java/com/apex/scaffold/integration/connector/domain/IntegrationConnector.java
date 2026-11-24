package com.apex.scaffold.integration.connector.domain;

import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 系统集成连接器 SPI（方案 M5 / 批次 E1）。
 *
 * <p>第三方以独立 JAR 实现本接口并在
 * {@code META-INF/services/com.apex.scaffold.integration.connector.domain.IntegrationConnector}
 * 中声明，即可接入新连接器类型——无需修改集成库代码（与 C3 存储 SPI 同思路）。
 *
 * <p>实现约束：
 * <ul>
 *   <li>阻塞 SDK 调用须隔离到 {@code BlockingIoSchedulers.remote()}；</li>
 *   <li>凭据由门面解密后经 {@code config} 传入，<b>实现不得持久化凭据</b>；</li>
 *   <li>失败以异常向上传播，由门面统一重试与审计。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public interface IntegrationConnector {

    /**
     * 连接器类型标识（与 {@code int_connector_config.connector_type} 一致）。
     *
     * @return 类型标识（如 HTTP / WEBHOOK / WECHAT / CHUANGLAN）
     */
    String type();

    /**
     * 连通性健康检查。
     *
     * @param config 已解密的连接配置（含端点、认证等）
     * @throws RuntimeException 探测失败时以异常传播，由调用方决定降级策略
     * @return 操作完成信号，失败时传播错误
     */
    Mono<Void> healthCheck(Map<String, Object> config);

    /**
     * 执行一次出站调用。
     *
     * @param config    已解密的连接配置
     * @param operation 操作标识（连接器自定义语义，如 sendMessage / syncUser）
     * @param payload   请求载荷
     * @param idempotencyKey 幂等键（连接器应尽力透传）
     * @return 响应载荷
     */
    Mono<Map<String, Object>> execute(
            Map<String, Object> config, String operation, Map<String, Object> payload, String idempotencyKey);
}
