package com.apex.scaffold.infrastructure.wechat.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信用户标签管理服务
 *
 * <p>提供微信公众平台用户标签管理相关的功能，包括创建、查询、修改、删除标签，
 * 以及为用户打标签、取消标签、获取标签下用户列表等。
 *
 * <h3>标签功能概述</h3>
 * <p>微信公众平台支持对用户进行标签化分组管理，方便精准推送消息。
 * 每个公众平台最多可创建100个标签，每个用户可以被打上多个标签。
 *
 * <h3>官方文档</h3>
 * <ul>
 *   <li><a href="https://developers.weixin.qq.com/doc/offiaccount/User_Management/User_Tag_Management.html">
 *       用户标签管理</a></li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
public class WeChatTagService {

    /**
     * 微信API基础URL
     */
    private static final String API_BASE_URL = "https://api.weixin.qq.com";

    /**
     * HTTP 客户端，用于调用微信标签 API
     *
     * <p>优化说明（Netty/线程池/IO 性能优化方案 P2-1）：
     * 统一使用 @Qualifier("weChatApiWebClient") Bean，替代原 ManagedWebClientFactory.create()，
     * 使微信 API 调用共享 weChatApi 连接池，避免在 external-http 池额外占用连接。
     */
    private final WebClient webClient;

    /**
     * 构造器注入 WebClient。
     *
     * @param webClient 注入的微信 API WebClient
     */
    public WeChatTagService(@Qualifier("weChatApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 创建用户标签
     *
     * <p>在公众号上创建一个新的用户标签。每个公众号最多可创建100个标签。
     *
     * <h3>标签命名规范</h3>
     * <ul>
     *   <li>标签名长度不超过30个字符</li>
     *   <li>一个公众号最多创建100个标签</li>
     *   <li>标签名不可与其他标签重复</li>
     * </ul>
     *
     * @param accessToken 微信全局AccessToken
     * @param tagName 标签名称，不超过30个字符
     * @return 创建结果Map，包含id（标签ID）和name（标签名称）字段
     */
    public Mono<Map<String, Object>> createTag(String accessToken, String tagName) {
        log.info("创建用户标签, tagName: {}", tagName);

        Map<String, Object> tagMap = new HashMap<>(4);
        tagMap.put("name", tagName);
        Map<String, Object> body = new HashMap<>(4);
        body.put("tag", tagMap);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/tags/create")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "创建标签"))
                .doOnSuccess(result -> log.info("创建标签成功"))
                .doOnError(e -> log.error("创建标签请求失败", e));
    }

    /**
     * 获取公众号已创建的标签列表
     *
     * <p>查询公众号所有已创建的标签，返回标签ID、名称和每个标签下的用户数量。
     *
     * @param accessToken 微信全局AccessToken
     * @return 标签列表Map，tags数组中每项包含id、name、count字段
     */
    public Mono<Map<String, Object>> getTags(String accessToken) {
        log.info("获取公众号标签列表");

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/tags/get")
                        .queryParam("access_token", accessToken)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "获取标签列表"))
                .doOnSuccess(result -> log.info("获取标签列表成功"))
                .doOnError(e -> log.error("获取标签列表请求失败", e));
    }

    /**
     * 编辑用户标签
     *
     * <p>修改指定标签的名称。
     *
     * @param accessToken 微信全局AccessToken
     * @param tagId 标签ID
     * @param newTagName 新的标签名称
     * @return 修改结果Map
     */
    public Mono<Map<String, Object>> updateTag(String accessToken, Integer tagId, String newTagName) {
        log.info("编辑用户标签, tagId: {}, newTagName: {}", tagId, newTagName);

        Map<String, Object> tagMap = new HashMap<>(4);
        tagMap.put("id", tagId);
        tagMap.put("name", newTagName);
        Map<String, Object> body = new HashMap<>(4);
        body.put("tag", tagMap);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/tags/update")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "编辑标签"))
                .doOnSuccess(result -> log.info("编辑标签成功"))
                .doOnError(e -> log.error("编辑标签请求失败", e));
    }

