package com.apex.scaffold.common.util.id;

/**
 * 雪花算法分布式 ID 生成器（C11 分布式 ID）。
 *
 * <p>标准 Snowflake 实现：64 位 Long 由
 * {@code 1bit 符号位 + 41bit 毫秒时间戳 + 5bit 数据中心 + 5bit 机器 + 12bit 序列} 组成，
 * 单机每毫秒可生成 4096 个不重复 ID，跨机器/数据中心全局唯一。</p>
 *
 * <h2>ID 结构（64 位）</h2>
 * <pre>
 *  0 | 0000000000 0000000000 0000000000 0000000000 0 | 00000 | 00000 | 000000000000
 *  ↑  |              41 位时间戳（毫秒）               | 中心  | 机器  |  12 位序列
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
 * long id = generator.nextId();
 * }</pre>
 *
 * <p>与 MyBatis-Plus {@code IdType.INPUT} 配合可作为实体主键分配器；
 * 项目数据表主键注释约定为「雪花算法」即指本生成器。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public class SnowflakeIdGenerator {

    /** 起始时间戳（2026-01-01），用于压缩时间戳位宽 */
    private static final long CLOCK_ROLLBACK_TOLERANCE_MILLIS = 5L;

    private static final long EPOCH = 1767225600000L;

    /** 机器 ID 位数 */
    private static final long WORKER_ID_BITS = 5L;

    /** 数据中心 ID 位数 */
    private static final long DATACENTER_ID_BITS = 5L;

    /** 序列位数（每毫秒 4096 个） */
    private static final long SEQUENCE_BITS = 12L;

    /** 最大机器 ID（31） */
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    /** 最大数据中心 ID（31） */
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);

    /** 序列掩码（4095） */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /** 机器 ID 左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 数据中心 ID 左移位数 */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 时间戳左移位数 */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    /** 数据中心 ID */
    private final long datacenterId;

    /** 机器 ID */
    private final long workerId;

    /** 上次生成 ID 的时间戳 */
    private long lastTimestamp = -1L;

    /** 毫秒内序列（0-4095） */
    private long sequence = 0L;

    /**
     * 构造雪花 ID 生成器。
     *
     * @param datacenterId 数据中心 ID（0-31）
     * @param workerId     机器 ID（0-31）
     * @throws IllegalArgumentException 参数超出范围时抛出
     */
    public SnowflakeIdGenerator(long datacenterId, long workerId) {
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("数据中心 ID 超出范围: " + datacenterId);
        }
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("机器 ID 超出范围: " + workerId);
        }
        this.datacenterId = datacenterId;
        this.workerId = workerId;
    }

    /**
     * 生成下一个分布式 ID。
     *
     * <p>线程安全（synchronized）：同一毫秒内序列自增，序列用尽则自旋等待
     * 下一毫秒。时间回拨时等待时钟追上，避免 ID 重复。</p>
     *
     * @return 64 位唯一 ID
     * @throws IllegalStateException 时钟回拨超过 5ms 时抛出（防时钟漂移）
     */
    public synchronized long nextId() {
        long timestamp = currentTimeMillis();
        if (timestamp < lastTimestamp) {
            long offset = lastTimestamp - timestamp;
            if (offset > CLOCK_ROLLBACK_TOLERANCE_MILLIS) {
                throw new IllegalStateException("时钟回拨超限，拒绝生成 ID: " + offset + "ms");
            }
            // 轻微回拨：等待时钟追平
            timestamp = tilNextMillis(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    /**
     * 自旋等待下一毫秒。
     *
     * @param lastTimestamp 上次时间戳
     * @return 大于上次时间戳的当前时间戳
     */
    private long tilNextMillis(long lastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 获取当前毫秒时间戳。
     *
     * @return 当前毫秒
     */
    private long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
