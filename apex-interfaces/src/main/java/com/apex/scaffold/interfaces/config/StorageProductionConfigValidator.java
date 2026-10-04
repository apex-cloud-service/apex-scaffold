package com.apex.scaffold.interfaces.config;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 生产环境对象存储配置校验器。
 *
 * <p>启动时校验默认存储类型、固定桶和对应后端配置，阻止生产环境使用未启用的存储实现、
 * 默认账号或空配置。校验只检查配置闭环，不在启动线程发起外部网络请求。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@Profile("prod")
public class StorageProductionConfigValidator extends AbstractProductionConfigValidator {

    private static final String TYPE_MINIO = "minio";
    private static final String TYPE_VOLCENGINE = "volcengine";
    private static final String TYPE_LOCAL = "local";

    private final String defaultType;
    private final String bucketName;
    private final String minioEndpoint;
    private final String minioAccessKey;
    private final String minioSecretKey;
    private final boolean localEnabled;
    private final String localBasePath;
    private final boolean volcEngineEnabled;
    private final String volcEngineEndpoint;
    private final String volcEngineRegion;
    private final String volcEngineAccessKey;
    private final String volcEngineSecretKey;

    /**
     * 创建生产对象存储配置校验器。
     *
     * @param defaultType 默认存储类型
     * @param bucketName 服务端固定桶名
     * @param minioEndpoint MinIO 服务地址
     * @param minioAccessKey MinIO 访问密钥
     * @param minioSecretKey MinIO 密钥
     * @param localEnabled Local 存储启用开关
     * @param localBasePath Local 存储根目录
     * @param volcEngineEnabled TOS 启用开关
     * @param volcEngineEndpoint TOS 服务地址
     * @param volcEngineRegion TOS 地域
     * @param volcEngineAccessKey TOS 访问密钥
     * @param volcEngineSecretKey TOS 密钥
     */
    public StorageProductionConfigValidator(
            @Value("${storage.default-type:local}") String defaultType,
            @Value("${storage.bucket-name:}") String bucketName,
            @Value("${minio.endpoint:}") String minioEndpoint,
            @Value("${minio.access-key:}") String minioAccessKey,
            @Value("${minio.secret-key:}") String minioSecretKey,
            @Value("${storage.local.enabled:true}") boolean localEnabled,
            @Value("${storage.local.base-path:./data/storage}") String localBasePath,
            @Value("${storage.volcengine.enabled:false}") boolean volcEngineEnabled,
            @Value("${storage.volcengine.endpoint:}") String volcEngineEndpoint,
            @Value("${storage.volcengine.region:}") String volcEngineRegion,
            @Value("${storage.volcengine.access-key:}") String volcEngineAccessKey,
            @Value("${storage.volcengine.secret-key:}") String volcEngineSecretKey) {
        this.defaultType = defaultType;
        this.bucketName = bucketName;
        this.minioEndpoint = minioEndpoint;
        this.minioAccessKey = minioAccessKey;
        this.minioSecretKey = minioSecretKey;
        this.localEnabled = localEnabled;
        this.localBasePath = localBasePath;
        this.volcEngineEnabled = volcEngineEnabled;
        this.volcEngineEndpoint = volcEngineEndpoint;
        this.volcEngineRegion = volcEngineRegion;
        this.volcEngineAccessKey = volcEngineAccessKey;
        this.volcEngineSecretKey = volcEngineSecretKey;
    }

    /**
     * 校验生产对象存储配置。
     *
     * @throws IllegalStateException 默认存储配置缺失、不安全或互相矛盾时抛出
     */
    @Override
    protected void doValidate() {
        requireText("STORAGE_BUCKET_NAME", bucketName);
        String normalizedType = defaultType == null ? "" : defaultType.trim().toLowerCase(Locale.ROOT);
        if (TYPE_MINIO.equals(normalizedType)) {
            validateMinio();
        } else if (TYPE_VOLCENGINE.equals(normalizedType)) {
            validateVolcEngine();
        } else if (TYPE_LOCAL.equals(normalizedType)) {
            validateLocal();
        } else if (normalizedType.isEmpty()) {
            throw invalid("STORAGE_DEFAULT_TYPE 不能为空");
        } else {
            throw invalid("STORAGE_DEFAULT_TYPE 仅支持 local、minio 或 volcengine");
        }
    }

    /**
     * 校验 Local 存储实现已启用。
     *
     */
    private void validateLocal() {
        if (!localEnabled) {
            throw invalid("选择 local 时 STORAGE_LOCAL_ENABLED 必须为 true");
        }
        requireText("STORAGE_LOCAL_BASE_PATH", localBasePath);
    }

    /** 校验 MinIO 地址与凭据。 */
    private void validateMinio() {
        validateHttpUrl("MINIO_ENDPOINT", minioEndpoint);
        requireSecret("MINIO_ACCESS_KEY", minioAccessKey);
        requireSecret("MINIO_SECRET_KEY", minioSecretKey);
    }

    /** 校验火山引擎 TOS 地址、地域与凭据。 */
    private void validateVolcEngine() {
        if (!volcEngineEnabled) {
            throw invalid("选择 volcengine 时 STORAGE_VOLCENGINE_ENABLED 必须为 true");
        }
        validateHttpUrl("VOLCENGINE_ENDPOINT", volcEngineEndpoint);
        requireText("VOLCENGINE_REGION", volcEngineRegion);
        requireSecret("VOLCENGINE_ACCESS_KEY", volcEngineAccessKey);
        requireSecret("VOLCENGINE_SECRET_KEY", volcEngineSecretKey);
    }

    /**
     * 返回校验错误信息的前缀。
     *
     * @return 错误信息前缀
     */
    @Override
    protected String prefix() {
        return "生产环境对象存储配置错误：";
    }
}
