package com.apex.scaffold.application.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 认证票据服务单元测试。
 *
 * @author weiqiang.ling
 */
@ExtendWith(MockitoExtension.class)
class AuthTicketServiceTest {

    private static final String TICKET_KEY = "apex:auth:ticket:ticket";

    @Mock
    private ReactiveStringRedisTemplate redis;

    @Mock
    private ReactiveValueOperations<String, String> values;

    private AuthTicketService service;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new AuthTicketService(redis);
    }

    /**
     * 验证 {@code issueStoresOpaqueTicketForFiveMinutes} 场景。
     */
    @Test
    void issueStoresOpaqueTicketForFiveMinutes() {
        when(values.set(startsWith("apex:auth:ticket:"), any(), eq(Duration.ofSeconds(300))))
                .thenReturn(Mono.just(true));
        AuthTicketPayload payload = payload(AuthTicketType.REGISTRATION, "device");

        StepVerifier.create(service.issue(payload))
                .assertNext(ticket -> assertThat(ticket).hasSize(32))
                .verifyComplete();
    }

    /**
     * 验证 {@code issueFailureAndRedisOutageAreReportedAsCacheErrors} 场景。
     */
    @Test
    void issueFailureAndRedisOutageAreReportedAsCacheErrors() {
        when(values.set(startsWith("apex:auth:ticket:"), any(), any(Duration.class)))
                .thenReturn(Mono.just(false));
        StepVerifier.create(service.issue(payload(AuthTicketType.REGISTRATION, null)))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.CACHE_ERROR))
                .verify();

        when(values.set(startsWith("apex:auth:ticket:"), any(), any(Duration.class)))
                .thenReturn(Mono.error(new RedisConnectionFailureException("down")));
        StepVerifier.create(service.issue(payload(AuthTicketType.REGISTRATION, null)))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.CACHE_ERROR))
                .verify();
    }

    /**
     * 验证 {@code executeConsumesTicketBeforeRunningAction} 场景。
     */
    @Test
    void executeConsumesTicketBeforeRunningAction() {
        when(values.getAndDelete(TICKET_KEY))
                .thenReturn(Mono.just(JsonUtil.toJson(payload(AuthTicketType.REGISTRATION, "device"))));

        StepVerifier.create(service.execute(
                        "ticket",
                        AuthTicketType.REGISTRATION,
                        "device",
                        String.class,
                        ignored -> Mono.just("registered")))
                .expectNext("registered")
                .verifyComplete();

        verify(values).getAndDelete(TICKET_KEY);
    }

    /**
     * 验证 {@code wrongPurposeIsRejectedAfterAtomicConsumption} 场景。
     */
    @Test
    void wrongPurposeIsRejectedAfterAtomicConsumption() {
        when(values.getAndDelete(TICKET_KEY))
                .thenReturn(Mono.just(JsonUtil.toJson(payload(AuthTicketType.WECHAT_BIND, "device-A"))));

        StepVerifier.create(service.consume("ticket", AuthTicketType.REGISTRATION))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.AUTH_TICKET_INVALID))
                .verify();
    }

    /**
     * 验证 {@code consumeReturnsPayloadForExpectedPurpose} 场景。
     */
    @Test
    void consumeReturnsPayloadForExpectedPurpose() {
        when(values.getAndDelete(TICKET_KEY))
                .thenReturn(Mono.just(JsonUtil.toJson(payload(AuthTicketType.REGISTRATION, "device"))));

        StepVerifier.create(service.consume("ticket", AuthTicketType.REGISTRATION))
                .assertNext(result -> assertThat(result.getDeviceId()).isEqualTo("device"))
                .verifyComplete();
    }

    /**
     * 验证 {@code blankAndExpiredTicketsAreRejected} 场景。
     */
    @Test
    void blankAndExpiredTicketsAreRejected() {
        StepVerifier.create(service.execute(
                        " ", AuthTicketType.REGISTRATION, "device", String.class, ignored -> Mono.just("x")))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.AUTH_TICKET_INVALID))
                .verify();

        when(values.getAndDelete("apex:auth:ticket:expired")).thenReturn(Mono.empty());

        StepVerifier.create(service.execute(
                        "expired", AuthTicketType.REGISTRATION, "device", String.class, ignored -> Mono.just("x")))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.AUTH_TICKET_INVALID))
                .verify();
    }

    /**
     * 验证 {@code redisOutageIsMappedToCacheError} 场景。
     */
    @Test
    void redisOutageIsMappedToCacheError() {
        when(values.getAndDelete(TICKET_KEY)).thenReturn(Mono.error(new RedisConnectionFailureException("down")));

        StepVerifier.create(service.execute(
                        "ticket", AuthTicketType.REGISTRATION, "device", String.class, ignored -> Mono.just("x")))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.CACHE_ERROR))
                .verify();
    }

    /**
     * 执行 {@code payload} 逻辑。
     *
     * @param type type 参数
     *
     * @param deviceId deviceId 参数
     *
     * @return 执行结果
     */
    private AuthTicketPayload payload(AuthTicketType type, String deviceId) {
        AuthTicketPayload payload = new AuthTicketPayload();
        payload.setType(type);
        payload.setDeviceId(deviceId);
        payload.setOpenId("openid");
        return payload;
    }

    /**
     * 执行 {@code assertCode} 逻辑。
     *
     * @param error error 参数
     *
     * @param expected expected 参数
     */
    private void assertCode(Throwable error, ResultCode expected) {
        assertThat(error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) error).getCode()).isEqualTo(expected.getCode());
    }
}
