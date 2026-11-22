package com.apex.scaffold.storage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * 第三方存储后端 SPI 加载器（方案 M3 / 批次 C3）。
 *
 * <p>扩展点在 {@link ObjectStorage}：第三方以独立 JAR 提供实现，并在
 * {@code META-INF/services/com.apex.scaffold.storage.ObjectStorage} 中声明类名，
 * 即可被自动发现并注册进 {@link StorageRegistry}——无需修改本 starter。
 *
 * <p>受控口径：
 * <ul>
 *   <li>发现过程按条目隔离：实例化失败、{@code type()} 抛异常或返回空白的实现会记录
 *       {@code WARN} 后被跳过，不影响其余 SPI 后端与已装配的内置后端；</li>
 *   <li>描述符本身不可遍历（{@link ServiceConfigurationError}）时停止发现剩余条目，
 *       已成功发现的条目仍然保留，不会无限重试；</li>
 *   <li>重复 {@code type()} 仍由 {@link StorageRegistry} 严格拒绝（不隐式覆盖）；</li>
 *   <li>SPI 后端同样受 {@code storage.default-type} 选择约束。</li>
 * </ul>
 *
 * <p>说明：内置五后端仍走 Spring 条件装配（SDK 依赖 {@code optional}），SPI 仅用于
 * 免改库接入新后端（如华为 OBS、Azure Blob）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.1
 */
@Slf4j
public final class StorageProviderLoader {

    private StorageProviderLoader() {}

    /**
     * 通过 {@link ServiceLoader} 发现第三方存储后端。
     *
     * @param classLoader 类加载器（用于隔离不同部署形态）
     * @return 已加载且声明了非空 {@code type()} 的 SPI 后端（可能为空）
     */
    public static List<ObjectStorage> load(ClassLoader classLoader) {
        List<ObjectStorage> discovered = new ArrayList<>();
        collect(ServiceLoader.load(ObjectStorage.class, classLoader).iterator(), discovered);
        return discovered;
    }

    /**
     * 通过上下文类加载器发现第三方存储后端。
     *
     * @return 已加载的 SPI 后端
     */
    public static List<ObjectStorage> load() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        return load(contextClassLoader == null ? StorageProviderLoader.class.getClassLoader() : contextClassLoader);
    }

    /**
     * 逐条收集 SPI 实现，隔离单条失败。
     *
     * <p>包内可见以便单元测试注入异常迭代器。</p>
     *
     * @param iterator 实现迭代器
     * @param target   收集目标列表
     */
    static void collect(Iterator<ObjectStorage> iterator, List<ObjectStorage> target) {
        while (true) {
            boolean present;
            try {
                present = iterator.hasNext();
            } catch (ServiceConfigurationError error) {
                log.warn("存储 SPI 描述符遍历失败，停止发现剩余条目: {}", error.getMessage());
                return;
            }
            if (!present) {
                return;
            }
            ObjectStorage storage;
            try {
                storage = iterator.next();
            } catch (ServiceConfigurationError | RuntimeException error) {
                log.warn("存储 SPI 实现实例化失败，已跳过该条目: {}", error.toString());
                continue;
            }
            accept(storage, target);
        }
    }

    /**
     * 校验并收集单个 SPI 实现。
     *
     * @param storage SPI 实现
     * @param target  收集目标列表
     */
    private static void accept(ObjectStorage storage, List<ObjectStorage> target) {
        try {
            String type = storage.type();
            if (type == null || type.isBlank()) {
                log.warn("存储 SPI 实现 {} 未声明 type，已跳过", storage.getClass().getName());
                return;
            }
            target.add(storage);
            log.info("已发现存储 SPI 后端: type={}, class={}", type, storage.getClass().getName());
        } catch (RuntimeException error) {
            log.warn("存储 SPI 实现 {} 的 type() 调用失败，已跳过: {}", storage.getClass().getName(), error.toString());
        }
    }
}
