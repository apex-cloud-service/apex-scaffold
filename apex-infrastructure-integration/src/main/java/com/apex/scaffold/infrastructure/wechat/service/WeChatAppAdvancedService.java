package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.infrastructure.wechat.config.WeChatAppProperties;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信APP高级功能服务
 *
 * <p>提供微信APP的高级功能，如分享、消息推送等
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
public class WeChatAppAdvancedService {

    /** AccessToken 缓存键 */
    private static final String ACCESS_TOKEN_CACHE_KEY = "bais:wechat:access_token";

    /** JS-SDK Ticket 缓存键 */
    private static final String JSAPI_TICKET_CACHE_KEY = "bais:wechat:jsapi_ticket";

    /** Token 过期缓冲时间（秒），提前 5 分钟刷新 */
    private static final long TOKEN_EXPIRE_BUFFER_SECONDS = 300L;

    /** 调度器关闭等待时间（秒） */
    private static final long SHUTDOWN_WAIT_SECONDS = 5L;

    /** 微信 APP 配置属性 */
    private final WeChatAppProperties properties;

    /** 内存缓存，用于存储 Token 和 Ticket（非 Spring Bean，服务内部实现细节） */
    private final Map<String, String> cache = new HashMap<>();

    /** 定时任务执行器，用于过期缓存清理（非 Spring Bean，服务内部实现细节） */
    private final ScheduledExecutorService scheduler = createScheduler();

    /**
     * HTTP 客户端，用于调用微信 API。
     *
     * <p>优化说明（Netty/线程池/IO 性能优化方案 P2-1）：
     * 统一使用 @Qualifier("weChatApiWebClient") Bean，替代原 ManagedWebClientFactory.create()，
     * 使微信 API 调用共享 wechat-api-pool 连接池，避免在 external-http 池额外占用连接。
     */
    private final WebClient webClient;

