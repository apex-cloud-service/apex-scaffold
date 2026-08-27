package com.apex.scaffold.infrastructure.storage.config;

import com.apex.scaffold.domain.service.StorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Primary;

/**
 * 默认对象存储服务配置。
 *
 * <p>根据 {@code storage.default-type} 从已经启用的底层存储实现中选择唯一的默认服务，
 * 并以 {@code @Primary} 门面暴露给应用层。业务服务只依赖 {@link StorageService} 抽象，
 * 不再通过 Bean 数量或字段名称推断实际存储后端。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@AutoConfiguration
public class DefaultStorageConfiguration {

    /**
     * 将 MinIO 选为显式配置的对象存储。
     *
     * @param minioStorageService MinIO 存储实现
     * @return 默认对象存储服务
     */
    @Bean(name = "defaultStorageService")
    @Primary
    @ConditionalOnProperty(name = "storage.default-type", havingValue = "minio")
    public StorageService minioDefaultStorageService(
            @Qualifier("minioStorageService") StorageService minioStorageService) {
        return minioStorageService;
    }

    /**
     * 将火山引擎 TOS 选为默认对象存储。
     *
     * @param volcEngineStorageService 火山引擎 TOS 存储实现
     * @return 默认对象存储服务
     */
    @Bean(name = "defaultStorageService")
    @Primary
    @ConditionalOnProperty(name = "storage.default-type", havingValue = "volcengine")
    public StorageService volcEngineDefaultStorageService(
            @Qualifier("volcEngineStorageService") StorageService volcEngineStorageService) {
        return volcEngineStorageService;
    }

    /**
     * 将本地文件系统选为默认对象存储。
     *
     * <p>未配置 {@code storage.default-type} 时默认选择 Local，确保文件上传不依赖外部对象存储服务。</p>
     *
     * @param localStorageService 本地存储实现
     * @return 默认对象存储服务
     */
    @Bean(name = "defaultStorageService")
    @Primary
    @ConditionalOnProperty(name = "storage.default-type", havingValue = "local", matchIfMissing = true)
    public StorageService localDefaultStorageService(
            @Qualifier("localStorageService") StorageService localStorageService) {
        return localStorageService;
    }
}
