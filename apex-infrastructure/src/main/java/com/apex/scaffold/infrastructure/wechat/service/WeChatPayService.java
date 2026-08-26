package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.infrastructure.wechat.config.WeChatPayProperties;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信支付V3服务
 *
 * <p>提供微信支付V3 API的核心功能，包括APP支付统一下单、订单查询、订单关闭、
 * 退款申请与查询，以及支付结果回调通知的签名验证与数据解密。
 *
 * <h3>支持的功能</h3>
 * <ul>
 *   <li>APP支付统一下单（{@link #createAppOrder(String, String, Integer, String, String)}）</li>
 *   <li>查询订单状态（{@link #queryOrder(String, String)}）</li>
 *   <li>关闭订单（{@link #closeOrder(String, String)}）</li>
 *   <li>申请退款（{@link #refund(String, String, Integer, Integer, String)}）</li>
 *   <li>查询退款状态（{@link #queryRefund(String)}）</li>
 *   <li>支付回调签名验证（{@link #verifyCallbackSignature(String, String, String, String)}）</li>
 *   <li>回调数据AES解密（{@link #decryptCallbackData(String, String, String)}）</li>
 * </ul>
 *
 * <h3>API认证说明</h3>
 * <p>微信支付V3 API使用基于RSA的签名认证机制（WECHATPAY2-SHA256-RSA2048），
 * 每次请求需要在HTTP头部携带Authorization签名信息。
 * 签名计算流程：构造待签名串 → 使用商户私钥签名 → 构造Authorization头部。
 *
 * <h3>官方文档</h3>
 * <ul>
 *   <li><a href="https://pay.weixin.qq.com/wiki/doc/apiv3/">微信支付V3开发文档</a></li>
 *   <li><a href="https://pay.weixin.qq.com/wiki/doc/apiv3/apis/chapter3_2_1.shtml">APP支付</a></li>
 *   <li><a href="https://pay.weixin.qq.com/wiki/doc/apiv3/apis/chapter3_2_2.shtml">查询订单</a></li>
 *   <li><a href="https://pay.weixin.qq.com/wiki/doc/apiv3/apis/chapter3_2_9.shtml">申请退款</a></li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see WeChatPayProperties
 * @see WeChatApiException
 */
@Service
public class WeChatPayService {
    private static final Logger log = LoggerFactory.getLogger(WeChatPayService.class);

    /**
     * 支付V3 API服务器地址
     */
    private static final String PAY_BASE_URL = "https://api.mch.weixin.qq.com";

    /**
     * 签名算法标识
     */
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    /**
     * AES-GCM认证标签长度（位）
     */
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * AES-GCM IV长度（字节）
     */
    private static final int GCM_IV_LENGTH = 12;

    /**
     * AES-GCM关联数据字节数组
     */
    private static final byte[] GCM_AAD_BYTES = {};

    /** 微信支付成功响应码 */
    private static final String PAY_SUCCESS_CODE = "SUCCESS";

    private static final String PAY_SUCCESS_CODE_NUM = "0";

    /** 默认空HashMap容量 */
    private static final int EMPTY_MAP_CAPACITY = 16;

    /** Classpath 资源前缀 */
    private static final String CLASSPATH_PREFIX = "classpath:";

    /** JSON响应中的code字段名 */
    private static final String JSON_KEY_CODE = "code";

    /**
     * AES解密算法标识
     */
    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * AES密钥算法
     */
    private static final String AES_KEY_ALGORITHM = "AES";

    /**
     * 微信支付V3配置属性
     */
    private final WeChatPayProperties payProperties;

    /** 微信支付负载编解码器。 */
    private final WeChatPayPayloadCodec payloadCodec = new WeChatPayPayloadCodec();

    /**
     * HTTP 客户端，用于调用微信支付 V3 API
     *
     * <p>优化说明（Netty/线程池/IO 性能优化方案 P2-1）：
     * 统一使用 @Qualifier("weChatPayWebClient") Bean，替代原 ManagedWebClientFactory.create()，
     * 使微信支付 API 调用共享 weChatPay 连接池，避免在 external-http 池额外占用连接。
     */
    private final WebClient payWebClient;

    /**
     * 商户RSA私钥
     */
    private PrivateKey privateKey;

    /**
     * P0-WX-1修复: 微信支付平台RSA公钥，用于回调签名验证.
     * 配置后优先使用 initVerify 进行正确的签名验证。
     *
     */
    private PublicKey platformPublicKey;

    /**
     * 构造器注入所有依赖。
     *
     * @param payProperties 微信支付配置属性
     * @param payWebClient 注入的微信支付 API WebClient
     */
    public WeChatPayService(
            WeChatPayProperties payProperties, @Qualifier("weChatPayWebClient") WebClient payWebClient) {
        this.payProperties = payProperties;
        this.payWebClient = payWebClient;
    }

    /**
     * 初始化支付服务
     *
     * <p>加载商户私钥证书、平台公钥证书。
     * 私钥文件支持文件系统路径和classpath路径两种方式。
     * 加载失败时记录警告日志，允许服务正常启动（后续API调用时会报错）。
     *
     */
    @PostConstruct
    /**
     * 初始化
     */
    public void init() {
        this.privateKey = loadPrivateKey();
        this.platformPublicKey = loadPlatformPublicKey();
    }

    /**
     * 加载商户API私钥
     *
     * <p>从配置的路径加载PEM格式的RSA私钥。
     * 支持两种路径格式：
     * <ul>
     *   <li>{@code classpath:xxx} - 从classpath加载</li>
     *   <li>普通路径 - 从文件系统加载</li>
     * </ul>
     *
     * @return 加载的私钥对象，加载失败返回null
     */
    private PrivateKey loadPrivateKey() {
        String keyPath = payProperties.getPrivateKeyPath();
        if (keyPath == null || keyPath.isEmpty()) {
            log.warn("微信支付商户私钥路径未配置，支付功能将不可用");
            return null;
        }
        try {
            byte[] keyBytes;
            if (keyPath.startsWith(CLASSPATH_PREFIX)) {
                String classpath = keyPath.substring(CLASSPATH_PREFIX.length());
                ClassPathResource resource = new ClassPathResource(classpath);
                try (InputStream is = resource.getInputStream()) {
                    keyBytes = is.readAllBytes();
                }
            } else {
                keyBytes = Files.readAllBytes(Paths.get(keyPath));
            }
            String keyStr = new String(keyBytes, StandardCharsets.UTF_8);
            keyStr = keyStr.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(keyStr);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey pk = keyFactory.generatePrivate(spec);
            log.info("微信支付商户私钥加载成功");
            return pk;
        } catch (Exception e) {
            log.error("加载微信支付商户私钥失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * P0-WX-1修复: 加载微信支付平台公钥证书.
     *
     * <p>从配置的路径加载PEM格式的RSA平台公钥，用于回调签名验证。
     * 支持两种路径格式：
     * <ul>
     *   <li>{@code classpath:xxx} - 从classpath加载</li>
     *   <li>普通路径 - 从文件系统加载</li>
     * </ul>
     *
     * @return 加载的平台公钥对象，未配置或加载失败返回null
     */
    private PublicKey loadPlatformPublicKey() {
        String keyPath = payProperties.getPlatformPublicKeyPath();
        if (keyPath == null || keyPath.isEmpty()) {
            log.warn("P0-WX-1: 微信支付平台公钥路径未配置，回调签名验证将使用商户私钥兜底方案（非标准做法，请尽快配置平台公钥）");
            return null;
        }
        try {
            byte[] keyBytes;
            if (keyPath.startsWith(CLASSPATH_PREFIX)) {
                String classpath = keyPath.substring(CLASSPATH_PREFIX.length());
                ClassPathResource resource = new ClassPathResource(classpath);
                try (InputStream is = resource.getInputStream()) {
                    keyBytes = is.readAllBytes();
                }
            } else {
                keyBytes = Files.readAllBytes(Paths.get(keyPath));
            }
            String keyStr = new String(keyBytes, StandardCharsets.UTF_8);
            keyStr = keyStr.replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(keyStr);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey pk = keyFactory.generatePublic(spec);
            log.info("P0-WX-1: 微信支付平台公钥加载成功");
            return pk;
        } catch (Exception e) {
            log.error("P0-WX-1: 加载微信支付平台公钥失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * APP支付统一下单
     *
     * <p>调用微信支付V3统一下单接口创建APP支付订单，返回调起支付所需的参数。
     * 微信APP支付流程：
     * <ol>
     *   <li>服务端调用本方法创建预付单</li>
     *   <li>获取prepay_id后组装调起参数返回给客户端</li>
     *   <li>客户端使用参数调起微信APP支付SDK</li>
     *   <li>支付完成后微信异步通知服务端</li>
     * </ol>
     *
     * <h3>请求参数说明</h3>
     * <ul>
     *   <li>{@code outTradeNo} - 商户订单号，由商户生成，最长32位，不可重复</li>
     *   <li>{@code description} - 商品描述，会展示在用户的微信支付账单中</li>
     *   <li>{@code totalAmount} - 订单总金额，单位：分</li>
     *   <li>{@code payerClientIp} - 用户终端的公网IP，用于微信支付风控</li>
     *   <li>{@code notifyUrl} - 可选，如果为空则使用配置中的默认回调地址</li>
     * </ul>
     *
     * <h3>响应组装说明</h3>
     * <p>返回的Map中包含客户端调起支付所需的全部参数：
     * <ul>
     *   <li>{@code appid} - 应用ID</li>
     *   <li>{@code partnerid} - 商户号</li>
     *   <li>{@code prepayid} - 预支付交易会话ID</li>
     *   <li>{@code package} - 固定值 "Sign=WXPay"</li>
     *   <li>{@code noncestr} - 随机字符串</li>
     *   <li>{@code timestamp} - 时间戳（秒）</li>
     *   <li>{@code sign} - 签名</li>
     * </ul>
     *
     * @param outTradeNo 商户订单号，不可为空且不可重复
     * @param description 商品描述，会展示在支付账单中
     * @param totalAmount 订单总金额，单位为分（如1元=100）
     * @param payerClientIp 用户终端的公网IP，不可为空
     * @param notifyUrl 支付结果回调URL，为null时使用配置中的默认值
     * @return APP调起支付所需的参数Map，调用失败时返回错误Mono
     */
    public Mono<Map<String, Object>> createAppOrder(
            String outTradeNo, String description, Integer totalAmount, String payerClientIp, String notifyUrl) {

        Map<String, Object> requestBody = new HashMap<>(6);
        requestBody.put("appid", requireAppId());
        requestBody.put("mchid", payProperties.getMchId());
        requestBody.put("description", description);
        requestBody.put("out_trade_no", outTradeNo);
        requestBody.put("notify_url", notifyUrl != null ? notifyUrl : payProperties.getNotifyUrl());
        Map<String, Object> amount = new HashMap<>(2);
        amount.put("total", totalAmount);
        amount.put("currency", "CNY");
        requestBody.put("amount", amount);
        Map<String, Object> sceneInfo = new HashMap<>(1);
        sceneInfo.put("payer_client_ip", payerClientIp);
        requestBody.put("scene_info", sceneInfo);

        String uri = "/v3/pay/transactions/app";
        String body = toJson(requestBody);

        return postPayApi(uri, body)
                .flatMap(result -> {
                    String prepayId = (String) result.get("prepay_id");
                    if (prepayId == null) {
                        return Mono.error(new WeChatApiException(-1, "统一下单失败：prepay_id为空"));
                    }
                    return Mono.just(buildAppPayParams(prepayId));
                })
                .doOnSuccess(params -> log.info("APP支付统一下单成功, outTradeNo: {}", outTradeNo))
                .doOnError(e -> log.error("APP支付统一下单失败, outTradeNo: {}", outTradeNo, e));
    }

    /**
     * 查询订单状态
     *
     * <p>根据商户订单号查询微信支付订单的当前状态。
     * 适用于以下场景：
     * <ul>
     *   <li>未收到支付回调时主动查询订单状态</li>
     *   <li>对账时获取订单的最终支付信息</li>
     *   <li>处理超时未支付的订单</li>
     * </ul>
     *
     * <h3>订单状态说明</h3>
     * <ul>
     *   <li>{@code SUCCESS} - 支付成功</li>
     *   <li>{@code REFUND} - 转入退款</li>
     *   <li>{@code NOTPAY} - 未支付</li>
     *   <li>{@code CLOSED} - 已关闭</li>
     *   <li>{@code REVOKED} - 已撤销（仅付款码支付）</li>
     *   <li>{@code USERPAYING} - 用户支付中</li>
     *   <li>{@code PAYERROR} - 支付失败</li>
     * </ul>
     *
     * @param outTradeNo 商户订单号
     * @param mchId 商户号，如果为null则使用配置中的商户号
     * @return 订单详情Map，包含trade_state、transaction_id、amount等字段
     */
    public Mono<Map<String, Object>> queryOrder(String outTradeNo, String mchId) {
        String uri = "/v3/pay/transactions/out-trade-no/" + outTradeNo;
        String queryMchId = mchId != null ? mchId : payProperties.getMchId();
        uri += "?mchid=" + queryMchId;

        return getPayApi(uri)
                .doOnSuccess(result ->
                        log.info("查询订单成功, outTradeNo: {}, tradeState: {}", outTradeNo, result.get("trade_state")))
                .doOnError(e -> log.error("查询订单失败, outTradeNo: {}", outTradeNo, e));
    }

    /**
     * 关闭订单
     *
     * <p>关闭指定商户订单号的未支付订单。
     * 注意：只能关闭状态为 {@code NOTPAY} 的订单。
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>用户取消订单后关闭对应的支付单</li>
     *   <li>创建新订单前关闭之前的支付单（避免订单号冲突）</li>
     *   <li>超时自动关闭未支付的订单</li>
     * </ul>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>已支付或已关闭的订单无法再次关闭</li>
     *   <li>关闭后原订单号可以重新使用</li>
     *   <li>订单生成后最短5分钟可关闭</li>
     * </ul>
     *
     * @param outTradeNo 商户订单号
     * @param mchId 商户号，如果为null则使用配置中的商户号
     * @return 空Mono表示关闭成功，或错误Mono表示关闭失败
     */
    public Mono<Void> closeOrder(String outTradeNo, String mchId) {
        String uri = "/v3/pay/transactions/out-trade-no/" + outTradeNo + "/close";
        Map<String, Object> body = new HashMap<>(1);
        body.put("mchid", mchId != null ? mchId : payProperties.getMchId());

        return postPayApi(uri, toJson(body))
                .doOnSuccess(result -> log.info("关闭订单成功, outTradeNo: {}", outTradeNo))
                .doOnError(e -> log.error("关闭订单失败, outTradeNo: {}", outTradeNo, e))
                .then();
    }

    /**
     * 申请退款
     *
     * <p>对已支付的订单发起退款申请。支持部分退款和全额退款。
     *
     * <h3>退款规则</h3>
     * <ul>
     *   <li>一笔退款失败后可重新提交，但不要更换退款单号</li>
     *   <li>同一个退款单号只能退一次</li>
     *   <li>申请退款总金额不能超过订单总金额</li>
     *   <li>订单总金额等于0时不能退款</li>
     *   <li>退款频率限制：1500次/min</li>
     * </ul>
     *
     * <h3>退款状态说明</h3>
     * <ul>
     *   <li>{@code SUCCESS} - 退款成功</li>
     *   <li>{@code CLOSED} - 退款关闭</li>
     *   <li>{@code PROCESSING} - 退款处理中</li>
     *   <li>{@code ABNORMAL} - 退款异常</li>
     * </ul>
     *
     * @param outTradeNo 原支付交易订单号
     * @param outRefundNo 商户退款单号，不可重复
     * @param totalAmount 原订单总金额，单位：分
     * @param refundAmount 退款金额，单位：分，不能超过订单总金额
     * @param reason 退款原因，可为null
     * @return 退款结果Map，包含refund_id、status、amount等字段
     */
    public Mono<Map<String, Object>> refund(
            String outTradeNo, String outRefundNo, Integer totalAmount, Integer refundAmount, String reason) {
        Map<String, Object> requestBody = new HashMap<>(4);
        requestBody.put("out_trade_no", outTradeNo);
        requestBody.put("out_refund_no", outRefundNo);
        if (reason != null && !reason.isEmpty()) {
            requestBody.put("reason", reason);
        }
        Map<String, Object> amount = new HashMap<>(3);
        amount.put("refund", refundAmount);
        amount.put("total", totalAmount);
        amount.put("currency", "CNY");
        requestBody.put("amount", amount);

        String uri = "/v3/refund/domestic/refunds";

        return postPayApi(uri, toJson(requestBody))
                .doOnSuccess(
                        result -> log.info("申请退款成功, outRefundNo: {}, status: {}", outRefundNo, result.get("status")))
                .doOnError(e -> log.error("申请退款失败, outRefundNo: {}", outRefundNo, e));
    }

    /**
     * 查询退款状态
     *
     * <p>根据商户退款单号查询退款的当前状态。
     * 退款可能处于处理中状态，建议间隔适当时间后重试查询。
     *
     * @param outRefundNo 商户退款单号
     * @return 退款详情Map，包含status、amount、refund_id等字段
     */
    public Mono<Map<String, Object>> queryRefund(String outRefundNo) {
        String uri = "/v3/refund/domestic/refunds/" + outRefundNo;

        return getPayApi(uri)
                .doOnSuccess(
                        result -> log.info("查询退款成功, outRefundNo: {}, status: {}", outRefundNo, result.get("status")))
                .doOnError(e -> log.error("查询退款失败, outRefundNo: {}", outRefundNo, e));
    }

    /**
     * 验证支付回调签名
     *
     * <p>根据微信支付V3版本回调通知中的签名信息验证回调的真实性。
     * 验证流程：
     * <ol>
     *   <li>构造待签名串：timestamp + "\n" + nonce + "\n" + body + "\n"</li>
     *   <li>使用平台公钥（微信支付平台提供的RSA公钥）进行SHA256withRSA验证</li>
     *   <li>验证通过返回true，验证失败或平台公钥未配置返回false</li>
     * </ol>
     *
     * <h3>安全要求</h3>
     * <ul>
     *   <li>必须配置微信支付平台公钥，否则签名验证将失败</li>
     *   <li>平台公钥应通过微信支付商户平台下载（API安全 -> APIv3密钥）</li>
     *   <li>签名验证失败时应拒绝处理回调请求，防止伪造通知</li>
     * </ul>
     *
     * <h3>配置方式</h3>
     * <p>在application.yml中配置平台公钥路径：</p>
     * <pre>
     * wechat:
     *   pay:
     *     platform-public-key-path: classpath:certificates/platform_public_key.pem
     * </pre>
     *
     * @param wechatpayTimestamp 回调中的时间戳，格式为秒级时间戳
     * @param wechatpayNonce 回调中的随机字符串，用于防止重复请求
     * @param wechatpaySignature Base64编码的签名值
     * @param body 回调请求的原始JSON体（未解析的原始字符串）
     * @return 签名有效返回true，平台公钥未配置或签名无效返回false
     * @see <a href="https://pay.weixin.qq.com/wiki/doc/apiv3/wechatpay/wechatpay4_1.shtml">微信支付回调通知签名验证</a>
     */
    public boolean verifyCallbackSignature(
            String wechatpayTimestamp, String wechatpayNonce, String wechatpaySignature, String body) {
        try {
            if (platformPublicKey == null) {
                log.error("微信支付平台公钥未配置，无法验证回调签名。请在 wechat.pay.platform-public-key-path 配置平台公钥证书路径");
                return false;
            }

            String message = wechatpayTimestamp + "\n" + wechatpayNonce + "\n" + body + "\n";

            Signature verifier = Signature.getInstance(SIGN_ALGORITHM);
            verifier.initVerify(platformPublicKey);
            verifier.update(message.getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getDecoder().decode(wechatpaySignature);
            boolean valid = verifier.verify(signatureBytes);
            log.info("支付回调签名验证结果: {}", valid ? "通过" : "失败");
            return valid;
        } catch (Exception e) {
            log.error("验证支付回调签名异常", e);
            return false;
        }
    }

    /**
     * 解密支付回调通知中的加密数据
     *
     * <p>使用APIv3密钥对回调通知中的加密资源数据进行AES-256-GCM解密。
     * 微信支付V3的回调通知中，敏感数据被封装在{@code resource}对象中：
     * <pre>{@code * {
     *   "resource": {
     *     "algorithm": "AEAD_AES_256_GCM",
     *     "ciphertext": "...",
     *     "associated_data": "",
     *     "nonce": "..."
     *   }
     * }
     * }</pre>
     *
     * <h3>解密流程</h3>
     * <ol>
     *   <li>获取resource中的ciphertext、associated_data和nonce</li>
     *   <li>使用APIv3密钥创建AES密钥</li>
     *   <li>使用AES-256-GCM算法解密ciphertext</li>
     *   <li>返回解密后的明文JSON字符串</li>
     * </ol>
     *
     * <h3>特殊情况处理</h3>
     * <ul>
     *   <li>如果associated_data非空，需要使用associated_data作为解密参数</li>
     *   <li>如果解密失败，可能是APIv3密钥不匹配或数据已被篡改</li>
     * </ul>
     *
     * @param ciphertext Base64编码的密文
     * @param associatedData 附加验证数据，可能为空字符串
     * @param nonce Base64编码的初始化向量（IV）
     * @return 解密后的明文JSON Map
     * @throws Exception 当解密失败时抛出异常
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> decryptCallbackData(String ciphertext, String associatedData, String nonce)
            throws Exception {
        byte[] keyBytes = payProperties.getApiV3Key().getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, AES_KEY_ALGORITHM);

        byte[] associatedDataBytes = associatedData != null && !associatedData.isEmpty()
                ? associatedData.getBytes(StandardCharsets.UTF_8)
                : GCM_AAD_BYTES;

        Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        cipher.updateAAD(associatedDataBytes);

        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
        String plaintext = new String(decrypted, StandardCharsets.UTF_8);
        log.info("支付回调数据解密成功");

        return parseJson(plaintext);
    }

    /**
     * 构建APP调起支付所需的参数
     *
     * <p>根据预支付ID和配置信息，组装客户端调起微信支付所需的完整参数集。
     * 客户端拿到这些参数后可直接调起微信APP支付SDK。
     *
     * @param prepayId 预支付交易会话ID，从统一下单接口获取
     * @return 包含appid、partnerid、prepayid、package、noncestr、timestamp、sign的Map
     */
    private Map<String, Object> buildAppPayParams(String prepayId) {
        String nonceStr = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        long timestamp = System.currentTimeMillis() / 1000;

        Map<String, Object> params = new HashMap<>(6);
        params.put("appid", requireAppId());
        params.put("partnerid", payProperties.getMchId());
        params.put("prepayid", prepayId);
        params.put("package", "Sign=WXPay");
        params.put("noncestr", nonceStr);
        params.put("timestamp", timestamp);

        String sign = generateAppPaySign(
                String.valueOf(params.get("appid")),
                String.valueOf(params.get("timestamp")),
                String.valueOf(params.get("noncestr")),
                prepayId);
        params.put("sign", sign);
        return params;
    }

    /**
     * 生成APP支付签名
     *
     * <p>按微信支付 V3 APP 调起规范拼接签名串，并使用商户 RSA 私钥执行
     * {@code SHA256withRSA} 签名。</p>
     *
     * @param appId 移动应用 AppID
     * @param timestamp 秒级时间戳
     * @param nonceStr 随机串
     * @param prepayId 预支付会话 ID
     * @return Base64 编码的 RSA 签名
     */
    private String generateAppPaySign(String appId, String timestamp, String nonceStr, String prepayId) {
        try {
            String signMessage = appId + "\n" + timestamp + "\n" + nonceStr + "\n" + prepayId + "\n";
            Signature signer = Signature.getInstance(SIGN_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(signMessage.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            log.error("生成APP支付签名失败", e);
            throw new IllegalStateException("生成APP支付签名失败", e);
        }
    }

    /**
     * 获取并校验 APP 支付 AppID。
     *
     * @return 非空 AppID
     */
    private String requireAppId() {
        String appId = payProperties.getAppId();
        if (appId == null || appId.isBlank()) {
            throw new IllegalStateException("wechat.pay.app-id未配置");
        }
        return appId;
    }

    /**
     * 构造微信支付V3 API的Authorization头部
     *
     * <p>签名计算流程：
     * <ol>
     *   <li>构造待签名串：HTTP方法\nURL路径\n时间戳\n随机数\n请求体\n</li>
     *   <li>使用商户私钥对签名串进行SHA256withRSA签名</li>
     *   <li>组装WECHATPAY2-SHA256-RSA2048格式的Authorization</li>
     * </ol>
     *
     * @param method HTTP方法（GET/POST）
     * @param uri 请求URI路径
     * @param body 请求体JSON字符串
     * @return Authorization头部的值
     */
    private String buildAuthorization(String method, String uri, String body) {
        try {
            long timestamp = System.currentTimeMillis() / 1000;
            String nonceStr = UUID.randomUUID().toString().replace("-", "").toUpperCase();

            String signMessage =
                    method + "\n" + uri + "\n" + timestamp + "\n" + nonceStr + "\n" + (body != null ? body : "") + "\n";

            Signature signer = Signature.getInstance(SIGN_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(signMessage.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getEncoder().encodeToString(signer.sign());

            return "WECHATPAY2-SHA256-RSA2048 "
                    + "mchid=\"" + payProperties.getMchId() + "\","
                    + "nonce_str=\"" + nonceStr + "\","
                    + "timestamp=\"" + timestamp + "\","
                    + "serial_no=\"" + payProperties.getMerchantSerialNumber() + "\","
                    + "signature=\"" + signature + "\"";
        } catch (Exception e) {
            log.error("构造支付API签名失败", e);
            throw new IllegalStateException("构造支付API签名失败", e);
        }
    }

    /**
     * 发送POST请求到微信支付V3 API
     *
     * <p>统一处理V3 API的POST请求，包括签名构造和错误处理。
     *
     * @param uri 请求URI路径
     * @param body 请求体JSON
     * @return 响应Map
     */
    private Mono<Map<String, Object>> postPayApi(String uri, String body) {
        String authorization = buildAuthorization("POST", uri, body);

        return payWebClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retry(1)
                .doOnNext(result -> handlePayApiError(result))
                .doOnError(e -> log.error("微信支付API POST请求失败, uri: {}", uri, e));
    }

    /**
     * 发送GET请求到微信支付V3 API
     *
     * <p>统一处理V3 API的GET请求，包括签名构造和错误处理。
     *
     * @param uri 请求URI路径（含查询参数）
     * @return 响应Map
     */
    private Mono<Map<String, Object>> getPayApi(String uri) {
        String pathOnly = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        String authorization = buildAuthorization("GET", pathOnly, null);

        return payWebClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", authorization)
                .header("Accept", "application/json")
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retry(1)
                .doOnNext(result -> handlePayApiError(result))
                .doOnError(e -> log.error("微信支付API GET请求失败, uri: {}", uri, e));
    }

    /**
     * 处理微信支付API的错误响应
     *
     * <p>检查API返回结果中的错误信息，如果存在错误码则抛出WeChatApiException。
     * 微信支付V3 API的错误响应可能以下两种格式：
     * <ul>
     *   <li>业务错误：包含code和message字段</li>
     *   <li>HTTP错误：由WebClient的错误处理单独捕获</li>
     * </ul>
     *
     * @param result API响应Map
     * @throws WeChatApiException 当API返回错误时
     */
    private void handlePayApiError(Map<String, Object> result) {
        if (result.containsKey(JSON_KEY_CODE)) {
            String code = (String) result.get(JSON_KEY_CODE);
            String message = (String) result.get("message");
            if (!Objects.equals(code, PAY_SUCCESS_CODE) && !Objects.equals(code, PAY_SUCCESS_CODE_NUM)) {
                log.error("微信支付API返回错误, code: {}, message: {}", code, message);
                throw new WeChatApiException(-1, code + ": " + message);
            }
        }
    }

    /**
     * 简单的对象转JSON序列化
     *
     * <p>用于构造请求体，不依赖Jackson等重量级序列化库。
     *
     * @param obj Map对象
     * @return JSON字符串
     */
    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> obj) {
        return payloadCodec.encode(obj);
    }

    /**
     * 简单的JSON解析
     *
     * <p>解析回调解密后的JSON字符串为Map格式，用于后续业务处理。
     *
     * @param json JSON字符串
     * @return 解析后的Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        return payloadCodec.decode(json);
    }
}
