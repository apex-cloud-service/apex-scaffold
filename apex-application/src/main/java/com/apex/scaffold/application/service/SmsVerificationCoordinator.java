package com.apex.scaffold.application.service;

import com.apex.scaffold.application.config.SmsSendLimitProperties;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.SensitiveDataUtil;
import com.apex.scaffold.domain.service.SmsCodePurpose;
import com.apex.scaffold.domain.service.SmsCodePurposeService;
import com.apex.scaffold.domain.service.SmsCodeService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 短信验证码发送协调器。
 *
 * <p>该协调器从 {@link AuthService} 中拆出手机号维度限流和真实供应商发送职责。限流计数
 * 使用 Redis Lua 脚本原子执行 INCR 与首次 EXPIRE，避免并发请求绕过阈值。验证码本身由
 * {@link SmsCodeService} 生成、发送并保存，协调器不会接触或记录验证码明文。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.2.0
 */
@Slf4j
@Service
public class SmsVerificationCoordinator {

    /** 原子增加手机号发送次数，并在首次访问时设置窗口 TTL。 */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]); "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
                    + "return count;",
            Long.class);

    /** 发送限流键前缀。 */
    private static final String RATE_LIMIT_KEY_PREFIX = "security:sms:send:";

    /** 真实短信验证码服务。 */
    private final SmsCodeService smsCodeService;

    /** Redis 响应式模板。 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 手机号维度限流配置。 */
    private final SmsSendLimitProperties properties;

    /**
     * 创建短信验证码发送协调器。
     *
     * @param smsCodeService 真实短信验证码服务
     * @param redisTemplate Redis 响应式模板
     * @param properties 限流配置
     */
    public SmsVerificationCoordinator(
            @Qualifier("defaultSmsCodeService") SmsCodeService smsCodeService,
            ReactiveStringRedisTemplate redisTemplate,
            SmsSendLimitProperties properties) {
        this.smsCodeService = smsCodeService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * 检查手机号限流后发送验证码。
     *
     * @param phone 目标手机号
     * @return 发送完成信号
     */
    public Mono<Void> send(String phone) {
        return send(phone, SmsCodePurpose.LOGIN);
    }

    /**
     * 检查手机号和业务用途维度的限流后发送验证码。
     *
     * @param phone 目标手机号
     * @param purpose 验证码用途
     * @return 发送完成信号
     */
    public Mono<Void> send(String phone, SmsCodePurpose purpose) {
        SmsCodePurpose normalizedPurpose = purpose == null ? SmsCodePurpose.LOGIN : purpose;
        String phoneFingerprint = SensitiveDataUtil.fingerprint(phone);
        String key = normalizedPurpose == SmsCodePurpose.LOGIN
                ? RATE_LIMIT_KEY_PREFIX + phoneFingerprint.substring("sha256:".length())
                : RATE_LIMIT_KEY_PREFIX
                        + normalizedPurpose.key()
                        + ":"
                        + phoneFingerprint.substring("sha256:".length());
        return redisTemplate
                .execute(RATE_LIMIT_SCRIPT, List.of(key), String.valueOf(properties.getWindowSeconds()))
                .next()
                .switchIfEmpty(Mono.error(new IllegalStateException("短信限流存储不可用")))
                .flatMap(count -> {
                    if (count > properties.getMaxAttempts()) {
                        log.warn("手机号短信发送超限, phoneFingerprint: {}, count: {}", phoneFingerprint, count);
                        return Mono.error(new BusinessException(ResultCode.RATE_LIMIT));
                    }
                    log.info("短信发送限流检查通过, phone: {}", SensitiveDataUtil.maskPhone(phone));
                    if (smsCodeService instanceof SmsCodePurposeService purposeService) {
                        return purposeService.sendAndStore(phone, normalizedPurpose);
                    }
                    // 兼容旧的测试替身或第三方适配器：登录用途仍走旧方法。
                    return smsCodeService.sendAndStore(phone);
                });
    }

    /**
     * 校验并核销指定用途的验证码。
     *
     * @param phone 手机号
     * @param code 验证码
     * @param purpose 验证码用途
     * @return 验证是否通过
     */
    public Mono<Boolean> validate(String phone, String code, SmsCodePurpose purpose) {
        SmsCodePurpose normalizedPurpose = purpose == null ? SmsCodePurpose.LOGIN : purpose;
        if (smsCodeService instanceof SmsCodePurposeService purposeService) {
            return purposeService.validate(phone, code, normalizedPurpose);
        }
        return smsCodeService.validate(phone, code);
    }
}
