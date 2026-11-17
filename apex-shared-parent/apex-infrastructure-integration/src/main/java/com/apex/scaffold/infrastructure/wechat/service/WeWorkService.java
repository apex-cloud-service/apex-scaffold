package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.infrastructure.wechat.config.WeWorkProperties;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 企业微信（WeWork）服务
 *
 * <p>提供企业微信自建应用相关的核心功能，包括AccessToken管理、
 * 消息推送、成员管理、部门管理等。
 *
 * <p><strong>企业微信API架构</strong></p>
 * <p>企业微信API分为以下模块：
 * <ul>
 *   <li><b>通讯录管理</b> - 成员管理、部门管理、标签管理</li>
 *   <li><b>消息推送</b> - 应用消息、群机器人消息</li>
 *   <li><b>身份验证</b> - OAuth 2.0登录、JS-SDK签名</li>
 *   <li><b>外部联系人</b> - 客户联系、客户群管理</li>
 * </ul>
 *
 * <p><strong>AccessToken说明</strong></p>
 * <p>企业微信AccessToken与微信公众平台AccessToken不通用，需要单独获取。
 * 所有企业微信API请求需要在URL中携带access_token参数。
 * AccessToken有效期为7200秒，建议缓存并提前刷新。
 *
 * <p><strong>官方文档</strong></p>
 * <ul>
 *   <li><a href="https://developer.work.weixin.qq.com/document/path/90664">
 *       企业微信服务端API</a></li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see WeWorkProperties
 */
@Slf4j
@Service
public class WeWorkService {

    /**
     * 企业微信API基础URL
     */
    private static final String API_BASE_URL = "https://qyapi.weixin.qq.com";

    /**
     * 企业微信配置属性
     */
    private final WeWorkProperties weWorkProperties;

    /**
     * HTTP 客户端，用于调用企业微信 API
     *
     * <p>优化说明（Netty/线程池/IO 性能优化方案 P2-1）：
     * 统一使用 @Qualifier("weWorkWebClient") Bean，替代原 ManagedWebClientFactory.create()，
     * 使企业微信 API 调用共享 weWork 连接池，避免在 external-http 池额外占用连接。
     */
    private final WebClient webClient;

    /**
     * 构造器注入所有依赖。
     *
     * @param weWorkProperties 企业微信配置属性
     * @param webClient 注入的企业微信 API WebClient
     */
    public WeWorkService(WeWorkProperties weWorkProperties, @Qualifier("weWorkWebClient") WebClient webClient) {
        this.weWorkProperties = weWorkProperties;
        this.webClient = webClient;
    }

