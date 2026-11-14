package com.apex.scaffold.common.id;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 消息ID生成器
 *
 * <p>基于雪花算法(Snowflake)生成全局唯一的消息ID,适用于分布式环境下的消息标识生成。
 *
 * <h2>职责范围</h2>
 * <p>雪花算法由Twitter开源,生成的ID是一个64位的long类型整数:
 * <pre>
 * ┌─────────────┬──────────┬──────────┬──────────────┐
 * │ 1bit(符号位) │ 41bit时间戳│ 10bit机器ID │ 12bit序列号 │
 * └─────────────┴──────────┴──────────┴──────────────┘
 * </pre>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>时间戳(41位): 精确到毫秒,可使用69年</li>
 *   <li>workerId(5位): 工作机器ID,最多支持32个实例</li>
 *   <li>datacenterId(5位): 数据中心ID,最多支持32个数据中心</li>
 *   <li>序列号(12位): 毫秒内序列,最多支持4096个ID/ms</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>单机TPS: 每秒可生成409.6万个ID</li>
 *   <li>全局唯一: 不同机器生成的ID不会重复</li>
 *   <li>趋势递增: ID按时间顺序递增,有利于数据库索引</li>
 *   <li>无需中心服务: 本地生成,无网络开销</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>workerId: 取应用名称hashCode的模32余数<br>
 * datacenterId: 取主机IP地址hashCode的模32余数<br>
 * 如果无法获取主机地址,datacenterId默认为0
 *
 * <h2>使用示例</h2>
 * <pre>
 * {@code
 * @Service
 * @RequiredArgsConstructor
 * public class MessageService {
 *
 *     private final MessageIdGenerator messageIdGenerator;
 *
 *     public void sendMessage() {
 *         String messageId = messageIdGenerator.nextId();
 *         // messageId示例: "1234567890123456789"
 *     }
 * }
 * }
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Snowflake
 */
@Slf4j
@Component
public class MessageIdGenerator {

    private final Snowflake snowflake;

    /**
     * 基于应用名派生 workerId、本机地址派生 datacenterId，初始化雪花算法。
     *
     * @param appName 应用名（{@code spring.application.name}）
     */
    public MessageIdGenerator(@Value("${spring.application.name:apex}") String appName) {
        long workerId = Math.abs(appName.hashCode() % 32);
        long datacenterId = getDatacenterId();
        this.snowflake = IdUtil.getSnowflake(workerId, datacenterId);
        log.info("MessageIdGenerator初始化: workerId={}, datacenterId={}", workerId, datacenterId);
    }

    /**
     * 生成全局唯一消息 ID。
     *
     * @return 雪花算法 ID 的十进制字符串
     */
    public String nextId() {
        return String.valueOf(snowflake.nextId());
    }

    private long getDatacenterId() {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            return Math.abs(hostAddress.hashCode() % 32);
        } catch (UnknownHostException e) {
            log.warn("无法获取主机地址,使用默认datacenterId=0", e);
            return 0L;
        }
    }
}
