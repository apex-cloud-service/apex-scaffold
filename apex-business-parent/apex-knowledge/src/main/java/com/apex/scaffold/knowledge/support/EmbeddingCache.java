package com.apex.scaffold.knowledge.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 进程内向量缓存（contentHash → 向量）。
 *
 * <p>重复摄取与相同分块重索引时免于重复调用 embedding 供应商，控制费用与延迟；
 * 缓存随进程重启丢失，由文档内容哈希保证可重算。容量按条目数近似控制，
 * 淘汰采用简单 LRU。
 *
 * @author Apex Team
 * @since 1.3.2
 */
@Component
public class EmbeddingCache {

    private final Map<String, float[]> cache;

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 构建缓存。
     *
     * @param maxEntries 最大条目数
     */
    public EmbeddingCache(@Value("${apex.knowledge.embedding.cache-entries:4096}") int maxEntries) {
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * 读取缓存向量。
     *
     * @param contentHash 内容摘要
     * @return 向量；未命中返回 null
     */
    public float[] get(String contentHash) {
        lock.readLock().lock();
        try {
            return cache.get(contentHash);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 写入缓存向量。
     *
     * @param contentHash 内容摘要
     * @param vector 向量
     */
    public void put(String contentHash, float[] vector) {
        lock.writeLock().lock();
        try {
            cache.put(contentHash, vector);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
