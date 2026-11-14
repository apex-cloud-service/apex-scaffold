package com.apex.scaffold.infrastructure.chuanglan.service;

import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanProperties;
import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanWebClientConfig;
import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import com.apex.scaffold.infrastructure.chuanglan.strategy.PhoneDecryptStrategy;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 创蓝云平台API服务
 *
 * <p>创蓝253云通信平台的底层HTTP调用服务，负责与创蓝API接口进行实际的网络通信。
 * 使用 {@link WebClient} 实现非阻塞响应式HTTP调用，与项目的WebFlux响应式架构保持一致。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>短信发送（v2模板API）</b> - POST {@code /msg/sms/v2/tpl/send}，通过模板ID发送验证码短信，使用普通鉴权（password 放入请求体）</li>
 *   <li><b>余额查询</b> - POST {@code /msg/balance/json}，用于查询短信账户余额</li>
 *   <li><b>闪验token置换</b> - POST {@code /open/flashsdk/mobile-query}，用于一键登录时通过token获取手机号</li>
 *   <li><b>本机号校验</b> - POST {@code /open/flashsdk/mobile-validate}，用于校验当前流量卡手机号与传入手机号是否一致</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>HTTP 4xx/5xx 状态码抛出 {@link ChuanglanApiException}</li>
 *   <li>创蓝业务错误码（如余额不足、账号密码错误）也抛出 {@link ChuanglanApiException}</li>
 *   <li>网络超时或连接异常包装为 {@link ChuanglanApiException}</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>接口地址</b>：https://wsflash.253.com/open/flashsdk/mobile-query</li>
 *   <li><b>协议</b>：HTTPS</li>
 *   <li><b>请求方式</b>：POST</li>
 *   <li><b>请求内容类型</b>：application/x-www-form-urlencoded</li>
 *   <li><b>签名算法</b>：HmacSHA256（参数按字段名正序排序后拼接，密钥为appKey）</li>
 *   <li><b>手机号解密</b>：AES/CBC/PKCS5Padding，密钥为md5(appKey)前16位，IV为后16位</li>
 *   <li><b>成功响应码</b>：200000</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 发送短信（v2模板API）
 * chuanglanApiService.sendSms("13800138000", "123456")
 *     .subscribe(result -> log.info("短信发送成功, msgId: {}", result.get("msgId")));
 *
 * // 闪验token置换手机号
 * chuanglanApiService.exchangeTokenForPhone("sdk-token-xxx")
 *     .subscribe(result -> log.info("手机号: {}", result.get("data").get("phone")));
 * }</pre>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 * @see ChuanglanProperties
 * @see com.apex.scaffold.infrastructure.chuanglan.gateway.ChuanglanGatewayImpl
 * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
 */
@Service
public class ChuanglanApiService {

    /** 短信子系统 Client。 */
    private final ChuanglanSmsClient smsClient;

    /** 闪验子系统 Client。 */
    private final ChuanglanFlashClient flashClient;

    /**
     * 构造创蓝API服务。
     *
     * @param properties      创蓝平台配置
     * @param webClient       由 {@link ChuanglanWebClientConfig} 创建的连接池化 WebClient
     * @param decryptStrategy 手机号解密策略（P0-CL-10重构）
     * @param requestSigner 创蓝协议签名构建器
     * @since 3.1.0
     */
    public ChuanglanApiService(
            ChuanglanProperties properties,
            @Qualifier("chuanglanWebClient") WebClient webClient,
            PhoneDecryptStrategy decryptStrategy,
            ChuanglanRequestSigner requestSigner) {
        this.smsClient = new ChuanglanSmsClient(properties, webClient, requestSigner);
        this.flashClient = new ChuanglanFlashClient(properties, webClient, decryptStrategy, requestSigner);
    }

    // ==================== 公共接口方法 ====================

