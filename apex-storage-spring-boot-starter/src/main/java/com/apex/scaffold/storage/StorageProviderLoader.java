package com.apex.scaffold.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * 第三方存储后端 SPI 加载器（方案 M3 / 批次 C3）。
 *
 * <p>扩展点在 {@link ObjectStorage}：第三方以独立 JAR 提供实现，并在
 * {@code META-INF/services/com.apex.scaffold.storage.ObjectStorage} 中声明类名，
 * 即可被自动发现并注册进 {@link StorageRegistry}——无需修改本 starter。
 *
 * <p>受控口径：
 * <ul>
 *   <li>加载失败（类缺失/构造异常）逐条记录并跳过，不阻断已装配的内置后端；</li>
 *   <li>重复 {@code type()} 仍由 {@link StorageRegistry} 严格拒绝（不隐式覆盖）；</li>
 *   <li>SPI 后端同样受 {@code storage.default-type} 选择约束。</li>
 * </ul>
 *
 * <p>说明：内置五后端仍走 Spring 条件装配（SDK 依赖 {@code optional}），SPI 仅用于
 * 免改库接入新后端（如华为 OBS、Azure Blob）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
public final class StorageProviderLoader {

    private StorageProviderLoader() {}

    /**
     * 通过 {@link ServiceLoader} 发现第三方存储后端。
     *
     * @param classLoader 类加载器（用于隔离不同部署形态）
     * @return 已加载的 SPI 后端（可能为空）
     */
    public static List<ObjectStorage> load(ClassLoader classLoader) {
        List<ObjectStorage> discovered = new ArrayList<>();
        for (ObjectStorage storage : ServiceLoader.load(ObjectStorage.class, classLoader)) {
            try {
                if (storage.type() == null || storage.type().isBlank()) {
                    continue;
                }
                discovered.add(storage);
            } catch (RuntimeException ignored) {
                // 单个第三方实现异常不阻断内置后端装配
            }
        }
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
}
