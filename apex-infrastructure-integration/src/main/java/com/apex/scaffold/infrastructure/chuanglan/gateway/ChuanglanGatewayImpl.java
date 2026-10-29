package com.apex.scaffold.infrastructure.chuanglan.gateway;

import com.apex.scaffold.domain.gateway.ChuanglanGateway;
import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import com.apex.scaffold.infrastructure.chuanglan.service.ChuanglanApiService;
import com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 创蓝智能云平台网关实现
 *
 * <p>实现 {@link ChuanglanGateway} 接口，通过委派 {@link ChuanglanApiService} 完成与
 * 创蓝253云通信平台的实际通信。遵循 DDD 分层架构的 Gateway 模式，
 * 作为基础设施层的适配器（Adapter），对上层隐藏API调用的技术细节。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>领域对象封装</b>：将API原始响应转换为领域层可用的结果对象</li>
 *   <li><b>异常处理统一</b>：所有API异常被捕获并转换为带有 success=false 的结果对象</li>
 *   <li><b>响应式编程</b>：使用 Project Reactor 的 Mono/Flux 进行非阻塞调用</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>一键登录</b>：{@link #getPhoneNumberByToken(String)}
 *       → {@link ChuanglanApiService#exchangeTokenForPhone(String)}</li>
 *   <li><b>短信发送</b>：{@link #sendSms(String, String)}
 *       → {@link ChuanglanApiService#sendSms(String, String)}</li>
 *   <li><b>余额查询</b>：{@link #queryBalance()}
 *       → {@link ChuanglanApiService#queryBalance()}</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 一键登录获取手机号
 * chuanglanGateway.getPhoneNumberByToken("sdk-token-xxx")
 *     .subscribe(result -> {
 *         if (result.isSuccess()) {
 *             log.info("获取手机号成功: {}", result.getPhone());
 *         } else {
 *             log.error("获取手机号失败: {} - {}",
 *                 result.getErrorCode(), result.getErrorMessage());
 *         }
 *     });
 *
 * // 发送短信（v2模板API，只需传入验证码）
 * chuanglanGateway.sendSms("13800138000", "123456")
 *     .subscribe(result -> {
 *         if (result.isSuccess()) {
 *             log.info("短信发送成功, msgId: {}", result.getMsgId());
 *         }
 *     });
 * }</pre>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 * @see ChuanglanGateway
 * @see ChuanglanApiService
 * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChuanglanGatewayImpl implements ChuanglanGateway {

    /**
     * 创蓝API服务
     *
     * <p>通过构造器注入获取，用于执行与创蓝平台的实际HTTP通信。
     *
     * @see ChuanglanApiService
     */
    private final ChuanglanApiService chuanglanApiService;

    /**
     * 断路器服务
     *
     * <p>用于保护外部API调用，防止级联故障。
     *
     * @see CircuitBreakerService
     */
    private final CircuitBreakerService circuitBreakerService;

    /**
     * 一键登录 - 通过闪验token获取手机号
     *
     * <p>调用创蓝一键登录API，使用token换取用户手机号。token为空时直接返回失败结果，
     * 调用异常时根据不同异常类型返回对应的错误信息。
     *
     * <p><strong>业务流程</strong></p>
     * <ol>
     *   <li>验证token参数，非空校验</li>
     *   <li>调用 {@link ChuanglanApiService#exchangeTokenForPhone(String)} 执行token置换（带断路器保护）</li>
     *   <li>解析响应结果，提取解密后的手机号</li>
     *   <li>异常处理：区分业务异常、断路器熔断和网络异常</li>
     * </ol>
     *
     * <p><strong>断路器保护</strong></p>
     * <p>使用断路器保护创蓝API调用，当失败率超过阈值时自动熔断，防止级联故障。</p>
     *
     * <p><strong>错误码说明</strong></p>
     * <ul>
     *   <li>"PHONE_NOT_FOUND" - 未获取到手机号</li>
     *   <li>"NETWORK_ERROR" - 网络异常</li>
     *   <li>"CIRCUIT_BREAKER_OPEN" - 断路器已打开，服务暂时不可用</li>
     *   <li>"200000" - 成功</li>
     *   <li>其他 - 创蓝API返回的业务错误码</li>
     * </ul>
     *
     * @param token 一键登录的令牌，由App端创蓝闪验SDK返回
     * @return 包含手机号及状态的结果对象 {@link PhoneNumberResult}
     * @see ChuanglanGateway.PhoneNumberResult
     * @see ChuanglanApiService#exchangeTokenForPhone(String)
     * @see CircuitBreakerService
     */
    @Override
    public Mono<PhoneNumberResult> getPhoneNumberByToken(String token) {
        // 参数校验
        if (token == null || token.isBlank()) {
            log.warn("获取手机号失败: token为空");
            return Mono.just(new PhoneNumberResult(null, false));
        }

        Mono<PhoneNumberResult> apiCall = chuanglanApiService
                .exchangeTokenForPhone(token)
                // 解析响应结果，提取手机号
                .map(this::parsePhoneNumberResult)
                // 异常处理：将异常转换为失败结果
                .onErrorResume(e -> {
                    log.error("获取手机号失败", e);
                    PhoneNumberResult result = new PhoneNumberResult(null, false);
                    if (e instanceof ChuanglanApiException ce) {
                        result.setErrorCode(ce.getErrorCode());
                        result.setErrorMessage(ce.getMessage());
                    } else {
                        result.setErrorCode("NETWORK_ERROR");
                        result.setErrorMessage("网络异常，请稍后重试");
                    }
                    return Mono.just(result);
                });

        // 使用断路器保护外部API调用
        return circuitBreakerService.withCircuitBreaker(
                "chuanglan-api", apiCall, Mono.just(createFallbackResult("CIRCUIT_BREAKER_OPEN", "服务暂时不可用，请稍后重试")));
    }

    /**
     * 一键登录 - 通过闪验token获取手机号（完整版）
     *
     * <p>调用创蓝一键登录API，使用token换取用户手机号。支持传入客户端IP、加密类型和流水号等可选参数。
     *
     * <p><strong>业务流程</strong></p>
     * <ol>
     *   <li>验证token参数，非空校验</li>
     *   <li>调用 {@link ChuanglanApiService#exchangeTokenForPhone(String, String, String, String)} 执行token置换（带断路器保护）</li>
     *   <li>解析响应结果，提取解密后的手机号</li>
     *   <li>异常处理：区分业务异常、断路器熔断和网络异常</li>
     * </ol>
     *
     * <p><strong>断路器保护</strong></p>
     * <p>使用断路器保护创蓝API调用，当失败率超过阈值时自动熔断，防止级联故障。</p>
     *
     * <p><strong>请求参数说明</strong></p>
     * <ul>
     *   <li><b>token</b> - 运营商token（必填），SDK返回的token，一次有效</li>
     *   <li><b>clientIp</b> - 客户端IP（可选），用于反欺诈核验</li>
     *   <li><b>encryptType</b> - 加密方式（可选），0(AES)、1(RSA)，默认0</li>
     *   <li><b>outId</b> - 客户方流水号（可选）</li>
     * </ul>
     *
     * <p><strong>错误码说明</strong></p>
     * <ul>
     *   <li>"PHONE_NOT_FOUND" - 未获取到手机号</li>
     *   <li>"NETWORK_ERROR" - 网络异常</li>
     *   <li>"CIRCUIT_BREAKER_OPEN" - 断路器已打开，服务暂时不可用</li>
     *   <li>"200000" - 成功</li>
     *   <li>其他 - 创蓝API返回的业务错误码</li>
     * </ul>
     *
     * @param token 一键登录的令牌，由App端创蓝闪验SDK返回
     * @param clientIp 客户端IP地址，用于反欺诈核验（可选）
     * @param encryptType 手机号加密方式：0（AES，默认）、1（RSA）
     * @param outId 客户方流水号（可选）
     * @return 包含手机号及状态的结果对象 {@link PhoneNumberResult}
     * @see ChuanglanGateway.PhoneNumberResult
     * @see ChuanglanApiService#exchangeTokenForPhone(String, String, String, String)
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    @Override
    public Mono<PhoneNumberResult> getPhoneNumberByToken(
            String token, String clientIp, String encryptType, String outId) {
        return getPhoneNumberByToken(token, clientIp, encryptType, outId, null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>P0-CL-9修复：通过 {@link ChuanglanApiService#exchangeTokenForPhone(String, String, String, String, String)}
     * 传递 platform 参数，根据客户端平台选择对应的闪验 AppID/AppKey。</p>
     *
     * @since 3.1.0
     */
    @Override
    public Mono<PhoneNumberResult> getPhoneNumberByToken(
            String token, String clientIp, String encryptType, String outId, String platform) {
        // 参数校验
        if (token == null || token.isBlank()) {
            log.warn("获取手机号失败: token为空");
            return Mono.just(new PhoneNumberResult(null, false));
        }

        log.debug(
                "获取手机号, encryptType: {}, outIdPresent: {}, platform: {}",
                encryptType,
                outId != null && !outId.isBlank(),
                platform);

        Mono<PhoneNumberResult> apiCall = chuanglanApiService
                .exchangeTokenForPhone(token, clientIp, encryptType, outId, platform)
                // 解析响应结果，提取手机号
                .map(this::parsePhoneNumberResult)
                // 异常处理：将异常转换为失败结果
                .onErrorResume(e -> {
                    log.error("获取手机号失败, platform: {}", platform, e);
                    PhoneNumberResult result = new PhoneNumberResult(null, false);
                    if (e instanceof ChuanglanApiException ce) {
                        result.setErrorCode(ce.getErrorCode());
                        result.setErrorMessage(ce.getMessage());
                    } else {
                        result.setErrorCode("NETWORK_ERROR");
                        result.setErrorMessage("网络异常，请稍后重试");
                    }
                    return Mono.just(result);
                });

        // 使用断路器保护外部API调用
        return circuitBreakerService.withCircuitBreaker(
                "chuanglan-api", apiCall, Mono.just(createFallbackResult("CIRCUIT_BREAKER_OPEN", "服务暂时不可用，请稍后重试")));
    }

    /**
     * 本机号校验 - 校验当前流量卡手机号与传入手机号是否一致
     *
     * <p>调用创蓝一键登录API，校验当前流量卡的手机号与传入的手机号码是否一致。
     *
     * <p><strong>业务流程</strong></p>
     * <ol>
     *   <li>验证token和mobile参数，非空校验</li>
     *   <li>调用 {@link ChuanglanApiService#validateMobile(String, String, String)} 执行校验</li>
     *   <li>解析响应结果，提取校验结果</li>
     *   <li>异常处理：区分业务异常和网络异常</li>
     * </ol>
     *
     * <p><strong>请求参数说明</strong></p>
     * <ul>
     *   <li><b>token</b> - 运营商token（必填），SDK返回的token，一次有效</li>
     *   <li><b>mobile</b> - 待校验的手机号码（必填）</li>
     *   <li><b>outId</b> - 客户方流水号（可选）</li>
     * </ul>
     *
     * <p><strong>响应字段说明</strong></p>
     * <ul>
     *   <li><b>isVerify</b> - 校验结果："1"表示是本机号码，"0"表示非本机号码</li>
     *   <li><b>tradeNo</b> - 交易流水号</li>
     * </ul>
     *
     * @param token 本机认证SDK返回的临时令牌
     * @param mobile 待校验的手机号码
     * @param outId 客户方流水号（可选）
     * @return 包含校验结果及状态的结果对象 {@link MobileValidateResult}
     * @see ChuanglanGateway.MobileValidateResult
     * @see ChuanglanApiService#validateMobile(String, String, String)
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    @Override
    public Mono<MobileValidateResult> validateMobile(String token, String mobile, String outId) {
        // 参数校验
        if (token == null || token.isBlank()) {
            log.warn("本机号校验失败: token为空");
            MobileValidateResult result = new MobileValidateResult(null, false, null, "PARAM_ERROR", "token不能为空");
            return Mono.just(result);
        }
        if (mobile == null || mobile.isBlank()) {
            log.warn("本机号校验失败: mobile为空");
            MobileValidateResult result = new MobileValidateResult(null, false, null, "PARAM_ERROR", "手机号不能为空");
            return Mono.just(result);
        }

        log.debug("本机号校验, outIdPresent: {}", outId != null && !outId.isBlank());

        return chuanglanApiService
                .validateMobile(token, mobile, outId)
                // 解析响应结果
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) response.get("data");
                    String isVerify = null;
                    String tradeNo = null;
                    if (data != null) {
                        isVerify = String.valueOf(data.get("isVerify"));
                        tradeNo = String.valueOf(data.get("tradeNo"));
                    }
                    return new MobileValidateResult(isVerify, true, tradeNo, null, null);
                })
                // 异常处理：将异常转换为失败结果
                .onErrorResume(e -> {
                    log.error("本机号校验失败", e);
                    MobileValidateResult result = new MobileValidateResult(null, false, null, null, null);
                    if (e instanceof ChuanglanApiException ce) {
                        result.setErrorCode(ce.getErrorCode());
                        result.setErrorMessage(ce.getMessage());
                    } else {
                        result.setErrorCode("NETWORK_ERROR");
                        result.setErrorMessage("网络异常，请稍后重试");
                    }
                    return Mono.just(result);
                });
    }

    /**
     * 本机号校验（简化版）
     *
     * <p>简化版本，仅传入token和mobile，其他参数使用默认值。
     *
     * @param token 本机认证SDK返回的临时令牌
     * @param mobile 待校验的手机号码
     * @return 校验结果Mono
     * @see #validateMobile(String, String, String)
     */
    @Override
    public Mono<MobileValidateResult> validateMobile(String token, String mobile) {
        return validateMobile(token, mobile, null);
    }

    /**
     * 发送短信（v2模板API）
     *
     * <p>通过创蓝短信平台v2模板API向指定手机号发送短信。参数为空时直接返回失败结果，
     * 调用异常时根据不同异常类型返回对应的错误信息。
     *
     * <p><strong>业务流程</strong></p>
     * <ol>
     *   <li>验证手机号和验证码参数</li>
     *   <li>调用 {@link ChuanglanApiService#sendSms(String, String)} 发送短信（带断路器保护）</li>
     *   <li>解析响应结果，提取消息ID</li>
     *   <li>异常处理：区分业务异常、断路器熔断和网络异常</li>
     * </ol>
     *
     * <p><strong>断路器保护</strong></p>
     * <p>使用断路器保护创蓝API调用，当失败率超过阈值时自动熔断，防止级联故障。</p>
     *
     * <p><strong>错误码说明</strong></p>
     * <ul>
     *   <li>"PARAM_ERROR" - 参数错误（手机号或验证码为空）</li>
     *   <li>"RESPONSE_ERROR" - 响应异常（缺少msgId字段）</li>
     *   <li>"SEND_FAILED" - 发送失败</li>
     *   <li>"CIRCUIT_BREAKER_OPEN" - 断路器已打开，服务暂时不可用</li>
     * </ul>
     *
     * @param phone 目标手机号，中国大陆11位手机号码
     * @param code 短信验证码，6位数字
     * @return 包含消息ID及状态的结果对象 {@link SmsSendResult}
     * @see ChuanglanGateway.SmsSendResult
     * @see ChuanglanApiService#sendSms(String, String)
     */
    @Override
    public Mono<SmsSendResult> sendSms(String phone, String code) {
        // 参数校验
        if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
            log.warn("发送短信失败: 参数为空");
            SmsSendResult result = new SmsSendResult(null, false);
            result.setErrorCode("PARAM_ERROR");
            result.setErrorMessage("手机号或验证码不能为空");
            return Mono.just(result);
        }

        Mono<SmsSendResult> apiCall = chuanglanApiService
                .sendSms(phone, code)
                // 解析响应结果，提取消息ID
                .map(response -> {
                    Object msgIdObj = response.get("msgId");
                    if (msgIdObj == null) {
                        log.warn("发送短信响应异常: 缺少msgId字段");
                        SmsSendResult result = new SmsSendResult(null, false);
                        result.setErrorCode("RESPONSE_ERROR");
                        result.setErrorMessage("短信发送响应异常");
                        return result;
                    }
                    return new SmsSendResult(String.valueOf(msgIdObj), true);
                })
                // 异常处理：将异常转换为失败结果
                .onErrorResume(e -> {
                    log.error("发送短信失败", e);
                    SmsSendResult result = new SmsSendResult(null, false);
                    if (e instanceof ChuanglanApiException ce) {
                        result.setErrorCode(ce.getErrorCode());
                        result.setErrorMessage(ce.getMessage());
                    } else {
                        result.setErrorCode("SEND_FAILED");
                        result.setErrorMessage("短信发送失败");
                    }
                    return Mono.just(result);
                });

        // 使用断路器保护外部API调用
        return circuitBreakerService.withCircuitBreaker(
                "chuanglan-api", apiCall, Mono.just(createSmsFallbackResult("CIRCUIT_BREAKER_OPEN", "服务暂时不可用，请稍后重试")));
    }

    /**
     * 查询创蓝账户余额
     *
     * <p>调用创蓝API查询当前账户的短信剩余条数或余额。
     *
     * <p><strong>业务流程</strong></p>
     * <ol>
     *   <li>调用 {@link ChuanglanApiService#queryBalance()} 查询余额（带断路器保护）</li>
     *   <li>解析响应结果，提取余额数值</li>
     *   <li>异常处理：区分业务异常、断路器熔断和网络异常</li>
     * </ol>
     *
     * <p><strong>断路器保护</strong></p>
     * <p>使用断路器保护创蓝API调用，当失败率超过阈值时自动熔断，防止级联故障。</p>
     *
     * <p><strong>错误码说明</strong></p>
     * <ul>
     *   <li>"BALANCE_NOT_FOUND" - 余额字段不存在</li>
     *   <li>"QUERY_FAILED" - 查询失败</li>
     *   <li>"CIRCUIT_BREAKER_OPEN" - 断路器已打开，服务暂时不可用</li>
     * </ul>
     *
     * @return 包含余额及状态的结果对象 {@link BalanceResult}
     * @see ChuanglanGateway.BalanceResult
     * @see ChuanglanApiService#queryBalance()
     */
    @Override
    public Mono<BalanceResult> queryBalance() {
        Mono<BalanceResult> apiCall = chuanglanApiService
                .queryBalance()
                // 解析响应结果，提取余额
                .map(response -> {
                    Object balance = response.get("balance");
                    if (balance == null) {
                        log.warn("查询余额响应异常: 缺少balance字段");
                        BalanceResult result = new BalanceResult(null, false);
                        result.setErrorCode("BALANCE_NOT_FOUND");
                        return result;
                    }
                    return new BalanceResult(String.valueOf(balance), true);
                })
                // 异常处理：将异常转换为失败结果
                .onErrorResume(e -> {
                    log.error("查询余额失败", e);
                    BalanceResult result = new BalanceResult(null, false);
                    if (e instanceof ChuanglanApiException ce) {
                        result.setErrorCode(ce.getErrorCode());
                    } else {
                        result.setErrorCode("QUERY_FAILED");
                    }
                    return Mono.just(result);
                });

        // 使用断路器保护外部API调用
        return circuitBreakerService.withCircuitBreaker(
                "chuanglan-api",
                apiCall,
                Mono.just(createBalanceFallbackResult("CIRCUIT_BREAKER_OPEN", "服务暂时不可用，请稍后重试")));
    }

    /**
     * 解析闪验响应结果，提取手机号
     *
     * <p>从创蓝闪验API响应中提取解密后的手机号。
     *
     * <p><strong>响应数据结构</strong></p>
     * <pre>{@code * {
     *   "code": "200000",
     *   "message": "成功",
     *   "chargeStatus": 1,
     *   "data": {
     *     "tradeNo": "18112115031414011",
     *     "mobileName": "1F881288CC68352FC410E8D4A36FC6E0",
     *     "phone": "13800138000"  // 由ChuanglanApiService解密后添加
     *   }
     * }
     * }</pre>
     *
     * <p>注意：{@code data.phone} 字段由 {@link ChuanglanApiService#exchangeTokenForPhone(String)}
     * 在接收到响应后调用AES解密算法解密 {@code data.mobileName} 后添加。
     *
     * @param response 创蓝闪验API的完整响应Map
     * @return 手机号解析结果 {@link PhoneNumberResult}
     * @see ChuanglanApiService#exchangeTokenForPhone(String)
     */
    private PhoneNumberResult parsePhoneNumberResult(Map<String, Object> response) {
        // 检查data字段是否存在
        Object dataObj = response.get("data");
        if (!(dataObj instanceof Map)) {
            log.warn("闪验响应解析失败: data字段类型异常或为空");
            PhoneNumberResult result = new PhoneNumberResult(null, false);
            result.setErrorCode("PHONE_NOT_FOUND");
            result.setErrorMessage("未获取到手机号");
            return result;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataObj;
        Object phoneObj = data.get("phone");
        String phone = phoneObj != null ? String.valueOf(phoneObj) : null;

        // 检查phone字段
        if (phone == null || phone.isBlank()) {
            log.warn("闪验响应解析失败: phone字段为空");
            PhoneNumberResult result = new PhoneNumberResult(null, false);
            result.setErrorCode("PHONE_NOT_FOUND");
            result.setErrorMessage("未获取到手机号");
            return result;
        }

        log.debug("闪验响应解析成功");
        return new PhoneNumberResult(phone, true);
    }

    /**
     * 创建降级结果
     *
     * <p>当断路器打开或服务不可用时，返回预设的降级结果。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     * @return 降级结果
     */
    private PhoneNumberResult createFallbackResult(String errorCode, String errorMessage) {
        PhoneNumberResult result = new PhoneNumberResult(null, false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }

    /**
     * 创建短信发送降级结果
     *
     * <p>当断路器打开或短信发送服务不可用时，返回预设的降级结果。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     * @return 短信发送降级结果
     */
    private SmsSendResult createSmsFallbackResult(String errorCode, String errorMessage) {
        SmsSendResult result = new SmsSendResult(null, false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }

    /**
     * 创建余额查询降级结果
     *
     * <p>当断路器打开或余额查询服务不可用时，返回预设的降级结果。
     *
     * @param errorCode    错误码
     * @param errorMessage 错误消息
     * @return 余额查询降级结果
     */
    private BalanceResult createBalanceFallbackResult(String errorCode, String errorMessage) {
        BalanceResult result = new BalanceResult(null, false);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        return result;
    }
}