    /**
     * 获取企业微信AccessToken
     *
     * <p>使用企业ID（CorpID）和应用Secret获取企业微信API调用的全局AccessToken。
     * 企业微信中，每个自建应用和通讯录管理都有独立的Secret，
     * 需要使用对应Secret获取Token后才能调用该Secret可用的API。
     *
     * <p><strong>Token说明</strong></p>
     * <ul>
     *   <li>有效期：7200秒（2小时）</li>
     *   <li>建议缓存并提前5分钟刷新</li>
     *   <li>每个应用的Secret对应独立的Token</li>
     *   <li>调用频率限制：每个Secret每天最多获取200次</li>
     * </ul>
     *
     * @return 企业微信AccessToken字符串
     * @throws com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException 当 API 返回错误时抛出
     */
    public Mono<String> getAccessToken() {
        log.info("获取企业微信AccessToken");
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/gettoken")
                        .queryParam("corpid", weWorkProperties.getCorpId())
                        .queryParam("corpsecret", weWorkProperties.getSecret())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "获取企业微信AccessToken"))
                .doOnSuccess(result -> log.info("获取企业微信AccessToken成功"))
                .map(result -> (String) result.get("access_token"))
                .doOnError(e -> log.error("获取企业微信AccessToken请求失败", e));
    }

    /**
     * 发送企业微信应用消息
     *
     * <p>通过企业微信自建应用向指定成员、部门或标签发送消息。
     * 支持文本、图片、语音、视频、文件、文本卡片、图文、Markdown等多种消息类型。
     *
     * <p><strong>接收人格式</strong></p>
     * <ul>
     *   <li>{@code "@all"} - 向全员发送</li>
     *   <li>{@code "UserID1|UserID2"} - 向指定成员发送，多个用竖线分隔</li>
     *   <li>{@code "1|2"} - 向指定部门发送</li>
     *   <li>{@code "1|2"} - 向指定标签发送</li>
     * </ul>
     *
     * @param accessToken 企业微信AccessToken
     * @param toUser 接收人，支持@all、成员ID、部门ID、标签ID
     * @param toParty 接收部门ID，多个用竖线分隔
     * @param toTag 接收标签ID，多个用竖线分隔
     * @param msgType 消息类型：text、image、voice、video、file、textcard、news、mpnews、markdown
     * @param contentMap 消息内容Map，根据不同的msgType需要不同的字段
     * @return 发送结果Map，包含msgtype、errcode等字段
     */
    public Mono<Map<String, Object>> sendMessage(
            String accessToken,
            String toUser,
            String toParty,
            String toTag,
            String msgType,
            Map<String, Object> contentMap) {
        log.info("发送企业微信应用消息, msgType: {}, toUser: {}", msgType, toUser);

        Map<String, Object> body = new HashMap<>(8);
        if (toUser != null && !toUser.isEmpty()) {
            body.put("touser", toUser);
        }
        if (toParty != null && !toParty.isEmpty()) {
            body.put("toparty", toParty);
        }
        if (toTag != null && !toTag.isEmpty()) {
            body.put("totag", toTag);
        }
        body.put("msgtype", msgType);
        body.put("agentid", weWorkProperties.getAgentId());
        body.put(msgType, contentMap);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/message/send")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "发送企业微信消息"))
                .doOnSuccess(result -> log.info("发送企业微信消息成功"))
                .doOnError(e -> log.error("发送企业微信消息请求失败", e));
    }

    /**
     * 发送企业微信文本消息
     *
     * <p>发送文本消息到企业微信，支持@提及功能。
     *
     * @param accessToken 企业微信AccessToken
     * @param toUser 接收人
     * @param content 文本内容，最长2048字节
     * @return 发送结果Map
     */
    public Mono<Map<String, Object>> sendTextMessage(String accessToken, String toUser, String content) {
        Map<String, Object> textMap = new HashMap<>(4);
        textMap.put("content", content);
        return sendMessage(accessToken, toUser, null, null, "text", textMap);
    }

    /**
     * 发送企业微信Markdown消息
     *
     * <p>发送Markdown格式的富文本消息，支持常见的Markdown语法。
     *
     * @param accessToken 企业微信AccessToken
     * @param toUser 接收人
     * @param content Markdown内容，最长2048字节
     * @return 发送结果Map
     */
    public Mono<Map<String, Object>> sendMarkdownMessage(String accessToken, String toUser, String content) {
        Map<String, Object> markdownMap = new HashMap<>(4);
        markdownMap.put("content", content);
        return sendMessage(accessToken, toUser, null, null, "markdown", markdownMap);
    }

    /**
     * 企业微信OAuth 2.0登录
     *
     * <p>企业微信网页授权登录，通过code换取成员信息。
     * 用于在企业微信内置浏览器中实现免登录。
     *
     * <p><strong>返回数据字段</strong></p>
     * <ul>
     *   <li>{@code UserId} - 成员UserID</li>
     *   <li>{@code DeviceId} - 设备ID</li>
     *   <li>{@code user_ticket} - 成员票据（用于获取敏感信息）</li>
     *   <li>{@code expires_in} - 票据有效时间（秒）</li>
     * </ul>
     *
     * @param code 企业微信OAuth回调中携带的code
     * @return 登录信息Map，包含UserId等字段
     */
    public Mono<Map<String, Object>> login(String code) {
        log.info("企业微信OAuth登录, code: {}", code);
        return getAccessToken().flatMap(accessToken -> webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/user/getuserinfo")
                        .queryParam("access_token", accessToken)
                        .queryParam("code", code)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "企业微信登录"))
                .doOnSuccess(result -> log.info("企业微信登录成功, UserId: {}", result.get("UserId")))
                .doOnError(e -> log.error("企业微信登录请求失败", e)));
    }

    /**
     * 获取企业微信成员信息
     *
     * <p>根据UserID获取企业微信成员的详细信息。
     * 若UserID为空，则根据access_token获取当前应用可见范围内的成员列表。
     *
     * @param accessToken 企业微信AccessToken
     * @param userId 企业微信成员UserID
     * @return 成员信息Map，包含name、department、position、mobile等字段
     */
    public Mono<Map<String, Object>> getUserInfo(String accessToken, String userId) {
        log.info("获取企业微信成员信息, userId: {}", userId);
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/user/get")
                        .queryParam("access_token", accessToken)
                        .queryParam("userid", userId)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "获取企业微信成员信息"))
                .doOnSuccess(result -> log.info("获取企业微信成员信息成功"))
                .doOnError(e -> log.error("获取企业微信成员信息请求失败", e));
    }
}
