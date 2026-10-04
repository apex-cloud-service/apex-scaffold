package com.apex.scaffold.infrastructure.chuanglan.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanProperties;
import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import com.apex.scaffold.infrastructure.chuanglan.strategy.PhoneDecryptStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 创蓝API服务单元测试类
 *
 * <p>测试 {@link ChuanglanApiService} 的所有公开方法，使用 Mockito 模拟 WebClient 和配置依赖。
 *
 * <h2>测试覆盖范围</h2>
 * <ul>
 *   <li>{@link ChuanglanApiService#sendSms(String, String)} - 短信发送</li>
 *   <li>{@link ChuanglanApiService#queryBalance()} - 余额查询</li>
 *   <li>{@link ChuanglanApiService#exchangeTokenForPhone(String)} - 闪验token置换（简化版）</li>
 *   <li>{@link ChuanglanApiService#exchangeTokenForPhone(String, String, String, String)} - 闪验token置换（完整版）</li>
 *   <li>{@link ChuanglanApiService#validateMobile(String, String)} - 本机号校验（简化版）</li>
 *   <li>{@link ChuanglanApiService#validateMobile(String, String, String)} - 本机号校验（完整版）</li>
 *   <li>兼容门面向短信与闪验专用 Client 的委派行为</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChuanglanApiService 单元测试")
@SuppressWarnings({"unchecked", "rawtypes"})
class ChuanglanApiServiceTest {

    @Mock
    private ChuanglanProperties chuanglanProperties;

    @Mock
    private WebClient webClient;

    @Mock
    private PhoneDecryptStrategy decryptStrategy;

    private ChuanglanApiService chuanglanApiService;

    private ChuanglanProperties.Sms smsConfig;
    private ChuanglanProperties.Flash flashConfig;

    /**
     * 测试前置：配置 Sms 和 Flash 子配置，创建被测试实例。
     */
    @BeforeEach
    void setUp() {
        smsConfig = new ChuanglanProperties.Sms();
        smsConfig.setHost("smssh.253.com");
        smsConfig.setPort(443);
        smsConfig.setAccount("TEST_ACCOUNT");
        smsConfig.setPassword("TEST_PASSWORD");
        smsConfig.setTemplateId("1021143438");
        smsConfig.setSignature("【APEX】");
        smsConfig.setSslEnabled(true);

        flashConfig = new ChuanglanProperties.Flash();
        flashConfig.setHost("wsflash.253.com");
        flashConfig.setPort(443);
        ChuanglanProperties.FlashPlatform androidConfig = new ChuanglanProperties.FlashPlatform();
        androidConfig.setAppId("TEST_APP_ID");
        androidConfig.setAppKey("TEST_APP_KEY");
        flashConfig.setAndroid(androidConfig);
        flashConfig.setSslEnabled(true);
        flashConfig.setEncryptType("0");

        lenient().when(chuanglanProperties.getSms()).thenReturn(smsConfig);
        lenient().when(chuanglanProperties.getFlash()).thenReturn(flashConfig);
        lenient().when(decryptStrategy.getEncryptType()).thenReturn("0");

        chuanglanApiService =
                new ChuanglanApiService(chuanglanProperties, webClient, decryptStrategy, new ChuanglanRequestSigner());
    }

    // P0-CL-10重构：encryptPhoneForTest / md5ForTest 已移除。
    // 解密逻辑已提取到 AesPhoneDecryptStrategy，测试通过 mock decryptStrategy 完成。

    /**
     * 创建模拟的 WebClient 请求链，并设置响应数据。
     *
     * <p>使用 raw 类型和 SuppressWarnings 解决 WebClient 泛型链式调用的 Mock 兼容性问题。
     *
     * @param mediaType 请求 Content-Type
     * @param responseBody 要返回的模拟响应体
     */
    private void mockWebClientChain(MediaType mediaType, Map<String, Object> responseBody) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(mediaType)).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class))).thenReturn(Mono.just(responseBody));
    }

    // ==================== sendSms 测试 ====================

    @Nested
    @DisplayName("sendSms - 发送短信")
    class SendSmsTests {

        /**
         * 测试成功发送短信，mock WebClient 返回成功响应。
         */
        @Test
        @DisplayName("发送成功应返回msgId")
        void sendSms_Success() {
            Map<String, Object> successResponse = Map.of(
                    "code", "000000",
                    "msgId", "25071516453300902203000007708373",
                    "time", "20251204162433",
                    "successNum", "1",
                    "failNum", "0",
                    "errorMsg", "");

            mockWebClientChain(MediaType.APPLICATION_JSON, successResponse);

            StepVerifier.create(chuanglanApiService.sendSms("13800138000", "123456"))
                    .assertNext(response -> {
                        assertThat(response.get("code")).isEqualTo("000000");
                        assertThat(response.get("msgId")).isEqualTo("25071516453300902203000007708373");
                    })
                    .verifyComplete();
        }

        /**
         * 测试发送失败，mock WebClient 返回错误码，期望抛出 ChuanglanApiException。
         */
        @Test
        @DisplayName("发送失败应抛出ChuanglanApiException")
        void sendSms_Failure() {
            Map<String, Object> errorResponse = Map.of(
                    "code", "E1003",
                    "errorMsg", "余额不足");

            mockWebClientChain(MediaType.APPLICATION_JSON, errorResponse);

            StepVerifier.create(chuanglanApiService.sendSms("13800138000", "123456"))
                    .expectErrorMatches(throwable -> throwable instanceof ChuanglanApiException
                            && "E1003".equals(((ChuanglanApiException) throwable).getErrorCode())
                            && throwable.getMessage().contains("余额不足"))
                    .verify();
        }
    }

    // ==================== exchangeTokenForPhone 测试 ====================

    @Nested
    @DisplayName("exchangeTokenForPhone - 闪验token置换手机号")
    class ExchangeTokenForPhoneTests {

        /**
         * 测试 token 置换成功，mock WebClient 返回包含加密手机号的响应。
         */
        @Test
        @DisplayName("成功置换手机号")
        void exchangeTokenForPhone_Success() throws Exception {
            // P0-CL-10重构：使用解密策略 mock 替代原 real AES 解密
            when(decryptStrategy.decrypt(anyString(), anyString())).thenReturn("13800138000");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tradeNo", "18112115031414011");
            data.put("mobileName", "ENCRYPTED_MOBILE_NAME_MOCK");

            Map<String, Object> successResponse = new LinkedHashMap<>();
            successResponse.put("code", "200000");
            successResponse.put("message", "成功");
            successResponse.put("chargeStatus", 1);
            successResponse.put("data", data);

            mockWebClientChain(MediaType.APPLICATION_FORM_URLENCODED, successResponse);

            StepVerifier.create(chuanglanApiService.exchangeTokenForPhone("test-token"))
                    .assertNext(response -> {
                        assertThat(response.get("code")).isEqualTo("200000");
                        Map<String, Object> respData = (Map<String, Object>) response.get("data");
                        assertThat(respData).isNotNull();
                        assertThat(respData.get("phone")).isNotNull();
                    })
                    .verifyComplete();
        }

        /**
         * 测试 data 中 mobileName 为 null（字符串 "null"），不进行解密，phone 不设置。
         */
        @Test
        @DisplayName("mobileName为null字符串时不设置phone")
        void exchangeTokenForPhone_NullMobileName_NoDecryption() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tradeNo", "18112115031414011");
            data.put("mobileName", "null");

            Map<String, Object> successResponse = new LinkedHashMap<>();
            successResponse.put("code", "200000");
            successResponse.put("message", "成功");
            successResponse.put("data", data);

            mockWebClientChain(MediaType.APPLICATION_FORM_URLENCODED, successResponse);

            StepVerifier.create(chuanglanApiService.exchangeTokenForPhone("test-token"))
                    .assertNext(response -> {
                        Map<String, Object> respData = (Map<String, Object>) response.get("data");
                        assertThat(respData).isNotNull();
                        assertThat(respData.get("phone")).isNull();
                    })
                    .verifyComplete();
        }

        /**
         * 测试 data 中 mobileName 为空字符串，不进行解密。
         */
        @Test
        @DisplayName("mobileName为空字符串时不设置phone")
        void exchangeTokenForPhone_BlankMobileName_NoDecryption() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mobileName", "");

            Map<String, Object> successResponse = new LinkedHashMap<>();
            successResponse.put("code", "200000");
            successResponse.put("data", data);

            mockWebClientChain(MediaType.APPLICATION_FORM_URLENCODED, successResponse);

            StepVerifier.create(chuanglanApiService.exchangeTokenForPhone("test-token"))
                    .assertNext(response -> {
                        Map<String, Object> respData = (Map<String, Object>) response.get("data");
                        assertThat(respData.get("phone")).isNull();
                    })
                    .verifyComplete();
        }

        /**
         * 测试闪验API返回非200000错误码，期望抛出 ChuanglanApiException。
         */
        @Test
        @DisplayName("未配置加密策略应抛出ChuanglanApiException")
        void exchangeTokenForPhone_UnsupportedEncryptType_ThrowsException() {
            StepVerifier.create(
                            chuanglanApiService.exchangeTokenForPhone(
                                    "test-token", null, "1", null))
                    .expectErrorMatches(throwable -> throwable instanceof ChuanglanApiException
                            && "UNSUPPORTED_ENCRYPT_TYPE".equals(
                                    ((ChuanglanApiException) throwable).getErrorCode()))
                    .verify();
            verifyNoInteractions(webClient);
        }

        /**
         * 测试闪验API返回非200000错误码，期望抛出 ChuanglanApiException。
         */
        @Test
        @DisplayName("闪验返回错误码应抛出ChuanglanApiException")
        void exchangeTokenForPhone_FlashError_ThrowsException() {
            Map<String, Object> errorResponse = Map.of(
                    "code", "403000",
                    "message", "用户校验失败");

            mockWebClientChain(MediaType.APPLICATION_FORM_URLENCODED, errorResponse);

            StepVerifier.create(chuanglanApiService.exchangeTokenForPhone("bad-token"))
                    .expectErrorMatches(throwable -> throwable instanceof ChuanglanApiException
                            && "403000".equals(((ChuanglanApiException) throwable).getErrorCode()))
                    .verify();
        }
    }

    // ==================== validateMobile 测试 ====================

    @Nested
    @DisplayName("validateMobile - 本机号校验")
    class ValidateMobileTests {

        /**
         * 测试本机号校验成功（isVerify=1）。
         */
        @Test
        @DisplayName("校验成功 - 是本机号码")
        void validateMobile_Success() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("isVerify", "1");
            data.put("tradeNo", "18112115031414011");

            Map<String, Object> successResponse = new LinkedHashMap<>();
            successResponse.put("code", "200000");
            successResponse.put("message", "成功");
            successResponse.put("data", data);

            mockWebClientChain(MediaType.APPLICATION_FORM_URLENCODED, successResponse);

            StepVerifier.create(chuanglanApiService.validateMobile("test-token", "13800138000"))
                    .assertNext(response -> {
                        assertThat(response.get("code")).isEqualTo("200000");
                        Map<String, Object> respData = (Map<String, Object>) response.get("data");
                        assertThat(respData.get("isVerify")).isEqualTo("1");
                    })
                    .verifyComplete();
        }

        /**
         * 测试本机号校验非本机号码（isVerify=0）。
         */
        @Test
        @DisplayName("校验成功 - 非本机号码")
        void validateMobile_NotLocalPhone() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("isVerify", "0");

            Map<String, Object> successResponse = new LinkedHashMap<>();
            successResponse.put("code", "200000");
            successResponse.put("data", data);

            mockWebClientChain(MediaType.APPLICATION_FORM_URLENCODED, successResponse);

            StepVerifier.create(chuanglanApiService.validateMobile("test-token", "13800138000"))
                    .assertNext(response -> {
                        Map<String, Object> respData = (Map<String, Object>) response.get("data");
                        assertThat(respData.get("isVerify")).isEqualTo("0");
                    })
                    .verifyComplete();
        }
    }

    // ==================== queryBalance 测试 ====================

    @Nested
    @DisplayName("queryBalance - 查询余额")
    class QueryBalanceTests {

        /**
         * 测试查询余额成功。
         */
        @Test
        @DisplayName("查询成功返回余额")
        void queryBalance_Success() {
            Map<String, Object> successResponse = Map.of(
                    "code", 0,
                    "balance", "953",
                    "time", "20180524170449",
                    "errorMsg", "");

            mockWebClientChain(MediaType.APPLICATION_JSON, successResponse);

            StepVerifier.create(chuanglanApiService.queryBalance())
                    .assertNext(response -> {
                        assertThat(String.valueOf(response.get("code"))).isEqualTo("0");
                        assertThat(response.get("balance")).isEqualTo("953");
                    })
                    .verifyComplete();
        }
    }
}