    /**
     * 删除用户标签
     *
     * <p>删除指定标签。删除后，之前被打上此标签的用户将不再拥有该标签。
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>删除后标签不可恢复</li>
     *   <li>删除标签不影响用户的其他数据</li>
     * </ul>
     *
     * @param accessToken 微信全局AccessToken
     * @param tagId 标签ID
     * @return 删除结果Map
     */
    public Mono<Map<String, Object>> deleteTag(String accessToken, Integer tagId) {
        log.info("删除用户标签, tagId: {}", tagId);

        Map<String, Object> tagMap = new HashMap<>(4);
        tagMap.put("id", tagId);
        Map<String, Object> body = new HashMap<>(4);
        body.put("tag", tagMap);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/tags/delete")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "删除标签"))
                .doOnSuccess(result -> log.info("删除标签成功"))
                .doOnError(e -> log.error("删除标签请求失败", e));
    }

    /**
     * 批量为用户打标签
     *
     * <p>为一个或多个用户添加指定标签。该操作会覆盖用户已有的标签。
     *
     * @param accessToken 微信全局AccessToken
     * @param tagId 标签ID
     * @param openIdList 要打标签的用户OpenID列表，单次最多50个
     * @return 微信API返回的标签操作响应（Map形式）
     */
    public Mono<Map<String, Object>> batchTagging(String accessToken, Integer tagId, List<String> openIdList) {
        log.info("批量为用户打标签, tagId: {}, count: {}", tagId, openIdList.size());

        Map<String, Object> body = new HashMap<>(4);
        body.put("tagid", tagId);
        body.put("openid_list", openIdList);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/tags/members/batchtagging")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "批量打标签"))
                .doOnSuccess(result -> log.info("批量打标签成功"))
                .doOnError(e -> log.error("批量打标签请求失败", e));
    }

    /**
     * 批量为用户取消标签
     *
     * <p>为一个或多个用户取消指定标签。
     *
     * @param accessToken 微信全局AccessToken
     * @param tagId 标签ID
     * @param openIdList 要取消标签的用户OpenID列表，单次最多50个
     * @return 微信API返回的标签操作响应（Map形式）
     */
    public Mono<Map<String, Object>> batchUntagging(String accessToken, Integer tagId, List<String> openIdList) {
        log.info("批量为用户取消标签, tagId: {}, count: {}", tagId, openIdList.size());

        Map<String, Object> body = new HashMap<>(4);
        body.put("tagid", tagId);
        body.put("openid_list", openIdList);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/tags/members/batchuntagging")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "批量取消标签"))
                .doOnSuccess(result -> log.info("批量取消标签成功"))
                .doOnError(e -> log.error("批量取消标签请求失败", e));
    }

    /**
     * 获取用户身上的标签列表
     *
     * <p>查询指定用户当前被打上的所有标签。
     *
     * @param accessToken 微信全局AccessToken
     * @param openId 用户OpenID
     * @return 用户标签信息Map，tagid_list数组包含该用户的所有标签ID
     */
    public Mono<Map<String, Object>> getUserTags(String accessToken, String openId) {
        log.info("获取用户标签列表, openId: {}", openId);

        Map<String, Object> body = new HashMap<>(4);
        body.put("openid", openId);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/tags/getidlist")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "获取用户标签列表"))
                .doOnSuccess(result -> log.info("获取用户标签列表成功"))
                .doOnError(e -> log.error("获取用户标签列表请求失败", e));
    }

    /**
     * 获取标签下的用户列表
     *
     * <p>分页获取指定标签下的所有关注用户。
     *
     * @param accessToken 微信全局AccessToken
     * @param tagId 标签ID
     * @param nextOpenId 用于分页，第一个拉取的OpenID（第一次传空字符串）
     * @return 用户列表Map，包含count、data（openid数组）、next_openid字段
     */
    public Mono<Map<String, Object>> getUsersByTag(String accessToken, Integer tagId, String nextOpenId) {
        log.info("获取标签下用户列表, tagId: {}, nextOpenId: {}", tagId, nextOpenId);

        Map<String, Object> body = new HashMap<>(4);
        body.put("tagid", tagId);
        body.put("next_openid", nextOpenId != null ? nextOpenId : "");

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/user/tag/get")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "获取标签用户列表"))
                .doOnSuccess(result -> log.info("获取标签下用户列表成功, count: {}", result.get("count")))
                .doOnError(e -> log.error("获取标签下用户列表请求失败", e));
    }
}
