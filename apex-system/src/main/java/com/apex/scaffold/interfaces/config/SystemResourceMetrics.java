package com.apex.scaffold.interfaces.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.net.NetworkInterface;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.Enumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统资源指标绑定器（磁盘 + 网络）。
 *
 * <p>补齐 Prometheus 指标体系中缺失的磁盘与网络维度：</p>
 * <ul>
 *   <li>{@code apex.disk.total} / {@code apex.disk.free} / {@code apex.disk.usable} /
 *       {@code apex.disk.used.percent} — 按挂载点（mount 标签）统计文件系统容量</li>
 *   <li>{@code apex.network.up} / {@code apex.network.mtu} / {@code apex.network.speed.mbps} —
 *       按网卡（iface 标签）统计启用状态与链路信息</li>
 * </ul>
 *
 * <p>Gauge 在 Prometheus 抓取时实时读取 {@link FileStore} 与 {@link NetworkInterface}，
 * 无缓存无阻塞长查询，失败时返回 NaN 而非伪装成 0。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class SystemResourceMetrics implements MeterBinder {

    private static final Logger log = LoggerFactory.getLogger(SystemResourceMetrics.class);

    /** 磁盘总容量指标名。 */
    private static final String METRIC_DISK_TOTAL = "apex.disk.total";

    /** 磁盘空闲容量指标名。 */
    private static final String METRIC_DISK_FREE = "apex.disk.free";

    /** 磁盘可用容量指标名。 */
    private static final String METRIC_DISK_USABLE = "apex.disk.usable";

    /** 磁盘使用率指标名。 */
    private static final String METRIC_DISK_USED_PERCENT = "apex.disk.used.percent";

    /** 网卡启用状态指标名。 */
    private static final String METRIC_NETWORK_UP = "apex.network.up";

    /** 网卡 MTU 指标名。 */
    private static final String METRIC_NETWORK_MTU = "apex.network.mtu";

    /** 网卡链路速率指标名。 */
    private static final String METRIC_NETWORK_SPEED = "apex.network.speed.mbps";

    @Override
    public void bindTo(MeterRegistry registry) {
        bindDisk(registry);
        bindNetwork(registry);
    }

    /**
     * 注册磁盘指标（按挂载点打标签）。
     *
     * @param registry Micrometer 注册表
     */
    private void bindDisk(MeterRegistry registry) {
        try {
            for (FileStore store : FileSystems.getDefault().getFileStores()) {
                String mount = store.name();
                Tags tags = Tags.of("mount", mount, "type", store.type());
                Gauge.builder(METRIC_DISK_TOTAL, store, s -> safeLong(store::getTotalSpace))
                        .description("磁盘总容量（字节）")
                        .tags(tags)
                        .register(registry);
                Gauge.builder(METRIC_DISK_FREE, store, s -> safeLong(store::getUnallocatedSpace))
                        .description("磁盘空闲容量（字节）")
                        .tags(tags)
                        .register(registry);
                Gauge.builder(METRIC_DISK_USABLE, store, s -> safeLong(store::getUsableSpace))
                        .description("磁盘可用容量（字节）")
                        .tags(tags)
                        .register(registry);
                Gauge.builder(METRIC_DISK_USED_PERCENT, store, s -> {
                            long total = safeLong(store::getTotalSpace);
                            long usable = safeLong(store::getUsableSpace);
                            return total <= 0
                                    ? Double.NaN
                                    : Math.min(1.0, Math.max(0.0, (double) (total - usable) / total));
                        })
                        .description("磁盘使用率 0~1")
                        .tags(tags)
                        .register(registry);
            }
        } catch (Exception e) {
            log.warn("注册磁盘指标失败: {}", e.getMessage());
        }
    }

    /**
     * 注册网卡指标（按网卡名打标签）。
     *
     * @param registry Micrometer 注册表
     */
    private void bindNetwork(MeterRegistry registry) {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface net = interfaces.nextElement();
                Tags tags = Tags.of("iface", net.getName());
                Gauge.builder(METRIC_NETWORK_UP, net, n -> isUp(n) ? 1.0 : 0.0)
                        .description("网卡是否启用（1=启用 0=停用）")
                        .tags(tags)
                        .register(registry);
                Gauge.builder(METRIC_NETWORK_MTU, net, n -> safeInt(n::getMTU))
                        .description("网卡 MTU")
                        .tags(tags)
                        .register(registry);
                Gauge.builder(METRIC_NETWORK_SPEED, net, n -> -1.0)
                        .description("网卡链路速率 Mbps（Java 无法获取，恒为 -1）")
                        .tags(tags)
                        .register(registry);
            }
        } catch (Exception e) {
            log.warn("注册网络指标失败: {}", e.getMessage());
        }
    }

    /**
     * 安全读取 long 型容量（异常返回 -1）。
     *
     * @param supplier 容量提供者（允许抛出受检异常）
     * @return 容量字节数
     */
    private static long safeLong(ThrowingLongSupplier supplier) {
        try {
            long value = supplier.getAsLong();
            return value < 0 ? 0 : value;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 安全读取网卡启用状态（异常返回 false）。
     *
     * @param net 网卡
     * @return 是否启用
     */
    private static boolean isUp(NetworkInterface net) {
        try {
            return net.isUp();
        } catch (Exception e) {
            return false;
        }
    }

    /** 允许抛出受检异常的 long 供应商。 */
    @FunctionalInterface
    private interface ThrowingLongSupplier {
        /**
         * 获取 long 值
         *
         * @return long 值
         * @throws Exception 执行过程中的异常
         */
        long getAsLong() throws Exception;
    }

    /**
     * 安全读取 int 型值（异常返回 -1）。
     *
     * @param supplier 值提供者（允许抛出受检异常）
     * @return 整数值
     */
    private static int safeInt(ThrowingIntSupplier supplier) {
        try {
            return supplier.getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }

    /** 允许抛出受检异常的 int 供应商。 */
    @FunctionalInterface
    private interface ThrowingIntSupplier {
        /**
         * 获取 int 值
         *
         * @return int 值
         * @throws Exception 执行过程中的异常
         */
        int getAsInt() throws Exception;
    }
}