    /**
     * 发送短信（v2模板API）
     *
     * <p>调用创蓝253短信v2模板API发送短信。使用JSON格式的POST请求，
     * 通过普通鉴权模式（password 放入请求体）进行身份验证。
     *
     * <p><strong>API请求格式（v2模板API）</strong></p>
     * <pre>{@code * POST https://smssh.253.com/msg/sms/v2/tpl/send
     * Content-Type: application/json
     *
     * {
     *   "account": "N6000001",
     *   "password": "yourPassword",
     *   "nonce": "2e6eceb5737b473284c930c8ef79090e",
     *   "timestamp": "1752143733",
     *   "phoneNumbers": "13800138000",
     *   "templateId": "1021143438",
     *   "templateParamJson": "[{\"param1\":\"123456\"}]",
     *   "signature": "【Apex】",
     *   "report": "true"
     * }
     * }</pre>
     *
     * <p><strong>响应格式（v2模板API）</strong></p>
     * <pre>{@code * {
     *   "code": "000000",
     *   "msgId": "25071516453300902203000007708373",
     *   "time": "20251204162433",
     *   "successNum": "1",
     *   "failNum": "0",
     *   "errorMsg": ""
     * }
     * }</pre>
     *
     * <p><strong>签名算法（加密鉴权，当前未启用）</strong></p>
     * <p>以下为 HmacSHA256 加密鉴权签名算法，当前使用普通鉴权模式，
     * 加密鉴权相关代码已保留注释，需要时恢复。</p>
     * <ol>
     *   <li>将API密码进行MD5加密，得到32位小写md5Password</li>
     *   <li>生成32位随机字符串 nonce 和秒级时间戳 timestamp</li>
     *   <li>将 md5Password、timestamp、nonce 三个值按字母顺序排序后拼接成字符串</li>
     *   <li>使用 HmacSHA256 以 md5Password 为密钥加密该拼接字符串，得到签名</li>
     *   <li>签名通过 X-QA-Hmac-Signature 请求头传递</li>
     * </ol>
     *
     * @param phone 目标手机号，中国大陆11位手机号码，格式如"13800138000"
     * @param code 短信验证码，作为模板变量 param1 的值
     * @return 响应结果Mono，包含code和msgId字段
     * @throws ChuanglanApiException 当API返回非成功码时抛出
     * @see <a href="https://doc.chuanglan.com/document/HAQYSZKH9HT5Z50L">创蓝短信发送API文档</a>
     */
    public Mono<Map<String, Object>> sendSms(String phone, String code) {
        return smsClient.sendSms(phone, code);
    }

    /**
     * 查询账户余额
     *
     * <p>调用创蓝253短信API查询当前账户的短信剩余条数或余额信息。
     *
     * <p><strong>API请求格式</strong></p>
     * <pre>{@code * POST https://smssh1.253.com/msg/balance/json
     * Content-Type: application/json
     *
     * {
     *   "account": "N6000001",
     *   "password": "123456"
     * }
     * }</pre>
     *
     * <p><strong>响应格式</strong></p>
     * <pre>{@code * {
     *   "code": 0,
     *   "balance": "953",
     *   "time": "20180524170449",
     *   "errorMsg": ""
     * }
     * }</pre>
     *
     * <p><strong>说明</strong></p>
     * <p>注意：此接口使用旧版v1 API格式，成功响应码为整型0，与短信v2 API的字符串"000000"不同。</p>
     *
     * @return 响应结果Mono，包含balance余额字段
     * @throws ChuanglanApiException 当API返回非成功码时抛出
     * @see <a href="https://doc.chuanglan.com/document/9RW0IQAIQ58KS3A0">创蓝余额查询API文档</a>
     */
    public Mono<Map<String, Object>> queryBalance() {
        return smsClient.queryBalance();
    }

