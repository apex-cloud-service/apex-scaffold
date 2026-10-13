package com.apex.scaffold.file.domain.service;

import com.apex.scaffold.file.domain.enums.StorageType;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 存储服务提供者类.
 *
 * <p>
 * 该类为Spring组件,负责根据不同的存储类型提供对应的存储服务实例. 支持三种存储类型:MinIO、VolcEngine(火山引擎)和本地存储.
 * </p>
 *
 * <p>
 * 该类采用工厂模式的设计,根据传入的存储类型参数返回相应的存储服务实现.
 * 当请求的存储类型无效或对应的存储服务未配置时,会降级到下一个可用的存储服务.
 * </p>
 *
 * <h3>P0-ST-1修复: 构造函数注入改为 Optional 方式避免启动失败</h3>
 * <p>当某种 StorageService Bean 未注册时（如未引入对应 starter），
 * 构造参数使用 {@code Optional} 接收，允许启动继续，
 * 并在运行时降级到可用服务或输出明确警告。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see StorageService
 * @see StorageType
 */
@Slf4j
@Component
public class StorageServiceProvider {

    /**
     * MinIO存储服务实例（可选注入）.
     */
    private final StorageService minioStorageService;

    /**
     * 火山引擎存储服务实例（可选注入）.
     */
    private final StorageService volcEngineStorageService;

    /**
     * 本地存储服务实例（可选注入）.
     */
    private final StorageService localStorageService;

    /**
     * 存储类型到存储服务的映射表.
     *
     * <p>
     * 使用EnumMap实现,提供高效的键值查找性能.
     * </p>
     */
    private final Map<StorageType, StorageService> storageServiceMap = new EnumMap<>(StorageType.class);

    /**
     * P0-ST-1修复: 使用Optional构造注入，允许部分StorageService缺失时不阻塞启动.
     *
     * <p>三种存储服务均使用 {@link Optional} 接收，确保任何一个未注册时服务仍可正常启动。
     * 运行时如果请求的存储类型对应的服务不可用，会降级到下一个可用的存储服务。</p>
     *
     * @param minioStorageService      MinIO存储服务（可选）
     * @param volcEngineStorageService 火山引擎存储服务（可选）
     * @param localStorageService      本地存储服务（可选）
     */
    public StorageServiceProvider(
            @Qualifier("minioStorageService") Optional<StorageService> minioStorageService,
            @Qualifier("volcEngineStorageService") Optional<StorageService> volcEngineStorageService,
            @Qualifier("localStorageService") Optional<StorageService> localStorageService) {
        this.minioStorageService = minioStorageService.orElse(null);
        this.volcEngineStorageService = volcEngineStorageService.orElse(null);
        this.localStorageService = localStorageService.orElse(null);
        initStorageServiceMap();
        logAvailableServices();
    }

    /**
     * P0-ST-1修复: 初始化存储服务映射表，仅注册非空的服务实例.
     *
     */
    private void initStorageServiceMap() {
        if (minioStorageService != null) {
            storageServiceMap.put(StorageType.MINIO, minioStorageService);
        }
        if (volcEngineStorageService != null) {
            storageServiceMap.put(StorageType.VOLCENGINE, volcEngineStorageService);
        }
        if (localStorageService != null) {
            storageServiceMap.put(StorageType.LOCAL, localStorageService);
        }
    }

    /**
     * P0-ST-1修复: 记录当前可用的存储服务，缺失的服务输出警告日志.
     *
     */
    private void logAvailableServices() {
        if (minioStorageService == null) {
            log.warn("P0-ST-1: MinIO存储服务未注入，将不可用");
        }
        if (volcEngineStorageService == null) {
            log.warn("P0-ST-1: 火山引擎存储服务未注入，将不可用");
        }
        if (localStorageService == null) {
            log.warn("P0-ST-1: 本地存储服务未注入，将不可用");
        }
        if (storageServiceMap.isEmpty()) {
            log.error("P0-ST-1: 无任何存储服务可用，所有存储操作将失败！");
        } else {
            log.info("P0-ST-1: 可用存储服务: {}", storageServiceMap.keySet());
        }
    }

    /**
     * P0-ST-1修复: 查找并返回第一个可用的存储服务（降级策略: LOCAL → MINIO → VOLCENGINE）.
     *
     * @return 第一个可用的存储服务，全部不可用时返回null并记录错误
     */
    private StorageService findFallbackService() {
        if (localStorageService != null) {
            log.warn("P0-ST-1: 降级使用本地存储服务");
            return localStorageService;
        }
        if (minioStorageService != null) {
            log.warn("P0-ST-1: 降级使用MinIO存储服务");
            return minioStorageService;
        }
        if (volcEngineStorageService != null) {
            log.warn("P0-ST-1: 降级使用火山引擎存储服务");
            return volcEngineStorageService;
        }
        log.error("P0-ST-1: 所有存储服务都不可用！");
        return null;
    }

    /**
     * 根据存储类型获取对应的存储服务实例.
     *
     * <p>
     * P0-ST-1修复: 如果请求的存储类型对应的服务不可用，则降级到下一个可用服务。
     * 如果传入的存储类型为null或不在支持的存储类型范围内, 将降级到第一个可用服务.
     * </p>
     *
     * @param storageType 存储类型枚举值,不能为null
     * @return 对应类型的存储服务实例,如果类型无效则降级到第一个可用服务
     * @see StorageType
     * @see StorageService
     */
    public StorageService getStorageService(StorageType storageType) {
        if (storageType == null) {
            log.warn("存储类型为null，尝试降级");
            return findFallbackService();
        }
        StorageService service = storageServiceMap.get(storageType);
        if (service != null) {
            return service;
        }
        log.warn("P0-ST-1: 请求的存储类型[{}]对应的服务不可用，尝试降级", storageType);
        return findFallbackService();
    }

    /**
     * 根据存储类型名称获取对应的存储服务实例.
     *
     * <p>
     * 该方法将字符串类型的存储类型名称转换为对应的枚举值, 然后调用{@link #getStorageService(StorageType)}方法返回存储服务实例. 转换时忽略大小写.
     * </p>
     *
     * <p>
     * P0-ST-1修复: 如果传入的名称为null、空字符串或不是有效的存储类型名称, 将降级到第一个可用服务.
     * </p>
     *
     * @param storageTypeName 存储类型名称字符串(如"MINIO"、"VOLCENGINE"、"LOCAL")
     * @return 对应类型的存储服务实例,如果名称无效则降级到第一个可用服务
     * @see StorageType
     * @see StorageService
     */
    public StorageService getStorageService(String storageTypeName) {
        if (storageTypeName == null || storageTypeName.isBlank()) {
            log.warn("存储类型名称为空，尝试降级");
            return findFallbackService();
        }
        try {
            StorageType storageType = StorageType.valueOf(storageTypeName.toUpperCase());
            return getStorageService(storageType);
        } catch (IllegalArgumentException e) {
            log.warn("不支持的存储类型: {}, 尝试降级", storageTypeName);
            return findFallbackService();
        }
    }

    /**
     * 获取默认的存储服务实例.
     *
     * <p>
     * P0-ST-1修复: 按 LOCAL → MINIO → VOLCENGINE 顺序返回第一个可用的存储服务。
     * </p>
     *
     * @return 默认的第一个可用存储服务实例，全部不可用时返回null
     * @see StorageService
     */
    public StorageService getDefaultStorageService() {
        return findFallbackService();
    }
}
