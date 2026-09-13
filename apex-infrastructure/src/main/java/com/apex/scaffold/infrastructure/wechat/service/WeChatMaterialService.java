package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信素材管理服务
 *
 * <p>提供微信素材管理相关的功能，包括临时素材上传/下载、永久素材管理、
 * 图文消息素材操作等。素材是微信公众平台用于群发消息、客服消息等功能的基础资源。
 *
 * <h3>素材类型说明</h3>
 * <ul>
 *   <li><b>临时素材</b>：上传后3天有效，适用于需要快速使用的场景</li>
 *   <li><b>永久素材</b>：上传后长期有效，适用于需要重复使用的场景</li>
 * </ul>
 *
 * <h3>支持的媒体类型</h3>
 * <ul>
 *   <li><b>图片（image）</b>：支持bmp/png/jpeg/jpg/gif，上限10M</li>
 *   <li><b>语音（voice）</b>：支持amr/mp3/wma/wav，上限2M</li>
 *   <li><b>视频（video）</b>：支持MP4，上限10M</li>
 *   <li><b>缩略图（thumb）</b>：支持jpg，上限64KB</li>
 * </ul>
 *
 * <h3>官方文档</h3>
 * <ul>
 *   <li><a href="https://developers.weixin.qq.com/doc/offiaccount/Asset_Management/New_temporary_materials.html">
 *       临时素材管理</a></li>
 *   <li><a href="https://developers.weixin.qq.com/doc/offiaccount/Asset_Management/Adding_Permanent_Assets.html">
 *       永久素材管理</a></li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
public class WeChatMaterialService {

    /**
     * 微信API基础URL
     */
    private static final String API_BASE_URL = "https://api.weixin.qq.com";

