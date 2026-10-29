package com.apex.scaffold.infrastructure.chuanglan.service;

import com.apex.scaffold.domain.gateway.ChuanglanGateway;
import com.apex.scaffold.domain.service.SmsCodePurpose;
import com.apex.scaffold.domain.service.SmsCodePurposeService;
import com.apex.scaffold.domain.service.SmsCodeService;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 创蓝短信验证码服务实现
 *
 * <p>实现 {@link SmsCodeService} 接口，提供基于创蓝253短信平台的验证码发送和验证功能。
 * 验证码使用 Redis 存储，并通过 {@link ChuanglanGateway} 调用创蓝短信API发送。
 *
 * <h2>P0-CL-1修复: 内存缓存迁移至Redis</h2>
 * <p>将原 {@link java.util.concurrent.ConcurrentHashMap} 内存缓存替换为
 * {@link ReactiveStringRedisTemplate} Redis存储，解决以下问题：</p>
 * <ul>
 *   <li>多实例部署时验证码不共享的问题</li>
 *   <li>应用重启导致所有验证码丢失的问题</li>
 *   <li>无需维护 ScheduledExecutorService 线程池做过期清理</li>
 * </ul>
 *
 * <h2>验证码策略</h2>
 * <ul>
 *   <li>生成策略：6位随机数字，范围 000000 ~ 999999</li>
 *   <li>有效期：5分钟（300秒），通过 Redis TTL 自动过期</li>
 *   <li>冷却期：60秒，冷却期内拒绝重新发送，保护短信费用</li>
 *   <li>复用策略：冷却期过后、验证码未过期时，复用已有验证码重新发送</li>
 *   <li>使用策略：一次性使用，验证成功后立即移除</li>
 *   <li>缓存策略：Redis SET + EXPIRE，利用 TTL 自动过期</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see SmsCodeService
 * @see ChuanglanGateway
 */
@Slf4j
@RequiredArgsConstructor
@Service("chuanglanSmsCodeService")
public class ChuanglanSmsCodeServiceImpl implements SmsCodeService, SmsCodePurposeService {

