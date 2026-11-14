package com.apex.scaffold.workflow.domain.repository;

import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * 后台调度租约端口。
 *
 * <p>进程内的原子标志只能阻止同一 JVM 重复进入，多实例部署还需跨进程互斥。
 * 租约带到期时间：持有实例异常退出时无需人工干预，到期后可被其他实例接管。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ScanLease {

    /**
     * 尝试抢占租约。
     *
     * @param name  租约名称
     * @param owner 本轮持有标识；不同扫描轮次必须使用不同标识，同标识重试表示续租
     * @param ttl   租约有效期
     * @return 抢占成功为 {@code true}；被其他实例持有且未到期为 {@code false}
     */
    Mono<Boolean> acquire(String name, String owner, Duration ttl);

    /**
     * 释放租约；仅当仍由 {@code owner} 持有时生效。
     *
     * @param name  租约名称
     * @param owner 抢占时使用的本轮持有标识
     */
    Mono<Void> release(String name, String owner);
}