    /**
     * HTTP 客户端，用于调用微信 API
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
    public WeChatMaterialService(@Qualifier("weChatApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 上传临时素材
     *
     * <p>上传多媒体文件到微信服务器，获取media_id用于后续的客服消息发送、
     * 群发消息等场景。临时素材的有效期为3天。
     *
     * <h3>使用流程</h3>
     * <ol>
     *   <li>将本地文件读入内存</li>
     *   <li>调用本方法上传文件</li>
     *   <li>获取返回的media_id</li>
     *   <li>在3天内使用该media_id发送消息</li>
     * </ol>
     *
     * <h3>上传限制</h3>
     * <ul>
     *   <li>图片（image）：2M以内，支持PNG\JPEG\JPG\GIF</li>
     *   <li>语音（voice）：2M以内，播放长度不超过60s，支持AMR\MP3</li>
     *   <li>视频（video）：10M以内，支持MP4</li>
     *   <li>缩略图（thumb）：64KB以内，支持JPG</li>
     * </ul>
     *
     * <h3>{@code fileName} 为空时的行为</h3>
     * <p>如果传入的fileName为空字符串或null，方法会使用默认文件名"file"提交上传请求。
     * 通常这不会导致上传失败，但接收端显示的文件名可能不具有实际含义。
     *
     * @param accessToken 微信全局AccessToken，需提前获取
     * @param type 媒体文件类型：image、voice、video、thumb
     * @param inputStream 文件输入流，方法内不会主动关闭此流
     * @param fileName 原始文件名，用于Multipart上传，可为null
     * @return 上传结果Map，包含media_id、type、created_at等字段
     * @throws WeChatApiException 当微信API返回错误时抛出
     */
    public Mono<Map<String, Object>> uploadTempMaterial(
            String accessToken, String type, InputStream inputStream, String fileName) {
        log.info("上传临时素材, type: {}, fileName: {}", type, fileName);

        try {
            byte[] fileBytes = inputStream.readAllBytes();
            String safeFileName = fileName != null && !fileName.isEmpty() ? fileName : "file";

            return webClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi-bin/media/upload")
                            .queryParam("access_token", accessToken)
                            .queryParam("type", type)
                            .build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(
                            "media", new org.springframework.core.io.ByteArrayResource(fileBytes) {
                                /** {@inheritDoc} */
                                @Override
                                public String getFilename() {
                                    return safeFileName;
                                }
                            }))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(result -> WeChatApiResponse.from(result).requireSuccess("上传临时素材"))
                    .doOnSuccess(result -> log.info("上传临时素材成功, mediaId: {}", result.get("media_id")))
                    .doOnError(e -> log.error("上传临时素材请求失败", e));
        } catch (Exception e) {
            log.error("读取素材文件失败", e);
            return Mono.error(new WeChatApiException(-1, "读取素材文件失败: " + e.getMessage()));
        }
    }

    /**
     * 下载临时素材
     *
     * <p>根据media_id下载微信服务器上的临时素材文件。
     * 视频素材的下载与其他类型不同：
     * <ul>
     *   <li>非视频素材：直接返回文件的二进制内容</li>
     *   <li>视频素材：返回JSON格式，包含视频的下载URL</li>
     * </ul>
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>下载用户发送给公众号的图片、语音等消息</li>
     *   <li>下载自己上传的临时素材</li>
     *   <li>素材转存到自己的存储系统中</li>
     * </ul>
     *
     * @param accessToken 微信全局AccessToken
     * @param mediaId 素材的media_id
     * @return 素材文件的二进制数据（图片/语音/缩略图），或包含video_url的Map（视频）
     */
    public Mono<byte[]> downloadTempMaterial(String accessToken, String mediaId) {
        log.info("下载临时素材, mediaId: {}", mediaId);

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/media/get")
                        .queryParam("access_token", accessToken)
                        .queryParam("media_id", mediaId)
                        .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnSuccess(bytes -> log.info("下载临时素材成功, size: {} bytes", bytes.length))
                .doOnError(e -> log.error("下载临时素材失败", e));
    }

    /**
     * 上传永久素材
     *
     * <p>上传多媒体文件作为微信的永久素材，永久素材长期有效且不会过期。
     * 永久素材的media_id只可在图文消息等特定场景重复使用。
     *
     * <h3>上传限制</h3>
     * <ul>
     *   <li>图片（image）：2M以内</li>
     *   <li>语音（voice）：2M以内</li>
     *   <li>视频（video）：10M以内，需额外设置标题和描述</li>
     *   <li>缩略图（thumb）：64KB以内</li>
     *   <li>永久素材数量：图片50000，其他1000</li>
     * </ul>
     *
     * <h3>视频上传</h3>
     * <p>视频永久素材上传需要两步：
     * <ol>
     *   <li>调用本方法上传视频文件获取media_id</li>
     *   <li>使用 {@link #uploadVideoDescription} 补充视频的标题和描述</li>
     * </ol>
     *
     * @param accessToken 微信全局AccessToken
     * @param type 素材类型：image、voice、video、thumb
     * @param inputStream 文件输入流
     * @param fileName 文件名
     * @return 上传结果Map，包含media_id、url等字段
     */
    public Mono<Map<String, Object>> uploadPermanentMaterial(
            String accessToken, String type, InputStream inputStream, String fileName) {
        log.info("上传永久素材, type: {}, fileName: {}", type, fileName);

        try {
            byte[] fileBytes = inputStream.readAllBytes();
            String safeFileName = fileName != null && !fileName.isEmpty() ? fileName : "file";

            return webClient
                    .post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi-bin/material/add_material")
                            .queryParam("access_token", accessToken)
                            .queryParam("type", type)
                            .build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(
                            "media", new org.springframework.core.io.ByteArrayResource(fileBytes) {
                                /** {@inheritDoc} */
                                @Override
                                public String getFilename() {
                                    return safeFileName;
                                }
                            }))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .map(result -> WeChatApiResponse.from(result).requireSuccess("上传永久素材"))
                    .doOnSuccess(result -> log.info("上传永久素材成功, mediaId: {}", result.get("media_id")))
                    .doOnError(e -> log.error("上传永久素材请求失败", e));
        } catch (Exception e) {
            log.error("读取素材文件失败", e);
            return Mono.error(new WeChatApiException(-1, "读取素材文件失败: " + e.getMessage()));
        }
    }

    /**
     * 上传视频永久素材的描述信息
     *
     * <p>视频永久素材在文件上传成功后，需要额外调用此方法设置视频的标题和介绍。
     * 此方法是视频素材上传的第二步。
     *
     * @param accessToken 微信全局AccessToken
     * @param mediaId 视频素材的上传结果中的media_id
     * @param title 视频标题，不超过30个字符
     * @param introduction 视频描述，不超过120个字符
     * @return 上传结果Map
     */
    public Mono<Map<String, Object>> uploadVideoDescription(
            String accessToken, String mediaId, String title, String introduction) {
        log.info("上传视频描述信息, mediaId: {}, title: {}", mediaId, title);

        Map<String, Object> body = new HashMap<>(3);
        body.put("media_id", mediaId);
        body.put("title", title);
        body.put("introduction", introduction);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/material/update_news")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("上传视频描述"))
                .doOnSuccess(result -> log.info("上传视频描述成功"))
                .doOnError(e -> log.error("上传视频描述请求失败", e));
    }

    /**
     * 获取永久素材
     *
     * <p>获取指定media_id的永久素材。图片、语音、缩略图直接返回文件二进制数据，
     * 视频素材返回视频下载URL的JSON。
     *
     * @param accessToken 微信全局AccessToken
     * @param mediaId 永久素材的media_id
     * @return 素材的二进制数据或JSON描述
     */
    public Mono<byte[]> getPermanentMaterial(String accessToken, String mediaId) {
        log.info("获取永久素材, mediaId: {}", mediaId);

        Map<String, Object> body = new HashMap<>(1);
        body.put("media_id", mediaId);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/material/get_material")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnSuccess(bytes -> log.info("获取永久素材成功, size: {} bytes", bytes.length))
                .doOnError(e -> log.error("获取永久素材失败", e));
    }

    /**
     * 删除永久素材
     *
     * <p>根据media_id删除已上传的永久素材，释放存储空间。
     * 注意：已删除的素材将无法恢复，图文消息中使用该素材的链接将失效。
     *
     * @param accessToken 微信全局AccessToken
     * @param mediaId 永久素材的media_id
     * @return 空Mono表示操作成功，或错误Mono表示操作失败
     */
    public Mono<Void> deletePermanentMaterial(String accessToken, String mediaId) {
        log.info("删除永久素材, mediaId: {}", mediaId);

        Map<String, Object> body = new HashMap<>(1);
        body.put("media_id", mediaId);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/material/del_material")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("删除永久素材"))
                .doOnSuccess(result -> log.info("删除永久素材成功"))
                .doOnError(e -> log.error("删除永久素材请求失败", e))
                .then();
    }

    /**
     * 获取永久素材总数
     *
     * <p>查询公众号各类永久素材的数量，包括图片、语音、视频、图文的数量。
     *
     * @param accessToken 微信全局AccessToken
     * @return 素材统计数据Map，包含voice_count、video_count、image_count、news_count等字段
     */
    public Mono<Map<String, Object>> getMaterialCount(String accessToken) {
        log.info("获取永久素材总数");

        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/material/get_materialcount")
                        .queryParam("access_token", accessToken)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(result -> log.info("获取永久素材总数成功"))
                .doOnError(e -> log.error("获取永久素材总数失败", e));
    }

    /**
     * 获取永久素材列表
     *
     * <p>分页获取指定类型的永久素材列表，用于管理已上传的素材资源。
     *
     * @param accessToken 微信全局AccessToken
     * @param type 素材类型：image、video、voice、news
     * @param offset 偏移量，从全部素材的该偏移位置开始返回
     * @param count 返回素材的数量，取值在1到20之间
     * @return 素材列表Map，包含total_count、item_count、item等字段
     */
    public Mono<Map<String, Object>> getMaterialList(String accessToken, String type, int offset, int count) {
        log.info("获取永久素材列表, type: {}, offset: {}, count: {}", type, offset, count);

        Map<String, Object> body = new HashMap<>(3);
        body.put("type", type);
        body.put("offset", offset);
        body.put("count", count);

        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/material/batchget_material")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .doOnSuccess(result -> log.info("获取永久素材列表成功"))
                .doOnError(e -> log.error("获取永久素材列表失败", e));
    }
}
