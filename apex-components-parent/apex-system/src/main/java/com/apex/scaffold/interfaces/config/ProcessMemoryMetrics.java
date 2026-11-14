package com.apex.scaffold.interfaces.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 进程内存指标绑定器。
 *
 * <p>注册与 Prometheus 标准进程指标同名的 Gauge，供 Grafana "进程内存（常驻 / 虚拟）" 面板使用：
 * <ul>
 *   <li>{@code process.resident.memory.bytes} - 进程常驻内存（RSS），导出名为
 *       {@code process_resident_memory_bytes}</li>
 *   <li>{@code process.virtual.memory.bytes} - 进程虚拟内存（VSZ），导出名为
 *       {@code process_virtual_memory_bytes}</li>
 * </ul>
 *
 * <p>micrometer-core 与 micrometer-registry-prometheus（1.9 ~ 1.14 全部版本验证）均不提供
 * 进程内存指标，这两个指标名来自 Prometheus 客户端库（Go/Java），Java 服务不会自动产出，
 * 导致 Grafana 进程内存面板长期无数据。本类在 Linux 上读取 {@code /proc/self/statm} 补齐；
 * 非 Linux 环境（如本机 macOS 开发）不注册指标，与现状一致，不影响本地开发。
 *
 * <h2>设计说明</h2>
 * <p>采用函数式实现：指标元数据收敛为不可变 record 列表（{@link MemoryFieldSpec}），
 * {@link #bindTo(MeterRegistry)} 通过 {@code List.forEach} + lambda 统一注册两个 Gauge；
 * {@link #readMemoryBytes(int)} 通过 {@link Optional} 链式映射完成"读取 → 取字段 →
 * 解析 → 换算字节"的数据管线，读取异常沿管线返回空值兜底为 0，避免命令式 try-catch 分支。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ProcessMemoryMetrics implements MeterBinder {

    /** {@code /proc/self/statm} 默认路径（Linux 专用） */
    static final String DEFAULT_STATM_PATH = "/proc/self/statm";

    /** Linux 页大小（字节），x86_64 与 aarch64 均为 4K */
    private static final long PAGE_SIZE_BYTES = 4096L;

    /** statm 中虚拟内存（size）字段下标 */
    private static final int VIRTUAL_MEMORY_FIELD = 0;

    /** statm 中常驻内存（resident）字段下标 */
    private static final int RESIDENT_MEMORY_FIELD = 1;

    /** 常驻内存指标名（Prometheus 导出时点号转为下划线） */
    private static final String RESIDENT_METRIC = "process.resident.memory.bytes";

    /** 虚拟内存指标名（Prometheus 导出时点号转为下划线） */
    private static final String VIRTUAL_METRIC = "process.virtual.memory.bytes";

    /** 进程内存指标规格列表，作为函数式注册的数据源 */
    private static final List<MemoryFieldSpec> MEMORY_FIELDS = List.of(
            new MemoryFieldSpec(RESIDENT_METRIC, RESIDENT_MEMORY_FIELD, "进程常驻内存（RSS），单位为字节"),
            new MemoryFieldSpec(VIRTUAL_METRIC, VIRTUAL_MEMORY_FIELD, "进程虚拟内存（VSZ），单位为字节"));

    /** 进程内存信息文件路径 */
    private final Path statmPath;

    /**
     * 使用默认 {@code /proc/self/statm} 路径构造。
     */
    public ProcessMemoryMetrics() {
        this(Path.of(DEFAULT_STATM_PATH));
    }

    /**
     * 使用指定 statm 文件路径构造，便于单元测试注入临时文件。
     *
     * @param statmPath statm 文件路径
     */
    ProcessMemoryMetrics(Path statmPath) {
        this.statmPath = statmPath;
    }

    /**
     * 绑定进程内存指标到注册表。
     *
     * <p>statm 文件不可读（非 Linux 环境）时直接返回，不注册任何指标；
     * 可读时遍历 {@link #MEMORY_FIELDS} 规格列表，为每一项调用
     * {@link #registerMemoryGauge(MeterRegistry, MemoryFieldSpec)} 完成注册。
     *
     * @param registry Micrometer 指标注册表
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        if (!Files.isReadable(statmPath)) {
            return;
        }
        MEMORY_FIELDS.forEach(spec -> registerMemoryGauge(registry, spec));
    }

    /**
     * 注册单个内存 Gauge 指标。
     *
     * <p>指标值由 {@code () -> readMemoryBytes(spec.fieldIndex())} lambda 提供，
     * Micrometer 在每次 Prometheus scrape 时调用该 Supplier 采集最新值，
     * 因此进程内存变化可实时反映到指标中。
     *
     * @param registry Micrometer 指标注册表
     * @param spec     内存指标规格（指标名、字段下标、描述）
     */
    private void registerMemoryGauge(MeterRegistry registry, MemoryFieldSpec spec) {
        Gauge.builder(spec.meterName(), () -> readMemoryBytes(spec.fieldIndex()))
                .description(spec.description())
                .baseUnit("bytes")
                .register(registry);
    }

    /**
     * 读取 statm 指定字段对应的内存字节数（纯函数）。
     *
     * <p>通过 {@link Optional} 管线完成"读取文件 → 过滤越界 → 取字段 → 解析数字 →
     * 换算字节"的映射链：任一环节失败（文件不可读、字段越界、内容非数字）都返回空值，
     * 链尾 {@code orElse(0.0)} 兜底为 0，保证采集不中断。
     *
     * @param fieldIndex 字段下标，0 为虚拟内存，1 为常驻内存
     * @return 内存字节数，读取失败时为 0
     */
    private double readMemoryBytes(int fieldIndex) {
        try {
            return readStatmFields()
                    .filter(fields -> fieldIndex < fields.length)
                    .map(fields -> fields[fieldIndex])
                    .map(Long::parseLong)
                    .map(pages -> (double) (pages * PAGE_SIZE_BYTES))
                    .orElse(0.0);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 读取并切分 statm 文件内容（纯函数）。
     *
     * <p>statm 首行共 7 个数字（页数）：size resident shared text lib data dt。
     * 文件读取异常时返回 {@link Optional#empty()}，由调用方管线兜底。
     *
     * @return 按空白切分的字段数组；读取失败时为空 Optional
     */
    private Optional<String[]> readStatmFields() {
        try {
            return Optional.of(
                    Files.readString(statmPath, StandardCharsets.UTF_8).trim().split("\\s+"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * 内存指标规格（不可变 record）。
     *
     * <p>描述一个进程内存 Gauge 的元数据三元组：Micrometer 指标名、statm 字段下标与指标描述，
     * 作为函数式注册的数据源，避免重复的注册样板代码。
     *
     * @param meterName   Micrometer 指标名，Prometheus 导出时点号转为下划线
     * @param fieldIndex  statm 字段下标
     * @param description 指标说明文本
     */
    private record MemoryFieldSpec(String meterName, int fieldIndex, String description) {}
}
