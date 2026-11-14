package com.apex.scaffold.file.infrastructure.storage.config;

import com.apex.scaffold.file.domain.enums.StorageType;
import com.apex.scaffold.file.domain.service.StorageService;
import com.apex.scaffold.file.domain.service.StorageServiceProvider;
import com.apex.scaffold.file.infrastructure.storage.service.ObjectStorageServiceAdapter;
import com.apex.scaffold.storage.StorageRegistry;
import java.util.EnumMap;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将独立存储注册表适配为文件领域门面，统一默认选择并避免旧策略扫描重复装配。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class DefaultStorageConfiguration {
    /** @param registry 独立注册表 @return 文件策略选择器 */
    @Bean
    public StorageServiceProvider storageServiceProvider(StorageRegistry registry) {
        EnumMap<StorageType, StorageService> services = new EnumMap<>(StorageType.class);
        for (StorageType type : StorageType.values()) {
            String id = type.name().toLowerCase(Locale.ROOT).replace('_', '-');
            if (registry.types().contains(id)) {
                services.put(type, new ObjectStorageServiceAdapter(registry.require(id)));
            }
        }
        StorageType defaultType = StorageType.valueOf(
                registry.defaultStorage().type().toUpperCase(Locale.ROOT).replace('-', '_'));
        return new StorageServiceProvider(services, defaultType);
    }
    /** @param provider 文件策略选择器 @return 默认文件门面 */
    @Bean(name = "defaultStorageService")
    public StorageService defaultStorageService(StorageServiceProvider provider) {
        return provider.getDefaultStorageService();
    }
}
