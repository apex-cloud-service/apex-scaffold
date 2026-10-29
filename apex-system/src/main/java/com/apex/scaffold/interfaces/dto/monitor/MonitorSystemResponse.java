package com.apex.scaffold.interfaces.dto.monitor;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/**
 * 系统资源监控响应（/admin-api/v1/monitor/system）。
 *
 *
 *<p>聚合 JVM 内存/GC/类加载、CPU、进程、线程、磁盘、网络与阻塞 IO 线程池快照。</p>
 *
 *@param timestamp 采集时间戳（毫秒）
 *@param jvm JVM 指标
 *@param cpu CPU 指标
 *@param process 进程指标
 *@param threads 线程指标
 *@param disk 磁盘使用列表
 *@param network 网络使用列表
 *@param blockingIo 阻塞 IO 线程池快照（key=池名）
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(description = "系统资源监控（内存/CPU/磁盘/网络/JVM/线程/GC）")
public record MonitorSystemResponse(
        @Schema(description = "采集时间戳（毫秒）") long timestamp,
        @Schema(description = "JVM 指标") JvmStats jvm,
        @Schema(description = "CPU 指标") CpuStats cpu,
        @Schema(description = "进程指标") ProcessStats process,
        @Schema(description = "线程指标") ThreadStats threads,
        @Schema(description = "磁盘使用列表") List<DiskUsage> disk,
        @Schema(description = "网络使用列表") List<NetworkUsage> network,
        @Schema(description = "阻塞 IO 线程池快照") Map<String, BlockingIoPool> blockingIo) {

    /**
     * JVM 内存/GC/类加载指标。
     *
     * @param heapUsed 堆内存已用（字节）
     * @param heapCommitted 堆内存已提交（字节）
     * @param heapMax 堆内存最大值（字节）
     * @param heapUsedPercent 堆内存使用率 0~1
     * @param nonHeapUsed 非堆内存已用（字节）
     * @param nonHeapCommitted 非堆内存已提交（字节）
     * @param gcPauseTotalMs GC 暂停累计耗时（毫秒）
     * @param gcPauseCount GC 暂停次数
     * @param classLoaded 已加载类数
     * @param classUnloaded 已卸载类数
     * @param bufferUsed 缓冲区已用内存（字节）
     */
    @Schema(description = "JVM 指标")
    public record JvmStats(
            @Schema(description = "堆内存已用（字节）") double heapUsed,
            @Schema(description = "堆内存已提交（字节）") double heapCommitted,
            @Schema(description = "堆内存最大值（字节）") double heapMax,
            @Schema(description = "堆内存使用率 0~1") double heapUsedPercent,
            @Schema(description = "非堆内存已用（字节）") double nonHeapUsed,
            @Schema(description = "非堆内存已提交（字节）") double nonHeapCommitted,
            @Schema(description = "GC 暂停累计耗时（毫秒）") double gcPauseTotalMs,
            @Schema(description = "GC 暂停次数") long gcPauseCount,
            @Schema(description = "已加载类数") long classLoaded,
            @Schema(description = "已卸载类数") long classUnloaded,
            @Schema(description = "缓冲区已用内存（字节）") double bufferUsed) {}

    /**
     * CPU 指标。
     *
     * @param processUsage 进程 CPU 使用率 0~1
     * @param systemUsage 系统 CPU 使用率 0~1
     * @param cores CPU 核数
     * @param loadAverage 系统负载均值（-1 表示不可用）
     */
    @Schema(description = "CPU 指标")
    public record CpuStats(
            @Schema(description = "进程 CPU 使用率 0~1") double processUsage,
            @Schema(description = "系统 CPU 使用率 0~1") double systemUsage,
            @Schema(description = "CPU 核数") double cores,
            @Schema(description = "系统负载均值（-1 表示不可用）") double loadAverage) {}

    /**
     * 进程指标。
     *
     * @param uptimeSeconds 进程运行时长（秒）
     * @param startTime 进程启动时间
     * @param residentMemory 常驻内存（字节，Linux 可用）
     * @param virtualMemory 虚拟内存（字节，Linux 可用）
     * @param openFiles 已打开文件描述符数
     * @param maxFiles 最大文件描述符数
     */
    @Schema(description = "进程指标")
    public record ProcessStats(
            @Schema(description = "进程运行时长（秒）") long uptimeSeconds,
            @Schema(description = "进程启动时间") String startTime,
            @Schema(description = "常驻内存（字节，Linux 可用）") double residentMemory,
            @Schema(description = "虚拟内存（字节，Linux 可用）") double virtualMemory,
            @Schema(description = "已打开文件描述符数") double openFiles,
            @Schema(description = "最大文件描述符数") double maxFiles) {}

    /**
     * 线程指标。
     *
     * @param live 当前存活线程数
     * @param peak 历史峰值线程数
     * @param daemon 守护线程数
     */
    @Schema(description = "线程指标")
    public record ThreadStats(
            @Schema(description = "当前存活线程数") double live,
            @Schema(description = "历史峰值线程数") double peak,
            @Schema(description = "守护线程数") double daemon) {}

    /**
     * 磁盘使用。
     *
     * @param mount 挂载点/盘符
     * @param type 文件系统类型
     * @param total 总容量（字节）
     * @param free 空闲容量（字节）
     * @param usable 可用容量（字节）
     * @param usedPercent 使用率 0~1
     */
    @Schema(description = "磁盘使用")
    public record DiskUsage(
            @Schema(description = "挂载点/盘符") String mount,
            @Schema(description = "文件系统类型") String type,
            @Schema(description = "总容量（字节）") long total,
            @Schema(description = "空闲容量（字节）") long free,
            @Schema(description = "可用容量（字节）") long usable,
            @Schema(description = "使用率 0~1") double usedPercent) {}

    /**
     * 网络使用。
     *
     * @param name 网卡名称
     * @param up 是否已启用
     * @param speedMbps 链路速率（Mbps，-1 表示未知）
     * @param rxBytes 累计接收字节数
     * @param txBytes 累计发送字节数
     * @param rxBytesPerSec 接收速率（字节/秒，Linux 可用）
     * @param txBytesPerSec 发送速率（字节/秒，Linux 可用）
     * @param available 速率数据是否可用
     */
    @Schema(description = "网络使用")
    public record NetworkUsage(
            @Schema(description = "网卡名称") String name,
            @Schema(description = "是否已启用") boolean up,
            @Schema(description = "链路速率（Mbps，-1 表示未知）") long speedMbps,
            @Schema(description = "累计接收字节数") long rxBytes,
            @Schema(description = "累计发送字节数") long txBytes,
            @Schema(description = "接收速率（字节/秒，Linux 可用）") double rxBytesPerSec,
            @Schema(description = "发送速率（字节/秒，Linux 可用）") double txBytesPerSec,
            @Schema(description = "速率数据是否可用") boolean available) {}

    /**
     * 阻塞 IO 线程池快照。
     *
     * @param maxThreads 最大工作线程数
     * @param queueCapacity 队列容量
     * @param activeTasks 执行中任务数
     * @param queuedTasks 排队任务数
     * @param completedTasks 累计完成任务数
     * @param saturation 饱和度 0~1
     * @param up 采集是否可用
     */
    @Schema(description = "阻塞 IO 线程池快照")
    public record BlockingIoPool(
            @Schema(description = "最大工作线程数") int maxThreads,
            @Schema(description = "队列容量") int queueCapacity,
            @Schema(description = "执行中任务数") int activeTasks,
            @Schema(description = "排队任务数") int queuedTasks,
            @Schema(description = "累计完成任务数") long completedTasks,
            @Schema(description = "饱和度 0~1") double saturation,
            @Schema(description = "采集是否可用") boolean up) {}
}