    /**
     * 原子比较并删除验证码的 Redis 脚本。
     *
     * <p>返回 {@code 1} 表示匹配且已核销，{@code 0} 表示不存在，{@code -1} 表示不匹配。
     * 比较与删除在同一条 Redis 命令中完成，避免同一验证码被并发请求重复使用。</p>
     */
    private static final RedisScript<Long> VERIFY_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]); "
                    + "if not value then return 0; end; "
                    + "if value == ARGV[1] then redis.call('DEL', KEYS[1]); return 1; end; "
                    + "return -1;",
            Long.class);

    /**
     * Redis 验证码 Key 前缀
     */
    private static final String CACHE_PREFIX = "cl:sms:code:";

    /**
     * Redis 冷却期 Key 前缀
     *
     * <p>防止60秒内重复发送，前端按钮在此期间禁用。
     */
    private static final String COOLDOWN_PREFIX = "cl:sms:cooldown:";

    /**
     * 验证码有效期（5分钟）
     */
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    /**
     * 发送冷却期（60秒）
     *
     * <p>冷却期内不生成新验证码，直接复用 Redis 中未过期的验证码重新发送。
     */
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    /**
     * 6位随机数字用于生成验证码
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Redis 响应式字符串模板
     */
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 创蓝短信网关
     */
    private final ChuanglanGateway chuanglanGateway;

    /**
     * 发送短信验证码至指定手机号
     *
     * <p>发送流程分为三个阶段：
     * <ol>
     *   <li><b>冷却期检查</b>：检查 {@code cl:sms:cooldown:{phone}} 是否存在，
     *       若存在则拒绝发送，返回错误提示"请60秒后重试"</li>
     *   <li><b>验证码复用</b>：检查 {@code cl:sms:code:{phone}} 是否存在，
     *       若存在（未过期）则复用该验证码重新发送</li>
     *   <li><b>生成新验证码</b>：若验证码已过期（key 不存在），
     *       生成新的6位数字验证码，存入 Redis 并发送</li>
     * </ol>
     *
     * <p>发送成功后设置冷却期标记（TTL=60秒），
     * 发送失败时：复用模式不删除验证码缓存，新生成模式删除验证码缓存。
     *
     * @param phone 目标手机号，中国大陆11位手机号码
     * @return 发送完成的空Mono，冷却期内返回Mono.error
     */
    @Override
    public Mono<Void> sendAndStore(String phone) {
        return sendAndStore(phone, SmsCodePurpose.LOGIN);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Void> sendAndStore(String phone, SmsCodePurpose purpose) {
        if (phone == null || phone.isBlank()) {
            log.warn("手机号为空，跳过发送验证码");
            return Mono.empty();
        }

        String cacheKey = codeKey(phone, purpose);
        String cooldownKey = cooldownKey(phone, purpose);

        // 1. 冷却期检查
        return redisTemplate
                .hasKey(cooldownKey)
                .flatMap(inCooldown -> {
                    if (Boolean.TRUE.equals(inCooldown)) {
                        log.warn("短信验证码冷却期未过，拒绝发送");
                        return Mono.<String>error(new RuntimeException("请60秒后重试"));
                    }
                    // 2. 检查是否存在未过期的验证码
                    return redisTemplate.opsForValue().get(cacheKey);
                })
                // 3. 验证码未过期，复用发送
                .flatMap(existingCode -> {
                    log.debug("验证码未过期，复用发送");
                    return doSendSms(phone, existingCode, cacheKey, cooldownKey, false);
                })
                // 4. 验证码已过期，生成新验证码并发送
                .switchIfEmpty(Mono.defer(() -> {
                    String newCode = generateCode();
                    log.debug("验证码已过期，已生成新验证码");
                    return redisTemplate
                            .opsForValue()
                            .set(cacheKey, newCode, CODE_TTL)
                            .flatMap(stored -> {
                                if (!Boolean.TRUE.equals(stored)) {
                                    return Mono.error(new IllegalStateException("验证码安全存储失败"));
                                }
                                return doSendSms(phone, newCode, cacheKey, cooldownKey, true);
                            });
                }));
    }

    /**
     * 调用创蓝短信网关发送验证码，成功后设置冷却期
     *
     * <p>发送失败时：复用模式保留 Redis 中已有验证码（仍可复用），
     * 新生成模式删除验证码缓存（清理无效数据）。
     *
     * @param phone            手机号
     * @param code             验证码
     * @param cacheKey         Redis 验证码 key
     * @param cooldownKey      Redis 冷却期 key
     * @param deleteCodeOnFail 发送失败时是否删除验证码缓存
     * @return 发送完成的 Mono
     */
    private Mono<Void> doSendSms(
            String phone, String code, String cacheKey, String cooldownKey, boolean deleteCodeOnFail) {
        return chuanglanGateway
                .sendSms(phone, code)
                .flatMap(result -> {
                    if (!result.isSuccess()) {
                        log.error("验证码短信发送失败, errorCode: {}", result.getErrorCode());
                        return Mono.error(new IllegalStateException("短信供应商发送失败"));
                    }
                    log.debug("短信发送成功, msgId: {}", result.getMsgId());
                    return redisTemplate
                            .opsForValue()
                            .set(cooldownKey, "1", COOLDOWN_TTL)
                            .then();
                })
                .onErrorResume(error -> {
                    log.error("验证码短信发送异常, errorType: {}", error.getClass().getSimpleName());
                    if (deleteCodeOnFail) {
                        return redisTemplate.opsForValue().delete(cacheKey).then(Mono.error(error));
                    }
                    return Mono.error(error);
                })
                .then();
    }

    /**
     * 验证用户输入的短信验证码
     *
     * <p>P0-CL-1修复: 从 Redis 获取验证码进行比对，验证成功后立即删除（一次性使用）。
     * 验证码不存在、已过期或输入错误均返回验证失败
     *
     * @param phone 手机号
     * @param code 用户输入的验证码
     * @return 验证通过返回true，验证失败返回false
     */
    @Override
    public Mono<Boolean> validate(String phone, String code) {
        return validate(phone, code, SmsCodePurpose.LOGIN);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Boolean> validate(String phone, String code, SmsCodePurpose purpose) {
        if (phone == null || phone.isBlank()) {
            log.warn("手机号为空，验证码验证失败");
            return Mono.just(false);
        }

        if (code == null || code.isBlank()) {
            log.warn("验证码为空，验证失败");
            return Mono.just(false);
        }

        String cacheKey = codeKey(phone, purpose);

        return redisTemplate
                .execute(VERIFY_AND_DELETE_SCRIPT, List.of(cacheKey), code)
                .next()
                .defaultIfEmpty(0L)
                .map(result -> result == 1L)
                .doOnNext(valid -> log.debug("验证码原子核销结果: {}", valid ? "success" : "rejected"));
    }

    /** 登录用途保留历史键格式，其他用途使用独立命名空间。 */
    private String codeKey(String phone, SmsCodePurpose purpose) {
        if (purpose == null || purpose == SmsCodePurpose.LOGIN) {
            return CACHE_PREFIX + phone;
        }
        return CACHE_PREFIX + purpose.key() + ":" + phone;
    }

    /** 登录用途保留历史键格式，其他用途使用独立命名空间。 */
    private String cooldownKey(String phone, SmsCodePurpose purpose) {
        if (purpose == null || purpose == SmsCodePurpose.LOGIN) {
            return COOLDOWN_PREFIX + phone;
        }
        return COOLDOWN_PREFIX + purpose.key() + ":" + phone;
    }

    /**
     * 生成6位随机数字验证码
     *
     * @return 6位数字格式的验证码字符串（如"001234"）
     */
    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
