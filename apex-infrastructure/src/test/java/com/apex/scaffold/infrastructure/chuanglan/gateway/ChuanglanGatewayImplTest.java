package com.apex.scaffold.infrastructure.chuanglan.gateway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService;
import com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 创蓝智能云平台网关实现单元测试类
 *
 * <p>测试 {@link ChuanglanGatewayImpl} 的所有公开方法，包括：
 * <ul>
 *   <li>{@link ChuanglanGatewayImpl#getPhoneNumberByToken(String)} - 一键登录获取手机号</li>
 *   <li>{@link ChuanglanGatewayImpl#getPhoneNumberByToken(String, String, String, String)} - 一键登录（完整版参数）</li>
 *   <li>{@link ChuanglanGatewayImpl#sendSms(String, String)} - 发送短信（v2模板API）</li>
 *   <li>{@link ChuanglanGatewayImpl#queryBalance()} - 查询余额</li>
 *   <li>{@link ChuanglanGatewayImpl#validateMobile(String, String)} - 本机号校验</li>
 *   <li>{@link ChuanglanGatewayImpl#validateMobile(String, String, String)} - 本机号校验（完整版）</li>
 * </ul>
 *
 * <p>测试覆盖范围：
 * <ul>
 *   <li>参数校验 - null值、空字符串等边界情况</li>
 *   <li>正常流程 - API调用成功时的响应处理</li>
 *   <li>异常处理 - 业务异常和网络异常的处理</li>
 *   <li>响应解析 - 不同响应格式的数据提取</li>
 * </ul>
 *
 * <p>测试方法命名规范：{@code 方法名_场景_预期结果}
 *
 * @author Apex Team
 * @version 3.1.0
 * @since 1.0.0
 * @see ChuanglanGatewayImpl
 * @see ChuanglanApiService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChuanglanGatewayImpl 单元测试")
class ChuanglanGatewayImplTest {

    /**
     * 创蓝API服务Mock对象
     *
     * <p>用于模拟 {@link ChuanglanApiService} 的行为，
     * 隔离测试目标类与外部依赖。
     *
     * @see ChuanglanApiService
     */
    @Mock
    private ChuanglanApiService chuanglanApiService;

    /**
     * 断路器服务Mock对象
     *
     * <p>用于模拟断路器行为，测试时默认透传执行。
     *
     * @see CircuitBreakerService
     */
    @Mock
    private CircuitBreakerService circuitBreakerService;

    /**
     * 被测试的网关实现类
     *
     * <p>Mockito会自动注入Mock的 chuanglanApiService 和 circuitBreakerService。
     *
     * @see ChuanglanGatewayImpl
     */
    @InjectMocks
    private ChuanglanGatewayImpl chuanglanGatewayImpl;

    /**
     * 测试前置配置
     *
     * <p>配置断路器Mock为透传模式，使测试聚焦于业务逻辑而非断路器行为。
     */
    @BeforeEach
    void setUp() {
        lenient()
                .when(circuitBreakerService.withCircuitBreaker(anyString(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    // ==================== getPhoneNumberByToken 测试（简化版） ====================

    /**
     * 一键登录获取手机号测试套件（简化版）
     *
     * <p>测试 {@link ChuanglanGatewayImpl#getPhoneNumberByToken(String)} 方法的各种场景。
     *
     * @author Apex Team
     */
    @Nested
    @DisplayName("getPhoneNumberByToken - 一键登录获取手机号（简化版）")
    class GetPhoneNumberByTokenTests {

        /**
         * 测试场景：token为null
         * 预期结果：返回success=false的失败结果
         */
        @Test
        @DisplayName("token为null应返回失败结果")
        void getPhoneNumberByToken_NullToken_ReturnsFailedResult() {
            chuanglanGatewayImpl
                    .getPhoneNumberByToken(null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：token为空字符串
         * 预期结果：返回success=false的失败结果
         */
        @Test
        @DisplayName("token为空字符串应返回失败结果")
        void getPhoneNumberByToken_BlankToken_ReturnsFailedResult() {
            chuanglanGatewayImpl
                    .getPhoneNumberByToken("   ")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API返回成功响应
         * 预期结果：返回解密后的手机号，success=true
         *
         * <p>根据创蓝闪验服务端文档V3.X，响应格式为：
         * <pre>{@code * {
         *   "code": "200000",
         *   "message": "成功",
         *   "chargeStatus": 1,
         *   "data": {
         *     "tradeNo": "18112115031414011",
         *     "mobileName": "1F881288CC68352FC410E8D4A36FC6E0",
         *     "phone": "13800138000"
         *   }
         * }
         * }</pre>
         */
        @Test
        @DisplayName("成功获取手机号")
        void getPhoneNumberByToken_Success_ReturnsPhoneNumber() {
            // 构造符合创蓝闪验服务端文档的响应格式
            Map<String, Object> data = new HashMap<>();
            data.put("phone", "13800138000");
            data.put("tradeNo", "18112115031414011");

            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("message", "成功");
            response.put("chargeStatus", 1);
            response.put("data", data);

            when(chuanglanApiService.exchangeTokenForPhone("valid-token")).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .getPhoneNumberByToken("valid-token")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isEqualTo("13800138000");
                        assertThat(result.isSuccess()).isTrue();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：响应中data.phone字段为null
         * 预期结果：返回PHONE_NOT_FOUND错误码
         */
        @Test
        @DisplayName("响应中phone字段为null应返回失败结果")
        void getPhoneNumberByToken_PhoneNull_ReturnsFailedResult() {
            Map<String, Object> data = new HashMap<>();
            data.put("phone", null);

            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("data", data);

            when(chuanglanApiService.exchangeTokenForPhone("valid-token")).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .getPhoneNumberByToken("valid-token")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PHONE_NOT_FOUND");
                        assertThat(result.getErrorMessage()).isEqualTo("未获取到手机号");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：响应中data.phone字段为空字符串
         * 预期结果：返回PHONE_NOT_FOUND错误码
         */
        @Test
        @DisplayName("响应中phone字段为空字符串应返回失败结果")
        void getPhoneNumberByToken_PhoneBlank_ReturnsFailedResult() {
            Map<String, Object> data = new HashMap<>();
            data.put("phone", "");

            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("data", data);

            when(chuanglanApiService.exchangeTokenForPhone("valid-token")).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .getPhoneNumberByToken("valid-token")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PHONE_NOT_FOUND");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：响应中缺少data.phone字段
         * 预期结果：返回PHONE_NOT_FOUND错误码
         */
        @Test
        @DisplayName("响应中缺少phone字段应返回失败结果")
        void getPhoneNumberByToken_MissingPhone_ReturnsFailedResult() {
            Map<String, Object> data = new HashMap<>();

            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("data", data);

            when(chuanglanApiService.exchangeTokenForPhone("valid-token")).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .getPhoneNumberByToken("valid-token")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PHONE_NOT_FOUND");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出业务异常 ChuanglanApiException
         * 预期结果：异常的错误码和消息被设置到结果中
         *
         * <p>常见业务错误码包括：
         * <ul>
         *   <li>403000 - 用户校验失败（一般为签名问题）</li>
         *   <li>200400 - 取号失败，号码补填不正确</li>
         * </ul>
         */
        @Test
        @DisplayName("抛出ChuanglanApiException应包含错误码")
        void getPhoneNumberByToken_ChuanglanApiException_ReturnsErrorResult() {
            when(chuanglanApiService.exchangeTokenForPhone("bad-token"))
                    .thenReturn(Mono.error(new ChuanglanApiException("E1002", "token已过期")));

            chuanglanGatewayImpl
                    .getPhoneNumberByToken("bad-token")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("E1002");
                        assertThat(result.getErrorMessage()).isEqualTo("token已过期");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出网络异常
         * 预期结果：返回NETWORK_ERROR错误码和通用错误消息
         */
        @Test
        @DisplayName("抛出通用异常应返回NETWORK_ERROR")
        void getPhoneNumberByToken_GenericException_ReturnsNetworkError() {
            when(chuanglanApiService.exchangeTokenForPhone("error-token"))
                    .thenReturn(Mono.error(new RuntimeException("连接超时")));

            chuanglanGatewayImpl
                    .getPhoneNumberByToken("error-token")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("NETWORK_ERROR");
                        assertThat(result.getErrorMessage()).isEqualTo("网络异常，请稍后重试");
                    })
                    .verifyComplete();
        }
    }

    // ==================== getPhoneNumberByToken 测试（完整版） ====================

    /**
     * 一键登录完整版参数测试套件
     *
     * <p>测试 {@link ChuanglanGatewayImpl#getPhoneNumberByToken(String, String, String, String)} 方法。
     *
     * @author Apex Team
     */
    @Nested
    @DisplayName("getPhoneNumberByToken - 完整版参数")
    class GetPhoneNumberByTokenFullTests {

        /**
         * 测试场景：完整版参数 - token为null
         * 预期结果：返回success=false的失败结果
         */
        @Test
        @DisplayName("token为null应返回失败结果")
        void getPhoneNumberByTokenFull_NullToken_ReturnsFailedResult() {
            chuanglanGatewayImpl
                    .getPhoneNumberByToken(null, "1.2.3.4", "0", "out-001")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：完整版参数调用应正确代理到ApiService
         * 预期结果：返回成功结果
         */
        @Test
        @DisplayName("完整版参数调用应正确代理到ApiService")
        void getPhoneNumberByTokenFull_WithAllParams_Success() {
            Map<String, Object> data = new HashMap<>();
            data.put("phone", "13800138000");

            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("data", data);

            when(chuanglanApiService.exchangeTokenForPhone(
                            eq("test-token"), eq("1.2.3.4"), eq("0"), eq("out-001"), isNull()))
                    .thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .getPhoneNumberByToken("test-token", "1.2.3.4", "0", "out-001")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getPhone()).isEqualTo("13800138000");
                        assertThat(result.isSuccess()).isTrue();
                    })
                    .verifyComplete();
        }
    }

    // ==================== sendSms 测试 ====================

    /**
     * 发送短信测试套件（v2模板API）
     *
     * <p>测试 {@link ChuanglanGatewayImpl#sendSms(String, String)} 方法的各种场景。
     *
     * @author Apex Team
     */
    @Nested
    @DisplayName("sendSms - 发送短信（v2模板API）")
    class SendSmsTests {

        /**
         * 测试场景：phone为null
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("phone为null应返回参数错误")
        void sendSms_NullPhone_ReturnsParamError() {
            chuanglanGatewayImpl
                    .sendSms(null, "123456")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                        assertThat(result.getErrorMessage()).isEqualTo("手机号或验证码不能为空");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：phone为空字符串
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("phone为空字符串应返回参数错误")
        void sendSms_BlankPhone_ReturnsParamError() {
            chuanglanGatewayImpl
                    .sendSms("  ", "123456")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：code(验证码)为null
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("code(验证码)为null应返回参数错误")
        void sendSms_NullCode_ReturnsParamError() {
            chuanglanGatewayImpl
                    .sendSms("13800138000", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：code(验证码)为空字符串
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("code(验证码)为空字符串应返回参数错误")
        void sendSms_BlankCode_ReturnsParamError() {
            chuanglanGatewayImpl
                    .sendSms("13800138000", "  ")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API返回成功响应（v2模板API）
         * 预期结果：返回消息ID，success=true
         *
         * <p>创蓝短信v2 API成功响应格式：
         * <pre>{@code * {
         *   "code": "000000",
         *   "msgId": "25071516453300902203000007708373",
         *   "time": "20251204162433"
         * }
         * }</pre>
         */
        @Test
        @DisplayName("发送成功应返回msgId")
        void sendSms_Success_ReturnsMsgId() {
            Map<String, Object> response = new HashMap<>();
            response.put("msgId", "25071516453300902203000007708373");
            when(chuanglanApiService.sendSms("13800138000", "123456")).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .sendSms("13800138000", "123456")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isEqualTo("25071516453300902203000007708373");
                        assertThat(result.isSuccess()).isTrue();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：响应中msgId字段为null
         * 预期结果：返回RESPONSE_ERROR错误码
         */
        @Test
        @DisplayName("响应中msgId为null应返回RESPONSE_ERROR")
        void sendSms_NullMsgId_ReturnsResponseError() {
            Map<String, Object> response = new HashMap<>();
            response.put("msgId", null);
            when(chuanglanApiService.sendSms("13800138000", "123456")).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .sendSms("13800138000", "123456")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("RESPONSE_ERROR");
                        assertThat(result.getErrorMessage()).isEqualTo("短信发送响应异常");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：响应中缺少msgId字段
         * 预期结果：返回RESPONSE_ERROR错误码
         */
        @Test
        @DisplayName("响应中缺少msgId字段应返回RESPONSE_ERROR")
        void sendSms_MissingMsgId_ReturnsResponseError() {
            Map<String, Object> response = new HashMap<>();
            when(chuanglanApiService.sendSms("13800138000", "123456")).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .sendSms("13800138000", "123456")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("RESPONSE_ERROR");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出业务异常
         * 预期结果：异常的错误信息被设置到结果中
         */
        @Test
        @DisplayName("抛出ChuanglanApiException应包含错误码")
        void sendSms_ChuanglanApiException_ReturnsErrorResult() {
            when(chuanglanApiService.sendSms("13800138000", "123456"))
                    .thenReturn(Mono.error(new ChuanglanApiException("E1003", "余额不足")));

            chuanglanGatewayImpl
                    .sendSms("13800138000", "123456")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("E1003");
                        assertThat(result.getErrorMessage()).isEqualTo("余额不足");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出网络异常
         * 预期结果：返回SEND_FAILED错误码
         */
        @Test
        @DisplayName("抛出通用异常应返回SEND_FAILED")
        void sendSms_GenericException_ReturnsSendFailed() {
            when(chuanglanApiService.sendSms("13800138000", "123456"))
                    .thenReturn(Mono.error(new RuntimeException("网络异常")));

            chuanglanGatewayImpl
                    .sendSms("13800138000", "123456")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getMsgId()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("SEND_FAILED");
                        assertThat(result.getErrorMessage()).isEqualTo("短信发送失败");
                    })
                    .verifyComplete();
        }
    }

    // ==================== queryBalance 测试 ====================

    /**
     * 查询余额测试套件
     *
     * <p>测试 {@link ChuanglanGatewayImpl#queryBalance()} 方法的各种场景。
     *
     * @author Apex Team
     */
    @Nested
    @DisplayName("queryBalance - 查询余额")
    class QueryBalanceTests {

        /**
         * 测试场景：API返回成功响应
         * 预期结果：返回余额数值，success=true
         *
         * <p>创蓝短信余额查询API响应格式（v1 API）：
         * <pre>{@code * {
         *   "code": 0,
         *   "balance": "953",
         *   "time": "20180524170449",
         *   "errorMsg": ""
         * }
         * }</pre>
         * <p>注意：余额查询使用旧版v1 API，响应码为整型0，与短信v2 API的字符串"000000"不同。</p>
         */
        @Test
        @DisplayName("查询成功应返回余额")
        void queryBalance_Success_ReturnsBalance() {
            Map<String, Object> response = new HashMap<>();
            response.put("balance", "953");
            when(chuanglanApiService.queryBalance()).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .queryBalance()
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getBalance()).isEqualTo("953");
                        assertThat(result.isSuccess()).isTrue();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：响应中balance字段为null
         * 预期结果：返回BALANCE_NOT_FOUND错误码
         */
        @Test
        @DisplayName("响应中balance为null应返回BALANCE_NOT_FOUND")
        void queryBalance_NullBalance_ReturnsBalanceNotFound() {
            Map<String, Object> response = new HashMap<>();
            response.put("balance", null);
            when(chuanglanApiService.queryBalance()).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .queryBalance()
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getBalance()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("BALANCE_NOT_FOUND");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：响应中缺少balance字段
         * 预期结果：返回BALANCE_NOT_FOUND错误码
         */
        @Test
        @DisplayName("响应中缺少balance字段应返回BALANCE_NOT_FOUND")
        void queryBalance_MissingBalance_ReturnsBalanceNotFound() {
            Map<String, Object> response = new HashMap<>();
            when(chuanglanApiService.queryBalance()).thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .queryBalance()
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getBalance()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("BALANCE_NOT_FOUND");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出业务异常
         * 预期结果：异常的错误码被设置到结果中
         */
        @Test
        @DisplayName("抛出ChuanglanApiException应返回错误码")
        void queryBalance_ChuanglanApiException_ReturnsErrorCode() {
            when(chuanglanApiService.queryBalance()).thenReturn(Mono.error(new ChuanglanApiException("E1004", "接口异常")));

            chuanglanGatewayImpl
                    .queryBalance()
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getBalance()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("E1004");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出网络异常
         * 预期结果：返回QUERY_FAILED错误码
         */
        @Test
        @DisplayName("抛出通用异常应返回QUERY_FAILED")
        void queryBalance_GenericException_ReturnsQueryFailed() {
            when(chuanglanApiService.queryBalance()).thenReturn(Mono.error(new RuntimeException("服务不可达")));

            chuanglanGatewayImpl
                    .queryBalance()
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getBalance()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("QUERY_FAILED");
                    })
                    .verifyComplete();
        }
    }

    // ==================== validateMobile 测试 ====================

    /**
     * 本机号校验测试套件
     *
     * <p>测试 {@link ChuanglanGatewayImpl#validateMobile(String, String, String)} 方法的各种场景。
     *
     * @author Apex Team
     */
    @Nested
    @DisplayName("validateMobile - 本机号校验")
    class ValidateMobileTests {

        /**
         * 测试场景：token为null
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("token为null应返回参数错误")
        void validateMobile_NullToken_ReturnsParamError() {
            chuanglanGatewayImpl
                    .validateMobile(null, "13800138000", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                        assertThat(result.getErrorMessage()).isEqualTo("token不能为空");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：token为空字符串
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("token为空字符串应返回参数错误")
        void validateMobile_BlankToken_ReturnsParamError() {
            chuanglanGatewayImpl
                    .validateMobile("  ", "13800138000", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：mobile为null
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("mobile为null应返回参数错误")
        void validateMobile_NullMobile_ReturnsParamError() {
            chuanglanGatewayImpl
                    .validateMobile("test-token", null, null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                        assertThat(result.getErrorMessage()).isEqualTo("手机号不能为空");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：mobile为空字符串
         * 预期结果：返回PARAM_ERROR错误码
         */
        @Test
        @DisplayName("mobile为空字符串应返回参数错误")
        void validateMobile_BlankMobile_ReturnsParamError() {
            chuanglanGatewayImpl
                    .validateMobile("test-token", "  ", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("PARAM_ERROR");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：校验成功 - 是本机号码
         * 预期结果：isVerify="1"，success=true，isNativeMobile()=true
         */
        @Test
        @DisplayName("校验成功 - 是本机号码")
        void validateMobile_Success_IsNativeMobile() {
            Map<String, Object> data = new HashMap<>();
            data.put("isVerify", "1");
            data.put("tradeNo", "18112115031414011");

            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("message", "成功");
            response.put("chargeStatus", 1);
            response.put("data", data);

            when(chuanglanApiService.validateMobile("test-token", "13800138000", null))
                    .thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .validateMobile("test-token", "13800138000", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isEqualTo("1");
                        assertThat(result.isSuccess()).isTrue();
                        assertThat(result.getTradeNo()).isEqualTo("18112115031414011");
                        assertThat(result.isNativeMobile()).isTrue();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：校验成功 - 非本机号码
         * 预期结果：isVerify="0"，success=true，isNativeMobile()=false
         */
        @Test
        @DisplayName("校验成功 - 非本机号码")
        void validateMobile_Success_NotNativeMobile() {
            Map<String, Object> data = new HashMap<>();
            data.put("isVerify", "0");
            data.put("tradeNo", "18112115031414011");

            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("data", data);

            when(chuanglanApiService.validateMobile("test-token", "13800138000", null))
                    .thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .validateMobile("test-token", "13800138000", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isEqualTo("0");
                        assertThat(result.isSuccess()).isTrue();
                        assertThat(result.isNativeMobile()).isFalse();
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出业务异常
         * 预期结果：异常的错误码和消息被设置到结果中
         */
        @Test
        @DisplayName("API抛出业务异常应包含错误码")
        void validateMobile_ChuanglanApiException_ReturnsErrorResult() {
            when(chuanglanApiService.validateMobile("bad-token", "13800138000", null))
                    .thenReturn(Mono.error(new ChuanglanApiException("200400", "取号失败")));

            chuanglanGatewayImpl
                    .validateMobile("bad-token", "13800138000", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("200400");
                        assertThat(result.getErrorMessage()).isEqualTo("取号失败");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：API抛出网络异常
         * 预期结果：返回NETWORK_ERROR错误码
         */
        @Test
        @DisplayName("抛出通用异常应返回NETWORK_ERROR")
        void validateMobile_GenericException_ReturnsNetworkError() {
            when(chuanglanApiService.validateMobile("error-token", "13800138000", null))
                    .thenReturn(Mono.error(new RuntimeException("连接超时")));

            chuanglanGatewayImpl
                    .validateMobile("error-token", "13800138000", null)
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isNull();
                        assertThat(result.isSuccess()).isFalse();
                        assertThat(result.getErrorCode()).isEqualTo("NETWORK_ERROR");
                        assertThat(result.getErrorMessage()).isEqualTo("网络异常，请稍后重试");
                    })
                    .verifyComplete();
        }

        /**
         * 测试场景：简化版validateMobile(token, mobile)应正确委托
         * 预期结果：正确调用完整版方法，outId传递null
         */
        @Test
        @DisplayName("简化版validateMobile(token, mobile)应正常调用")
        void validateMobile_SimplifiedVersion_DelegatesCorrectly() {
            Map<String, Object> data = new HashMap<>();
            data.put("isVerify", "1");
            Map<String, Object> response = new HashMap<>();
            response.put("code", "200000");
            response.put("data", data);

            when(chuanglanApiService.validateMobile("test-token", "13800138000", null))
                    .thenReturn(Mono.just(response));

            chuanglanGatewayImpl
                    .validateMobile("test-token", "13800138000")
                    .as(StepVerifier::create)
                    .assertNext(result -> {
                        assertThat(result.getIsVerify()).isEqualTo("1");
                        assertThat(result.isSuccess()).isTrue();
                    })
                    .verifyComplete();

            verify(chuanglanApiService).validateMobile("test-token", "13800138000", null);
        }
    }
}
