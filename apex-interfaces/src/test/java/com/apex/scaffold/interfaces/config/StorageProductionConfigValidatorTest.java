package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link StorageProductionConfigValidator} 单元测试。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@DisplayName("生产对象存储配置校验")
class StorageProductionConfigValidatorTest {

    /** 验证完整且非默认的 MinIO 配置可以通过。 */
    @Test
    void shouldAcceptSecureMinioConfiguration() {
        StorageProductionConfigValidator validator =
                validator("minio", "apex", "https://minio.internal", "service-access", "service-secret", false);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    /** 验证生产环境拒绝仓库历史默认账号。 */
    @Test
    void shouldRejectDefaultMinioCredentials() {
        StorageProductionConfigValidator validator =
                validator("minio", "apex", "https://minio.internal", "minioadmin", "minioadmin", false);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("默认或占位凭据");
    }

    /** 验证选择 TOS 时必须同步打开 TOS 实现。 */
    @Test
    void shouldRejectDisabledVolcEngineBackend() {
        StorageProductionConfigValidator validator = validator("volcengine", "apex", "", "", "", false);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_VOLCENGINE_ENABLED");
    }

    /** 验证完整的 TOS 配置可以作为生产默认存储。 */
    @Test
    void shouldAcceptVolcEngineConfiguration() {
        StorageProductionConfigValidator validator = validator("volcengine", "apex", "", "", "", true);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    /** 验证生产环境可以使用已启用的本地文件系统作为默认存储。 */
    @Test
    void shouldAcceptEnabledLocalStorage() {
        StorageProductionConfigValidator validator = validator("local", "apex", "", "", "", false);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    /** 验证选择 Local 时必须同步启用本地存储实现。 */
    @Test
    void shouldRejectDisabledLocalStorage() {
        StorageProductionConfigValidator validator = new StorageProductionConfigValidator(
                "local",
                "apex",
                "",
                "",
                "",
                false,
                "/data/apex/uploads",
                false,
                "https://tos-cn-beijing.volces.com",
                "cn-beijing",
                "volc-access",
                "volc-secret");

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STORAGE_LOCAL_ENABLED");
    }

    /**
     * 创建测试使用的配置校验器。
     *
     * @param type 默认存储类型
     * @param bucket 桶名
     * @param minioEndpoint MinIO 地址
     * @param minioAccess MinIO 访问密钥
     * @param minioSecret MinIO 密钥
     * @param volcEnabled TOS 开关
     * @return 配置校验器
     */
    private StorageProductionConfigValidator validator(
            String type,
            String bucket,
            String minioEndpoint,
            String minioAccess,
            String minioSecret,
            boolean volcEnabled) {
        return new StorageProductionConfigValidator(
                type,
                bucket,
                minioEndpoint,
                minioAccess,
                minioSecret,
                true,
                "/data/apex/uploads",
                volcEnabled,
                "https://tos-cn-beijing.volces.com",
                "cn-beijing",
                "volc-access",
                "volc-secret");
    }
}