    /**
     * 闪验 - 通过token置换手机号（Android、iOS、HarmonyOS端）
     *
     * <p>调用创蓝闪验服务端API，使用客户端SDK获取的临时token置换用户的真实手机号码。
     * 该接口需要在创蓝闪验控制台配置服务端出口IP白名单以确保安全性。
     *
     * <p><strong>业务流程</strong></p>
     * <ol>
     *   <li>App端集成创蓝闪验SDK，用户授权后获取临时token</li>
     *   <li>App将token传给服务端登录接口</li>
     *   <li>服务端调用此方法向创蓝API请求置换手机号</li>
     *   <li>创蓝验证token有效性后返回手机号密文</li>
     *   <li>使用AES算法解密手机号密文得到真实手机号</li>
     * </ol>
     *
     * <p><strong>接口规范（创蓝闪验服务端文档V3.X）</strong></p>
     * <ul>
     *   <li><b>接口地址</b>：https://wsflash.253.com/open/flashsdk/mobile-query</li>
     *   <li><b>协议</b>：HTTPS</li>
     *   <li><b>请求方式</b>：POST</li>
     *   <li><b>请求内容类型</b>：application/x-www-form-urlencoded</li>
     *   <li><b>响应内容类型</b>：application/json</li>
     *   <li><b>签名算法</b>：HmacSHA256</li>
     *   <li><b>手机号解密</b>：AES/CBC/PKCS5Padding，密钥为md5(appKey)前16位，IV为后16位</li>
     * </ul>
     *
     * <p><strong>请求参数</strong></p>
     * <table border="1">
     *   <tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>appId</td><td>String</td><td>是</td><td>应用的APPID，由控制台创建应用时生成</td></tr>
     *   <tr><td>token</td><td>String</td><td>是</td><td>运营商token，SDK返回的token，一次有效，不可压测</td></tr>
     *   <tr><td>clientIp</td><td>String</td><td>否</td><td>客户端IP，需要使用反欺诈核验功能时传入</td></tr>
     *   <tr><td>encryptType</td><td>String</td><td>否</td><td>手机号加密方式：0（AES）、1（RSA），缺省为0</td></tr>
     *   <tr><td>outId</td><td>String</td><td>否</td><td>客户方流水号</td></tr>
     *   <tr><td>sign</td><td>String</td><td>是</td><td>HmacSHA256签名</td></tr>
     * </table>
     *
     * <p><strong>响应格式</strong></p>
     * <pre>{@code * {
     *   "code": "200000",
     *   "message": "成功",
     *   "chargeStatus": 1,
     *   "data": {
     *     "tradeNo": "18112115031414011",
     *     "mobileName": "1F881288CC68352FC410E8D4A36FC6E0"
     *   }
     * }
     * }</pre>
     *
     * <p><strong>响应字段说明</strong></p>
     * <table border="1">
     *   <tr><th>字段名</th><th>类型</th><th>说明</th></tr>
     *   <tr><td>code</td><td>String</td><td>响应代码，200000表示成功</td></tr>
     *   <tr><td>message</td><td>String</td><td>响应描述</td></tr>
     *   <tr><td>chargeStatus</td><td>Int</td><td>计费标识：1收费/0不收费</td></tr>
     *   <tr><td>data.tradeNo</td><td>String</td><td>交易流水号</td></tr>
     *   <tr><td>data.mobileName</td><td>String</td><td>手机号密文，需解密</td></tr>
     * </table>
     *
     * @param token App端闪验SDK返回的临时令牌，用于向运营商换取手机号
     * @param clientIp 客户端IP地址，用于反欺诈核验（可选）
     * @param encryptType 手机号加密方式：0（AES，默认）、1（RSA）
     * @param outId 客户方流水号（可选）
     * @return 响应结果Mono，解密后的手机号位于response.get("data").get("phone")
     * @throws ChuanglanApiException 当API返回非成功码（200000）时抛出
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    public Mono<Map<String, Object>> exchangeTokenForPhone(
            String token, String clientIp, String encryptType, String outId) {
        return exchangeTokenForPhone(token, clientIp, encryptType, outId, null);
    }

    /**
     * 通过token置换手机号。
     *
     * <p>P0-CL-9: P0-CL-10: platform + AES
     *
     * @param token       token
     * @param clientIp    IP
     * @param encryptType 0/1
     * @param outId       id
     * @param platform    android/ios/h5/harmony/wechat
     * @return Mono result
     * @since 3.1.0
     */
    public Mono<Map<String, Object>> exchangeTokenForPhone(
            String token, String clientIp, String encryptType, String outId, String platform) {
        return flashClient.exchangeTokenForPhone(token, clientIp, encryptType, outId, platform);
    }

