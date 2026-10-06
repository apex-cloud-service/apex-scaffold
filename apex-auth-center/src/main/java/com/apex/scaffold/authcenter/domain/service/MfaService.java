package com.apex.scaffold.authcenter.domain.service;

import com.apex.scaffold.authcenter.domain.model.MfaConfig;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多因素认证(MFA)领域服务接口
 *
 * <p>所有用户级操作均显式接收 {@code tenantId}，确保查询、缓存键与验证码 Redis Key
 * 均按租户隔离，避免跨租户数据越权访问。
 *
 * @author Apex Team
 * @since 1.2.0
 */
public interface MfaService {

    /**
     * 启用TOTP多因素认证，生成密钥与二维码并落库为待激活状态
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return TOTP密钥和二维码URL
     */
    Mono<TotpSetupInfo> enableTotp(String userId, String tenantId);

    /**
     * 验证并激活TOTP，验证通过后将状态置为激活并生成备用验证码
     *
     * @param userId 用户ID
     * @param code 验证码
     * @param tenantId 租户ID
     * @return 激活结果和备用验证码
     */
    Mono<MfaActivationResult> verifyAndActivateTotp(String userId, String code, String tenantId);

    /**
     * 启用短信MFA，绑定手机号并置为激活状态
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param phoneNumber 手机号码
     * @return 已激活的短信MFA配置
     */
    Mono<MfaConfig> enableSms(String userId, String tenantId, String phoneNumber);

    /**
     * 启用邮箱MFA，绑定邮箱并置为激活状态
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @param email 邮箱地址
     * @return 已激活的邮箱MFA配置
     */
    Mono<MfaConfig> enableEmail(String userId, String tenantId, String email);

    /**
     * 禁用指定类型的MFA，将配置状态置为禁用并记录审计日志
     *
     * @param userId 用户ID
     * @param type 要禁用的MFA类型
     * @param tenantId 租户ID
     * @return 完成信号
     */
    Mono<Void> disableMfa(String userId, MfaConfig.MfaType type, String tenantId);

    /**
     * 验证MFA代码（TOTP/SMS/Email），并更新失败次数与最后验证时间
     *
     * @param userId 用户ID
     * @param code 验证码
     * @param type MFA类型
     * @param tenantId 租户ID
     * @return 验证是否成功
     */
    Mono<Boolean> verifyMfaCode(String userId, String code, MfaConfig.MfaType type, String tenantId);

    /**
     * 发送MFA验证码（SMS/Email），生成6位数字验证码并按类型下发
     *
     * @param userId 用户ID
     * @param type MFA类型
     * @param tenantId 租户ID
     * @return 完成信号
     */
    Mono<Void> sendMfaCode(String userId, MfaConfig.MfaType type, String tenantId);

    /**
     * 查询用户的全部MFA配置
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 该用户的MFA配置流
     */
    Flux<MfaConfig> getUserMfaConfigs(String userId, String tenantId);

    /**
     * 检查用户是否启用了任一处于激活状态的MFA
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 存在激活状态的MFA配置时为true，否则为false
     */
    Mono<Boolean> isMfaEnabled(String userId, String tenantId);

    /**
     * 重新生成TOTP备用验证码，覆盖原有备用码并记录审计日志
     *
     * @param userId 用户ID
     * @param tenantId 租户ID
     * @return 新的备用验证码结果
     */
    Mono<BackupCodesResult> regenerateBackupCodes(String userId, String tenantId);

    /**
     * 验证备用验证码，验证通过后该备用码将被作废
     *
     * @param userId 用户ID
     * @param code 备用验证码
     * @param tenantId 租户ID
     * @return 验证通过返回true，验证失败或配置不存在返回false
     */
    Mono<Boolean> verifyBackupCode(String userId, String code, String tenantId);

    /**
     * 解锁被锁定的MFA，重置失败尝试次数并记录审计日志
     *
     * @param userId 被解锁的用户ID
     * @param type 要解锁的MFA类型
     * @param operator 执行解锁操作的运维人员标识
     * @param tenantId 租户ID
     * @return 完成信号
     */
    Mono<Void> unlockMfa(String userId, MfaConfig.MfaType type, String operator, String tenantId);

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
