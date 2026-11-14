package com.apex.scaffold.storage.autoconfigure;

import com.apex.scaffold.storage.*;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 独立对象存储自动装配。默认启用本地，云策略必须显式启用；默认策略缺失立即失败。
 * 兼容既有 minio.* 与 storage.volcengine.* 配置，不扫描业务包。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@AutoConfiguration
public class StorageAutoConfiguration {
    private static final List<String> CLOUD_TYPES = List.of("minio", "volcengine", "aliyun-oss", "qiniu");

    /**
     * 创建默认本地策略，文件读写由策略隔离到存储线程池。
     * @param environment 配置环境
     * @return 本地对象存储策略
     */
    @Bean
    @ConditionalOnMissingBean(LocalStorage.class)
    @ConditionalOnProperty(name = "storage.local.enabled", havingValue = "true", matchIfMissing = true)
    LocalStorage localStorage(Environment environment) {
        return new LocalStorage(environment.getProperty("storage.local.base-path", "/root/data/apex/uploads"));
    }

    /**
     * 注册已启用策略，拒绝缺失 SDK、重复策略及不可用默认类型。
     * @param providers 已装配策略
     * @param environment 配置环境
     * @return 严格校验的策略注册表
     * @throws IllegalStateException 已启用云策略未装配
     * @throws IllegalArgumentException 重复策略或默认类型不可用
     */
    @Bean
    @ConditionalOnMissingBean
    StorageRegistry storageRegistry(List<ObjectStorage> providers, Environment environment) {
        for (String type : CLOUD_TYPES) {
            if (environment.getProperty("storage." + type + ".enabled", Boolean.class, false)
                    && providers.stream().noneMatch(provider -> provider.type().equals(type))) {
                throw new IllegalStateException("已启用的存储策略不可用，请检查 SDK: " + type);
            }
        }
        // C3：合并 ServiceLoader 发现的第三方后端（免改 starter 接入新后端）；
        // 重复 type 仍由 StorageRegistry 严格拒绝
        java.util.List<ObjectStorage> all = new java.util.ArrayList<>(providers);
        for (ObjectStorage spi : com.apex.scaffold.storage.StorageProviderLoader.load()) {
            if (providers.stream().noneMatch(existing -> existing.type().equals(spi.type()))) {
                all.add(spi);
            }
        }
        return new StorageRegistry(
                all,
                environment
                        .getProperty("storage.default-type", "local")
                        .toLowerCase(java.util.Locale.ROOT)
                        .replace('_', '-'));
    }