    /**
     * 闪验 - 通过token置换手机号（简化版，仅传入token）
     *
     * <p>简化版本，仅传入token，其他参数使用默认值。
     *
     * @param token App端闪验SDK返回的临时令牌
     * @return 响应结果Mono
     * @see #exchangeTokenForPhone(String, String, String, String)
     */
    public Mono<Map<String, Object>> exchangeTokenForPhone(String token) {
        return exchangeTokenForPhone(token, null, null, null);
    }

    /**
     * 本机号校验接口（Android、iOS、HarmonyOS端）
     *
     * <p>调用创蓝闪验服务端API，校验当前流量卡的手机号与传入的手机号码是否一致。
     * 该接口需要在创蓝闪验控制台配置服务端出口IP白名单以确保安全性。
     *
     * <p><strong>接口规范（创蓝闪验服务端文档V3.X）</strong></p>
     * <ul>
     *   <li><b>接口地址</b>：https://wsflash.253.com/open/flashsdk/mobile-validate</li>
     *   <li><b>协议</b>：HTTPS</li>
     *   <li><b>请求方式</b>：POST</li>
     *   <li><b>请求内容类型</b>：application/x-www-form-urlencoded</li>
     *   <li><b>响应内容类型</b>：application/json</li>
     * </ul>
     *
     * <p><strong>请求参数</strong></p>
     * <table border="1">
     *   <tr><th>参数名</th><th>类型</th><th>必填</th><th>说明</th></tr>
     *   <tr><td>appId</td><td>String</td><td>是</td><td>应用的APPID，由控制台创建应用时生成</td></tr>
     *   <tr><td>token</td><td>String</td><td>是</td><td>运营商token，SDK返回的token，一次有效</td></tr>
     *   <tr><td>mobile</td><td>String</td><td>是</td><td>待校验的手机号码</td></tr>
     *   <tr><td>outId</td><td>String</td><td>否</td><td>客户方流水号</td></tr>
     *   <tr><td>sign</td><td>String</td><td>是</td><td>HmacSHA256签名</td></tr>
     * </table>
     *
     * <p><strong>响应格式</strong></p>
     * <pre>{@code * {
     *   "code": "200000",
     *   "message": "成功",
     *   "chargeStatus": 1,
     *   "data": {
     *     "tradeNo": "18112115031414011",
     *     "isVerify": "1"
     *   }
     * }
     * }</pre>
     *
     * <p><strong>响应字段说明</strong></p>
     * <table border="1">
     *   <tr><th>字段名</th><th>类型</th><th>说明</th></tr>
     *   <tr><td>code</td><td>String</td><td>响应代码，200000表示成功</td></tr>
     *   <tr><td>message</td><td>String</td><td>响应描述</td></tr>
     *   <tr><td>chargeStatus</td><td>Int</td><td>计费标识：1收费/0不收费</td></tr>
     *   <tr><td>data.tradeNo</td><td>String</td><td>交易流水号</td></tr>
     *   <tr><td>data.isVerify</td><td>String</td><td>校验结果：1是本机号码，0非本机号码</td></tr>
     * </table>
     *
     * @param token 本机认证SDK返回的临时令牌
     * @param mobile 待校验的手机号码
     * @param outId 客户方流水号（可选）
     * @return 响应结果Mono，校验结果位于response.get("data").get("isVerify")
     * @throws ChuanglanApiException 当API返回非成功码（200000）时抛出
     * @see <a href="https://doc.chuanglan.com/document/SY88R0I8TCUPR1M3">创蓝闪验SDK服务端接口文档</a>
     */
    public Mono<Map<String, Object>> validateMobile(String token, String mobile, String outId) {
        return flashClient.validateMobile(token, mobile, outId);
    }

    /**
     * 本机号校验接口（简化版）
     *
     * <p>简化版本，仅传入token和mobile，其他参数使用默认值。
     *
     * @param token 本机认证SDK返回的临时令牌
     * @param mobile 待校验的手机号码
     * @return 响应结果Mono
     * @see #validateMobile(String, String, String)
     */
    public Mono<Map<String, Object>> validateMobile(String token, String mobile) {
        return validateMobile(token, mobile, null);
    }
}
