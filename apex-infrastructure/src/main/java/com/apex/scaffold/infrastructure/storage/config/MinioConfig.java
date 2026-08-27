package com.apex.scaffold.infrastructure.storage.config;

import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * MinIO 客户端 Bean 配置。
 *
 * <p>在满足 {@code storage.default-type=minio} 条件时创建 {@link MinioClient}，
 * 通过 {@code minio.endpoint}、{@code minio.access-key}、{@code minio.secret-key}
 * 连接目标 MinIO 服务。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@AutoConfiguration
public class MinioConfig {
    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    /** 构造 MinIO 配置类 */
    public MinioConfig() {}

    /**
     * 创建MinIO客户端
     *
     * @param endpoint   MinIO 服务地址
     * @param accessKey  访问密钥
     * @param secretKey  秘密密钥
     * @return MinioClient实例
     */
    @Bean
    @ConditionalOnExpression("!'${minio.access-key:}'.isEmpty() and !'${minio.secret-key:}'.isEmpty()")
    public MinioClient minioClient(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey) {
        log.info("初始化MinIO客户端, endpoint: {}", endpoint);
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
