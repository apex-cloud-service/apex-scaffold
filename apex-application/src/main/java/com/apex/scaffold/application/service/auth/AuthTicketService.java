package com.apex.scaffold.application.service.auth;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.common.util.SensitiveDataUtil;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 签发和原子消费短期一次性认证票据。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
/**n * AuthTicketServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class AuthTicketService {

    /**
     * 票据有效期（秒）。
     *
     * <p>认证票据签发后在此时间内有效，超过后自动过期失效。
     * 默认 300 秒（5 分钟），与 {@link #issue(AuthTicketPayload)} 中的过期时间设置保持一致。</p>
     */
    public static final long EXPIRES_IN_SECONDS = 300L;

    private static final String KEY_PREFIX = "apex:auth:ticket:";
    private static final String REVOKED_PHONE_PREFIX = KEY_PREFIX + "revoked:phone:";
    private static final String REVOKED_OPEN_ID_PREFIX = KEY_PREFIX + "revoked:open-id:";

    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 签发五分钟有效的随机票据。
     *
     * <p>生成 UUID 去横杠后作为票据，将 Payload 序列化为 JSON 存入 Redis，
     * 设置 5 分钟过期。用于登录流程中向客户端签发短期认证凭证。</p>
     *
     * @param payload 票据载荷（类型、用户标识等）
     * @return 生成的票据字符串
     */
    public Mono<String> issue(AuthTicketPayload payload) {
        if (payload != null && payload.getIssuedAtEpochMs() == null) {
            payload.setIssuedAtEpochMs(System.currentTimeMillis());
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        return redisTemplate
                .opsForValue()
                .set(KEY_PREFIX + ticket, JsonUtil.toJson(payload), Duration.ofSeconds(EXPIRES_IN_SECONDS))
                .flatMap(saved -> saved
                        ? Mono.just(ticket)
                        : Mono.error(new BusinessException(ResultCode.CACHE_ERROR, "认证票据保存失败")))
                .onErrorMap(
                        error -> !(error instanceof BusinessException),
                        error -> new BusinessException(ResultCode.CACHE_ERROR, "认证票据保存失败"));
    }

    /**
     * 原子读取并删除票据，保证票据一次性使用。
     *
     * <p>使用 Redis 的 GETDEL 命令原子读取并删除票据，
     * 额外校验票据类型是否匹配，防止票据跨场景误用。</p>
     *
     * @param ticket       要消费的票据字符串
     * @param expectedType 期望的票据类型，不匹配则拒绝
     * @return 票据载荷
     */
    public Mono<AuthTicketPayload> consume(String ticket, AuthTicketType expectedType) {
        if (ticket == null || ticket.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.AUTH_TICKET_INVALID));
        }
        return redisTemplate
                .opsForValue()
                .getAndDelete(KEY_PREFIX + ticket)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.AUTH_TICKET_INVALID)))
                .map(json -> JsonUtil.fromJson(json, AuthTicketPayload.class))
                .flatMap(payload -> {
                    if (payload == null || payload.getType() != expectedType) {
                        return Mono.error(new BusinessException(ResultCode.AUTH_TICKET_INVALID));
                    }
                    return isRevoked(payload)
                            .flatMap(revoked -> revoked
                                    ? Mono.error(new BusinessException(ResultCode.AUTH_TICKET_INVALID))
                                    : Mono.just(payload));
                })
                .onErrorMap(
                        error -> !(error instanceof BusinessException),
                        error -> new BusinessException(ResultCode.CACHE_ERROR, "认证票据读取失败"));
    }

    /**
     * 吊销某个手机号或微信身份在注销前签发的临时票据。
     *
     * @param phone 手机号，可为空
     * @param openId 微信 openId，可为空
     * @return 完成信号
     */
    public Mono<Void> revokeIdentity(String phone, String openId) {
        String marker = String.valueOf(System.currentTimeMillis());
        Mono<Boolean> phoneMarker = phone == null || phone.isBlank()
                ? Mono.just(true)
                : redisTemplate
                        .opsForValue()
                        .set(revokedPhoneKey(phone), marker, Duration.ofSeconds(EXPIRES_IN_SECONDS));
        Mono<Boolean> openIdMarker = openId == null || openId.isBlank()
                ? Mono.just(true)
                : redisTemplate
                        .opsForValue()
                        .set(revokedOpenIdKey(openId), marker, Duration.ofSeconds(EXPIRES_IN_SECONDS));
        return Mono.when(phoneMarker, openIdMarker).then();
    }

    /**
     * 判断票据载荷是否已被身份级吊销标记覆盖。
     *
     * <p>同时检查手机号与 openId 两类标记，任一标记的写入时间晚于票据签发时间
     * 即视为已吊销。</p>
     *
     * @param payload 票据载荷
     * @return 已吊销返回 true
     */
    private Mono<Boolean> isRevoked(AuthTicketPayload payload) {
        long issuedAt = payload.getIssuedAtEpochMs() == null ? 0L : payload.getIssuedAtEpochMs();
        Mono<Boolean> phone =
                markerContains(payload.getPhone() == null ? null : revokedPhoneKey(payload.getPhone()), issuedAt);
        Mono<Boolean> openId =
                markerContains(payload.getOpenId() == null ? null : revokedOpenIdKey(payload.getOpenId()), issuedAt);
        return Mono.zip(phone, openId).map(tuple -> tuple.getT1() || tuple.getT2());
    }

    /**
     * 读取吊销标记并判断其写入时间是否晚于票据签发时间。
     *
     * @param key 吊销标记键，为空表示无需检查
     * @param issuedAt 票据签发时间戳（毫秒）
     * @return 标记存在且更新返回 true；标记缺失或不可解析返回 false
     */
    private Mono<Boolean> markerContains(String key, long issuedAt) {
        if (key == null) {
            return Mono.just(false);
        }
        return Mono.defer(() -> {
                    Mono<String> marker = redisTemplate.opsForValue().get(key);
                    return marker == null ? Mono.empty() : marker;
                })
                .map(value -> {
                    try {
                        return issuedAt < Long.parseLong(value);
                    } catch (NumberFormatException error) {
                        return false;
                    }
                })
                .defaultIfEmpty(false);
    }

    /**
     * 生成手机号吊销标记键（敏感指纹化）。
     *
     * @param phone 手机号
     * @return Redis 标记键
     */
    private String revokedPhoneKey(String phone) {
        return REVOKED_PHONE_PREFIX + SensitiveDataUtil.fingerprint(phone).substring("sha256:".length());
    }

    /**
     * 生成 openId 吊销标记键（敏感指纹化）。
     *
     * @param openId 微信 openId
     * @return Redis 标记键
     */
    private String revokedOpenIdKey(String openId) {
        return REVOKED_OPEN_ID_PREFIX + SensitiveDataUtil.fingerprint(openId).substring("sha256:".length());
    }

    /**
     * 原子消费票据并执行后续业务逻辑。
     *
     * <p>组合 {@link #consume(String, AuthTicketType)} 与业务回调，
     * 确保票据仅能被消费一次且在有效期内。</p>
     *
     * @param <T>          响应类型
     * @param ticket       票据字符串
     * @param expectedType 期望的票据类型
     * @param deviceId     设备标识
     * @param responseType 响应类型
     * @param handler      票据验证通过后的业务回调
     * @return 回调返回的 Mono 结果
     */
    public <T> Mono<T> execute(
            String ticket,
            AuthTicketType expectedType,
            String deviceId,
            Class<T> responseType,
            Function<AuthTicketPayload, Mono<T>> handler) {
        return consume(ticket, expectedType).flatMap(handler);
    }
}
