package com.apex.scaffold.common.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 面向投票高频读取链路的轻量级、有界、惰性过期本地缓存。
 *
 * <p>该实现不依赖基础设施层缓存组件，便于领域层和应用层共同使用，同时保持模块依赖方向。缓存条目以
 * {@link System#currentTimeMillis()} 记录写入时间；读取时发现过期会原子移除，写入超过容量上限时先清理
 * 全部过期条目，仍超限则淘汰当前最早写入的条目。该策略适用于允许短时间陈旧、条目价值接近的派生数据，
 * 不应用于授权、余额、幂等状态或其他强一致数据。</p>
 *
 * <p>本类只保证单次读写和淘汰操作的线程安全，不提供跨节点一致性，也不合并并发缓存未命中的加载请求。
 * 调用方必须在核心业务写入成功后主动调用 {@link #invalidate(Object)}，并为每次读取传入正数 TTL。</p>
 *
 * @param <K> 缓存键类型
 * @param <V> 缓存值类型
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public final class ExpiringLocalCache<K, V> {

    /** 保存缓存值与写入时间的并发容器。 */
    private final ConcurrentHashMap<K, CacheEntry<V>> entries = new ConcurrentHashMap<>();

    /** 为同一毫秒内的并发写入提供严格递增的淘汰顺序。 */
    private final AtomicLong writeSequence = new AtomicLong();

    /**
     * 读取未过期缓存值。
     *
     * <p>当键不存在、TTL 非正数或条目已经过期时返回 {@code null}。过期条目会使用键值双重匹配方式移除，
     * 避免并发刷新后的新条目被旧读取线程误删。</p>
     *
     * @param key 缓存键，不应为 {@code null}
     * @param ttlMillis 条目允许存活的毫秒数
     * @return 未过期的缓存值；未命中或过期时返回 {@code null}
     */
    public V get(K key, long ttlMillis) {
        CacheEntry<V> entry = entries.get(key);
        if (entry == null || ttlMillis <= 0) {
            return null;
        }
        if (System.currentTimeMillis() - entry.writtenAtMillis() >= ttlMillis) {
            entries.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    /**
     * 写入缓存并将条目总数限制在给定上限内。
     *
     * <p>容量上限小于 1 时不会保留新值。达到上限后先按当前 TTL 清理过期数据；若高并发写入后仍然超限，
     * 则反复移除写入时间最早的条目，直至恢复到上限以内。</p>
     *
     * @param key 缓存键，不应为 {@code null}
     * @param value 缓存值，不应为 {@code null}
     * @param ttlMillis 用于容量清理的条目存活毫秒数
     * @param maximumSize 最大条目数
     */
    public void put(K key, V value, long ttlMillis, int maximumSize) {
        if (maximumSize < 1) {
            entries.remove(key);
            return;
        }
        long now = System.currentTimeMillis();
        entries.put(key, new CacheEntry<>(value, now, writeSequence.incrementAndGet()));
        evictOverflow(now, ttlMillis, maximumSize);
    }

    /**
     * 主动失效指定键。
     *
     * @param key 需要失效的缓存键
     */
    public void invalidate(K key) {
        if (key != null) {
            entries.remove(key);
        }
    }

    /**
     * 返回当前缓存条目数。
     *
     * <p>该方法主要用于单元测试和运行期诊断，返回值可能在并发写入时立即变化。</p>
     *
     * @return 当前条目数
     */
    public int size() {
        return entries.size();
    }

    /**
     * 清理过期条目并淘汰最早写入的数据。
     *
     * @param now 当前毫秒时间戳
     * @param ttlMillis 条目存活时间
     * @param maximumSize 最大条目数
     */
    private void evictOverflow(long now, long ttlMillis, int maximumSize) {
        if (entries.size() <= maximumSize) {
            return;
        }
        if (ttlMillis > 0) {
            entries.entrySet().removeIf(entry -> now - entry.getValue().writtenAtMillis() >= ttlMillis);
        }
        while (entries.size() > maximumSize) {
            Map.Entry<K, CacheEntry<V>> oldest = entries.entrySet().stream()
                    .min(java.util.Comparator.comparingLong(
                            entry -> entry.getValue().sequence()))
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            entries.remove(oldest.getKey(), oldest.getValue());
        }
    }

    /**
     * 不可变缓存条目。
     *
     * @param value 缓存值
     * @param writtenAtMillis 写入时间戳
     * @param sequence 进程内严格递增的写入序号，用于稳定淘汰顺序
     * @param <V> 缓存值类型
     * @author Apex Team
     * @since 2.2.0
     */
    private record CacheEntry<V>(V value, long writtenAtMillis, long sequence) {}
}
