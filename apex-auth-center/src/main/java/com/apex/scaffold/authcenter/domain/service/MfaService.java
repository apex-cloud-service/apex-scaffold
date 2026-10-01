package com.apex.scaffold.authcenter.domain.service;

import com.apex.scaffold.authcenter.domain.model.MfaConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多因素认证(MFA)领域服务接口
 * @author Apex Team
 * @since 1.2.0
 */
public interface MfaService {

    /**
     * 启用TOTP多因素认证
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return TOTP密钥和二维码URL
     */
    Mono<TotpSetupInfo> enableTotp(String userId, String tenantId);

    /**
     * 验证并激活TOTP
     *
     * @param userId 用户ID
     * @param code 验证码
     * @return 激活结果和备用验证码
     */
    Mono<MfaActivationResult> verifyAndActivateTotp(String userId, String code);

    /**
     * 启用短信MFA
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param tenantId TODO
     * @param phoneNumber TODO
     * @return TODO
     */
    Mono<MfaConfig> enableSms(String userId, String tenantId, String phoneNumber);

    /**
     * 启用邮箱MFA
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param tenantId TODO
     * @param email TODO
     * @return TODO
     */
    Mono<MfaConfig> enableEmail(String userId, String tenantId, String email);

    /**
     * 禁用MFA
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param type TODO
     * @return TODO
     */
    Mono<Void> disableMfa(String userId, MfaConfig.MfaType type);

    /**
     * 验证MFA代码
     *
     * @param userId 用户ID
     * @param code 验证码
     * @param type MFA类型
     * @return 验证是否成功
     */
    Mono<Boolean> verifyMfaCode(String userId, String code, MfaConfig.MfaType type);

    /**
     * 发送MFA验证码（SMS/Email）
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param type TODO
     * @return TODO
     */
    Mono<Void> sendMfaCode(String userId, MfaConfig.MfaType type);

    /**
     * 查询用户的MFA配置
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Flux<MfaConfig> getUserMfaConfigs(String userId);

    /**
     * 检查用户是否启用了MFA
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Mono<Boolean> isMfaEnabled(String userId);

    /**
     * 重新生成备用验证码
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @return TODO
     */
    Mono<BackupCodesResult> regenerateBackupCodes(String userId);

    /**
     * 验证备用验证码
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param code TODO
     * @return TODO
     */
    Mono<Boolean> verifyBackupCode(String userId, String code);

    /**
     * 解锁被锁定的MFA
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     * @return TODO
     */
    /**
     * TODO: Add method description
     *
     * @param userId TODO
     * @param type TODO
     * @param operator TODO
     * @return TODO
     */
    Mono<Void> unlockMfa(String userId, MfaConfig.MfaType type, String operator);

    /**
     * TOTP设置信息
     */
    record TotpSetupInfo(String secret, String qrCodeUrl, String manualEntryKey) {}

    /**
     * MFA激活结果
     */
    record MfaActivationResult(boolean success, String[] backupCodes) {}

    /**
     * 备用验证码结果
     */
    record BackupCodesResult(String[] codes) {}
}
