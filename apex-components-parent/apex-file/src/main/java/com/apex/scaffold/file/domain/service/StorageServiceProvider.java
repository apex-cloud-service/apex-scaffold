package com.apex.scaffold.file.domain.service;

import com.apex.scaffold.file.domain.enums.StorageType;
import java.util.Map;
import java.util.Objects;

/**
 * 文件存储策略选择器，显式选择不可用或未知策略时失败，默认值由装配配置确定。
 *
 * @author Apex Team
 * @since 1.3.1
 */
public class StorageServiceProvider {
    private final Map<StorageType, StorageService> services;
    private final StorageService defaultService;

    /** @param services 已启用的策略 @param defaultType 显式默认类型 */
    public StorageServiceProvider(Map<StorageType, StorageService> services, StorageType defaultType) {
        this.services = Map.copyOf(services);
        defaultService = getStorageService(defaultType);
    }

    /** @param type 请求策略 @return 对应门面，缺失时抛出异常 */
    public StorageService getStorageService(StorageType type) {
        StorageService service = services.get(Objects.requireNonNull(type, "存储类型不能为空"));
        if (service == null) {
            throw new IllegalArgumentException("存储策略未启用: " + type);
        }
        return service;
    }

    /** @param type 策略名称，兼容枚举及 SPI 标识 @return 对应门面 */
    public StorageService getStorageService(String type) {
        return getStorageService(StorageType.valueOf(Objects.requireNonNull(type, "存储类型不能为空")
                .toUpperCase(java.util.Locale.ROOT)
                .replace('-', '_')));
    }

    /** @return 已配置的默认策略 */
    public StorageService getDefaultStorageService() {
        return defaultService;
    }
}
