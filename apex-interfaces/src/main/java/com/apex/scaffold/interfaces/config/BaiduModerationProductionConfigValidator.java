package com.apex.scaffold.interfaces.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 防止生产环境开放图片上传时漏配百度云机审。
 *
 * <p>正脸档案、生活照与兼容的通用图片入口都会在写入对象存储前调用百度云；
 * 只有生活照允许进入人工复核，因此隔离桶只在生活照入口开启时强制校验。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
@Profile("prod")
public class BaiduModerationProductionConfigValidator extends AbstractProductionConfigValidator {

    private final boolean fileUploadEnabled;
    private final boolean votePhotoEnabled;
    private final boolean baiduEnabled;
    private final String apiKey;
    private final String secretKey;
    private final int strategyId;
    private final String quarantineBucket;

    /**
     * 构造百度云图片审核生产配置校验器。
     *
     * @param fileUploadEnabled 是否开启通用图片上传
     * @param votePhotoEnabled 是否开启生活照上传
     * @param baiduEnabled 是否开启百度云图片审核
     * @param apiKey 百度云 API Key
     * @param secretKey 百度云 Secret Key
     * @param strategyId 百度云审核策略 ID
     * @param quarantineBucket 人工审核隔离桶
     */
    public BaiduModerationProductionConfigValidator(
            @Value("${file.upload.enabled:false}") boolean fileUploadEnabled,
            @Value("${file.upload.vote-photo-enabled:false}") boolean votePhotoEnabled,
            @Value("${image.moderation.baidu.enabled:false}") boolean baiduEnabled,
            @Value("${image.moderation.baidu.api-key:${BAIDU_IMAGE_MODERATION_API_KEY:}}") String apiKey,
            @Value("${image.moderation.baidu.secret-key:${BAIDU_IMAGE_MODERATION_SECRET_KEY:}}") String secretKey,
            @Value("${image.moderation.baidu.strategy-id:0}") int strategyId,
            @Value("${image.moderation.quarantine-bucket:}") String quarantineBucket) {
        this.fileUploadEnabled = fileUploadEnabled;
        this.votePhotoEnabled = votePhotoEnabled;
        this.baiduEnabled = baiduEnabled;
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.strategyId = strategyId;
        this.quarantineBucket = quarantineBucket;
    }

    /**
     * 执行 {@code doValidate} 逻辑。
     */
    @Override
    protected void doValidate() {
        if (!fileUploadEnabled) {
            return;
        }
        if (!baiduEnabled) {
            throw invalid("图片上传已开启，但百度云图片审核未开启");
        }
        if (isBlank(apiKey) || isBlank(secretKey)) {
            throw invalid("图片上传已开启，但百度云 API Key/Secret Key 未配置");
        }
        if (strategyId <= 0) {
            throw invalid("图片上传已开启，但百度云审核策略 ID 未配置");
        }
        if (votePhotoEnabled && isBlank(quarantineBucket)) {
            throw invalid("生活照上传已开启，但人工审核隔离桶未配置");
        }
    }

    /**
     * 执行 {@code prefix} 逻辑。
     *
     * @return 执行结果
     */
    @Override
    protected String prefix() {
        return "生产环境图片审核配置错误：";
    }
}