    /**
     * 读取云后端必填配置，不在错误消息中输出配置值。
     *
     * @param environment 配置环境
     * @param prefix 后端配置前缀
     * @param property 属性名称
     * @return 非空配置值
     * @throws IllegalArgumentException 属性缺失或为空白
     */
    private static String required(Environment environment, String prefix, String property) {
        String value = environment.getProperty(prefix + property);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少存储配置: " + prefix + property);
        }
        return value;
    }

    /**
     * MinIO 后端的条件装配：仅在类路径存在客户端且显式启用时生效。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(io.minio.MinioClient.class)
    @ConditionalOnProperty(name = "storage.minio.enabled", havingValue = "true")
    static class MinioConfiguration {
        /**
         * 按新配置前缀或兼容前缀创建受管 MinIO 客户端。
         * @param environment 配置环境
         * @return 由容器管理生命周期的客户端
         * @throws IllegalArgumentException 必填配置缺失
         */
        @Bean
        @ConditionalOnMissingBean
        io.minio.MinioClient minioClient(Environment environment) {
            String prefix = environment.containsProperty("storage.minio.endpoint") ? "storage.minio." : "minio.";
            return io.minio.MinioClient.builder()
                    .endpoint(required(environment, prefix, "endpoint"))
                    .credentials(
                            required(environment, prefix, "access-key"), required(environment, prefix, "secret-key"))
                    .build();
        }

        /**
         * 将受管客户端适配为存储策略，客户端由容器关闭。
         * @param client 受管 MinIO 客户端
         * @return MinIO 存储策略
         */
        @Bean
        @ConditionalOnMissingBean
        MinioStorage minioStorage(io.minio.MinioClient client) {
            return new MinioStorage(client);
        }
    }

    /**
     * 火山引擎 TOS 后端的条件装配：仅在类路径存在客户端且显式启用时生效。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(com.volcengine.tos.TOSV2.class)
    @ConditionalOnProperty(name = "storage.volcengine.enabled", havingValue = "true")
    static class TosConfiguration {
        /**
         * 按显式区域与端点创建受管 TOS 客户端。
         * @param environment 配置环境
         * @return 由容器管理生命周期的客户端
         * @throws IllegalArgumentException 必填配置缺失
         */
        @Bean
        @ConditionalOnMissingBean
        com.volcengine.tos.TOSV2 tosClient(Environment environment) {
            String prefix = "storage.volcengine.";
            return new com.volcengine.tos.TOSV2ClientBuilder()
                    .build(
                            required(environment, prefix, "region"), required(environment, prefix, "endpoint"),
                            required(environment, prefix, "access-key"), required(environment, prefix, "secret-key"));
        }

        /**
         * 将受管 TOS 客户端适配为策略，优先使用应用提供的客户端。
         * @param client 受管 TOS 客户端
         * @return TOS 存储策略
         */
        @Bean
        @ConditionalOnMissingBean
        TosStorage tosStorage(com.volcengine.tos.TOSV2 client) {
            return new TosStorage(client);
        }
    }

    /**
     * 阿里云 OSS 后端的条件装配：仅在类路径存在客户端且显式启用时生效。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(com.aliyun.oss.OSS.class)
    @ConditionalOnProperty(name = "storage.aliyun-oss.enabled", havingValue = "true")
    static class OssConfiguration {
        /**
         * 创建 OSS 客户端，容器销毁时调用 shutdown 释放资源。
         * @param environment 配置环境
         * @return 受管 OSS 客户端
         * @throws IllegalArgumentException 必填配置缺失
         */
        @Bean(destroyMethod = "shutdown")
        @ConditionalOnMissingBean
        com.aliyun.oss.OSS ossClient(Environment environment) {
            String prefix = "storage.aliyun-oss.";
            return new com.aliyun.oss.OSSClientBuilder()
                    .build(
                            required(environment, prefix, "endpoint"),
                            required(environment, prefix, "access-key"),
                            required(environment, prefix, "secret-key"));
        }

        /**
         * 将受管 OSS 客户端适配为策略。
         * @param client 受管 OSS 客户端
         * @return OSS 存储策略
         */
        @Bean
        @ConditionalOnMissingBean
        OssStorage ossStorage(com.aliyun.oss.OSS client) {
            return new OssStorage(client);
        }
    }

    /**
     * 七牛云后端的条件装配：仅在类路径存在上传管理器且显式启用时生效。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(com.qiniu.storage.UploadManager.class)
    @ConditionalOnProperty(name = "storage.qiniu.enabled", havingValue = "true")
    static class QiniuConfiguration {
        /**
         * 创建七牛固定桶策略，区域和可信下载域名均需显式配置。
         * @param environment 配置环境
         * @return 七牛对象存储策略
         * @throws IllegalArgumentException 必填配置缺失或下载域名不安全
         */
        @Bean
        @ConditionalOnMissingBean
        QiniuStorage qiniuStorage(Environment environment) {
            String prefix = "storage.qiniu.";
            return new QiniuStorage(
                    com.qiniu.util.Auth.create(
                            required(environment, prefix, "access-key"), required(environment, prefix, "secret-key")),
                    new com.qiniu.storage.Configuration(
                            com.qiniu.storage.Region.createWithRegionId(required(environment, prefix, "region"))),
                    required(environment, prefix, "bucket"),
                    required(environment, prefix, "download-domain"));
        }
    }
}