    /**
     * 构造微信 APP 高级功能服务。
     *
     * <p>cache 和 scheduler 为服务内部实现细节，非 Spring 管理的 Bean，
     * 仍通过内联初始化创建。仅 webClient 通过构造器注入。
     *
     * @param properties 微信 APP 配置属性
     * @param webClient  微信公众号 API WebClient（通过 Qualifier 注入）
     */
    public WeChatAppAdvancedService(
            WeChatAppProperties properties, @Qualifier("weChatApiWebClient") WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    /**
     * 创建用于令牌缓存过期清理的单核心线程定时执行器。
     *
     * <p>线程按需创建，使用带递增序号的 {@code wechat-token-scheduler-} 名称并设为守护线程，
     * 不阻止 JVM 退出。取消的任务会从执行器队列移除。本方法不提交任务，返回的执行器
     * 由当前服务持有，并在 {@link #shutdown()} 生命周期回调中关闭。
     *
     * @return 配置了守护线程工厂和取消任务移除策略的定时执行器
     */
    private static ScheduledExecutorService createScheduler() {
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            /**
             * 为令牌调度任务创建带递增名称的守护线程，不启动线程。
             *
             * @param r 调度执行器提供的任务
             * @return 新建的守护线程，不阻止 JVM 退出
             */
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "wechat-token-scheduler-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, threadFactory);
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /**
     * 获取缓存的AccessToken
     *
     * <p>优先从缓存获取，如果不存在则重新获取
     *
     * @return AccessToken
     */
    public Mono<String> getCachedAccessToken() {
        String cachedToken = cache.get(ACCESS_TOKEN_CACHE_KEY);
        if (cachedToken != null) {
            return Mono.just(cachedToken);
        }
        return fetchAndCacheAccessToken();
    }

    /**
     * 获取并缓存AccessToken
     *
     * <p>调用微信开放平台 {@code /cgi-bin/token} 接口获取全局 AccessToken，
     * 并缓存到内存中。缓存过期前5分钟自动刷新，避免token过期导致请求失败。
     *
     * @return AccessToken
     */
    private Mono<String> fetchAndCacheAccessToken() {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取AccessToken"))
                .flatMap((Map<String, Object> result) -> {
                    String accessToken = (String) result.get("access_token");
                    Integer expiresIn = (Integer) result.get("expires_in");
                    if (accessToken == null || expiresIn == null) {
                        return Mono.error(
                                new WeChatApiException(-1, "获取 AccessToken 失败：微信返回 access_token 或 expires_in 为空"));
                    }
                    long ttlSeconds = Math.max(expiresIn.longValue() - TOKEN_EXPIRE_BUFFER_SECONDS, 60L);
                    cache.put(ACCESS_TOKEN_CACHE_KEY, accessToken);
                    // 定时清理过期的缓存
                    scheduler.schedule(() -> cache.remove(ACCESS_TOKEN_CACHE_KEY), ttlSeconds, TimeUnit.SECONDS);
                    return Mono.just(accessToken);
                })
                .doOnSuccess(token -> log.info("获取并缓存AccessToken成功"))
                .doOnError(e -> log.error("获取AccessToken失败", e));
    }

    /**
     * 发送模板消息
     *
     * <p>向用户发送模板消息，调用微信公众平台 {@code /cgi-bin/message/template/send} 接口。
     *
     * @param accessToken 全局 AccessToken
     * @param toUser 接收用户OpenID
     * @param templateId 模板ID
     * @param data 模板数据
     * @return 发送结果
     */
    public Mono<Map<String, Object>> sendTemplateMessage(
            String accessToken, String toUser, String templateId, Map<String, Object> data) {
        Map<String, Object> request = new HashMap<>(3);
        request.put("touser", toUser);
        request.put("template_id", templateId);
        request.put("data", data);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/message/template/send")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("发送模板消息"))
                .doOnSuccess(result -> log.info("发送模板消息成功, msgId: {}", result.get("msgid")))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("发送模板消息请求失败", e);
                    }
                });
    }

    /**
     * 生成小程序码
     *
     * <p>生成小程序码，可用于分享
     *
     * @param accessToken AccessToken
     * @param scene 场景值
     * @param page 页面路径
     * @return 小程序码图片URL
     */
    public Mono<byte[]> generateWxaCode(String accessToken, String scene, String page) {
        Map<String, Object> request = new HashMap<>(3);
        request.put("scene", scene);
        request.put("page", page);
        request.put("width", 430);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/wxa/getwxacodeunlimit")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnSuccess(bytes -> log.info("生成小程序码成功, size: {} bytes", bytes.length))
                .doOnError(e -> log.error("生成小程序码失败", e));
    }

    /**
     * 获取微信服务器IP地址
     *
     * <p>调用微信开放平台 {@code /cgi-bin/getcallbackip} 接口获取微信服务器IP地址列表，
     * 用于服务器白名单配置。
     *
     * @param accessToken 全局 AccessToken
     * @return IP地址列表，包含 ip_list 字段
     */
    public Mono<Map<String, Object>> getCallbackIp(String accessToken) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/getcallbackip")
                        .queryParam("access_token", accessToken)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取微信服务器IP"))
                .doOnSuccess(result -> log.info("获取微信服务器IP成功"))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("获取微信服务器IP请求失败", e);
                    }
                });
    }

    /**
     * 检查文本内容是否违规
     *
     * <p>使用微信内容安全接口检查文本内容
     *
     * @param accessToken AccessToken
     * @param content 文本内容
     * @return 检查结果
     */
    public Mono<Boolean> checkTextContent(String accessToken, String content) {
        Map<String, Object> request = new HashMap<>(1);
        request.put("content", content);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/wxa/msg_sec_check")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result ->
                        Integer.valueOf(0).equals(WeChatApiResponse.from(result).errorCode()))
                .onErrorResume(e -> {
                    log.error("检查文本内容失败", e);
                    return Mono.just(false);
                });
    }

    /**
     * 检查图片内容是否违规
     *
     * <p>使用微信内容安全接口检查图片内容
     *
     * @param accessToken AccessToken
     * @param imageData 图片数据
     * @return 检查结果
     */
    public Mono<Boolean> checkImageContent(String accessToken, byte[] imageData) {
        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/wxa/img_sec_check")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(imageData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result ->
                        Integer.valueOf(0).equals(WeChatApiResponse.from(result).errorCode()))
                .onErrorResume(e -> {
                    log.error("检查图片内容失败", e);
                    return Mono.just(false);
                });
    }

    /**
     * 获取 JS-SDK Ticket
     *
     * <p>用于生成 JS-SDK 签名
     *
     * @param accessToken AccessToken
     * @return Ticket
     */
    public Mono<String> getJsApiTicket(String accessToken) {
        String cachedTicket = cache.get(JSAPI_TICKET_CACHE_KEY);
        if (cachedTicket != null) {
            return Mono.just(cachedTicket);
        }
        return fetchAndCacheJsApiTicket(accessToken);
    }

    /**
     * 获取并缓存 JS-SDK Ticket
     *
     * @param accessToken 访问令牌
     * @return JS-SDK Ticket
     */
    private Mono<String> fetchAndCacheJsApiTicket(String accessToken) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/ticket/getticket")
                        .queryParam("access_token", accessToken)
                        .queryParam("type", "jsapi")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取 JS-SDK Ticket"))
                .flatMap(result -> {
                    String ticket = (String) result.get("ticket");
                    Integer expiresIn = (Integer) result.get("expires_in");
                    if (ticket == null || expiresIn == null) {
                        return Mono.error(
                                new WeChatApiException(-1, "获取 JS-SDK Ticket 失败：微信返回 ticket 或 expires_in 为空"));
                    }
                    long ttlSeconds = Math.max(expiresIn.longValue() - TOKEN_EXPIRE_BUFFER_SECONDS, 60L);
                    cache.put(JSAPI_TICKET_CACHE_KEY, ticket);
                    // 定时清理过期的缓存
                    scheduler.schedule(() -> cache.remove(JSAPI_TICKET_CACHE_KEY), ttlSeconds, TimeUnit.SECONDS);
                    return Mono.just(ticket);
                });
    }

    /**
     * 生成 JS-SDK 签名
     *
     * @param ticket JS-SDK Ticket
     * @param url 当前网页 URL（不包含#及其后面部分）
     * @param nonceStr 随机字符串
     * @param timestamp 时间戳
     * @return 签名结果
     */
    public String generateJsApiSignature(String ticket, String url, String nonceStr, long timestamp) {
        String normalizedUrl = Optional.ofNullable(url).orElse("");
        int hashIndex = normalizedUrl.indexOf('#');
        if (hashIndex >= 0) {
            normalizedUrl = normalizedUrl.substring(0, hashIndex);
        }
        String string1 = "jsapi_ticket="
                + ticket
                + "&noncestr="
                + nonceStr
                + "&timestamp="
                + timestamp
                + "&url="
                + normalizedUrl;

        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(string1.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("生成 JS-SDK 签名失败", e);
            throw new IllegalStateException("生成 JS-SDK 签名失败", e);
        }
    }

    /**
     * 发送订阅消息
     *
     * <p>向用户发送订阅消息，用于替代已逐步下线的模板消息。
     * 订阅消息需要用户主动订阅后才能发送，分为一次性订阅和长期订阅。
     *
     * <p><strong>订阅消息与模板消息的区别</strong></p>
     * <ul>
     *   <li><b>订阅消息</b>：用户主动订阅，每次订阅可推送一条消息</li>
     *   <li><b>模板消息</b>：已逐步下线，不再支持新接入</li>
     * </ul>
     *
     * <p><strong>使用流程</strong></p>
     * <ol>
     *   <li>在微信公众平台申请订阅消息模板</li>
     *   <li>前端调用 {@code wx.requestSubscribeMessage} 引导用户订阅</li>
     *   <li>服务端调用本方法发送订阅消息</li>
     * </ol>
     *
     * <p><strong>接口地址</strong></p>
     * <p>{@code POST https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=ACCESS_TOKEN}
     *
     * @param accessToken 微信全局AccessToken
     * @param toUser 接收者OpenID，用户订阅时的openid
     * @param templateId 订阅消息模板ID，在微信公众平台申请的模板ID
     * @param page 点击消息跳转的小程序页面路径，如 {@code pages/index/index}，可为null
     * @param data 模板内容数据，key对应模板字段名（如thing1、time2、thing3），value为数据对象包含value和color
     * @return 发送结果Map，包含errcode和errmsg字段
     * @since 1.0.0
     */
    public Mono<Map<String, Object>> sendSubscribeMessage(
            String accessToken, String toUser, String templateId, String page, Map<String, Object> data) {
        log.info("发送订阅消息, toUser: {}, templateId: {}", toUser, templateId);

        Map<String, Object> request = new HashMap<>(6);
        request.put("touser", toUser);
        request.put("template_id", templateId);
        request.put("data", data);
        if (page != null && !page.isEmpty()) {
            request.put("page", page);
        }
        request.put("miniprogram_state", "formal");
        request.put("lang", "zh_CN");

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/message/subscribe/send")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("发送订阅消息"))
                .doOnSuccess(result -> log.info("发送订阅消息成功"))
                .doOnError(e -> log.error("发送订阅消息请求失败", e));
    }

    /**
     * 停止令牌与票据缓存过期调度器：先等待已排定任务结束，超时后强制关闭。
     *
     * <p>由 Spring 在容器销毁前调用；被中断时恢复线程中断标记，避免吞掉中断信号。</p>
     */
    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
