package com.apex.scaffold.infrastructure.wechat.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * 微信数据统计服务
 *
 * <p>提供微信公众平台和开放平台的各项数据统计分析功能，
 * 包括用户增长分析、用户属性分析、消息发送统计、接口调用统计等。
 *
 * <h3>主要功能模块</h3>
 * <ul>
 *   <li><b>用户分析</b> - 用户增长、用户属性、用户活跃度</li>
 *   <li><b>消息分析</b> - 消息发送/接收统计、模板消息发送统计</li>
 *   <li><b>接口分析</b> - 接口调用次数、调用耗时、调用失败率</li>
 *   <li><b>内容分析</b> - 群发图文阅读量、分享转发量</li>
 * </ul>
 *
 * <h3>统计时间说明</h3>
 * <ul>
 *   <li>日数据：当天早上8点前可获取前一天的数据</li>
 *   <li>月数据：当月结束后可获取</li>
 *   <li>数据保留期：一般为90天</li>
 * </ul>
 *
 * <h3>官方文档</h3>
 * <ul>
 *   <li><a href="https://developers.weixin.qq.com/doc/offiaccount/Analytics/User_Analysis_Data_Interface.html">
 *       用户分析</a></li>
 *   <li><a href="https://developers.weixin.qq.com/doc/offiaccount/Analytics/Messages_Analysis_Data_Interface.html">
 *       消息分析</a></li>
 *   <li><a href="https://developers.weixin.qq.com/doc/offiaccount/Analytics/Advertisement_Analysis_Interface.html">
 *       接口分析</a></li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
public class WeChatStatisticsService {

    /**
     * 微信API基础URL
     */
    private static final String API_BASE_URL = "https://api.weixin.qq.com";

    /**
     * 日期格式化器，格式为yyyy-MM-dd
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 日期格式化器，格式为yyyyMM
     */
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * HTTP 客户端，用于调用微信数据统计 API
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
    public WeChatStatisticsService(@Qualifier("weChatApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 获取用户增减数据（日统计）
     *
     * <p>获取指定日期范围内的每日用户增长数据，包括新关注人数、取消关注人数、
     * 净增关注人数、累计关注人数等指标。
     *
     * <h3>返回数据字段</h3>
     * <ul>
     *   <li>{@code ref_date} - 数据的日期</li>
     *   <li>{@code user_source} - 用户的渠道</li>
     *   <li>{@code new_user} - 新增的用户数量</li>
     *   <li>{@code cancel_user} - 取消关注的用户数量</li>
     * </ul>
     *
     * <h3>{@code beginDate} 为空时的行为</h3>
     * <p>待办：beginDate 为空时，方法应默认设置为前一天日期。
     * 当前版本若传入空值会直接传递给微信API，微信将以错误响应。
     *
     * @param accessToken 微信全局AccessToken
     * @param beginDate 统计起始日期
     * @param endDate 统计结束日期，不能早于beginDate
     * @return 用户增长统计数据Map
     */
    public Mono<Map<String, Object>> getUserSummary(String accessToken, LocalDate beginDate, LocalDate endDate) {
        log.info("获取用户增减数据, beginDate: {}, endDate: {}", beginDate, endDate);

        Map<String, Object> body = new HashMap<>(2);
        body.put("begin_date", beginDate.format(DATE_FORMATTER));
        body.put("end_date", endDate.format(DATE_FORMATTER));

        return postStatApi(accessToken, "/datacube/getusersummary", body)
                .doOnSuccess(result -> log.info("获取用户增减数据成功"))
                .doOnError(e -> log.error("获取用户增减数据失败", e));
    }

    /**
     * 获取累计用户数据（日统计）
     *
     * <p>获取指定日期范围内的每日累计用户数据。
     * 累计用户数=总用户数=净增用户数+历史累计用户数。
     *
     * @param accessToken 微信全局AccessToken
     * @param beginDate 统计起始日期
     * @param endDate 统计结束日期
     * @return 累计用户数据Map
     */
    public Mono<Map<String, Object>> getUserCumulate(String accessToken, LocalDate beginDate, LocalDate endDate) {
        log.info("获取累计用户数据, beginDate: {}, endDate: {}", beginDate, endDate);

        Map<String, Object> body = new HashMap<>(2);
        body.put("begin_date", beginDate.format(DATE_FORMATTER));
        body.put("end_date", endDate.format(DATE_FORMATTER));

        return postStatApi(accessToken, "/datacube/getusercumulate", body)
                .doOnSuccess(result -> log.info("获取累计用户数据成功"))
                .doOnError(e -> log.error("获取累计用户数据失败", e));
    }

    /**
     * 获取消息发送概况数据（日统计）
     *
     * <p>获取指定日期范围内的每日群发消息统计数据，包括群发消息数量、
     * 发送人数、送达人数、阅读人数等指标。
     *
     * <h3>返回数据字段</h3>
     * <ul>
     *   <li>{@code ref_date} - 数据的日期</li>
     *   <li>{@code msg_type} - 消息类型</li>
     *   <li>{@code msg_user} - 发送人数</li>
     *   <li>{@code msg_count} - 发送消息数量</li>
     * </ul>
     *
     * @param accessToken 微信全局AccessToken
     * @param beginDate 统计起始日期
     * @param endDate 统计结束日期
     * @return 消息发送统计Map
     */
    public Mono<Map<String, Object>> getUpstreamMsg(String accessToken, LocalDate beginDate, LocalDate endDate) {
        log.info("获取消息发送概况数据, beginDate: {}, endDate: {}", beginDate, endDate);

        Map<String, Object> body = new HashMap<>(2);
        body.put("begin_date", beginDate.format(DATE_FORMATTER));
        body.put("end_date", endDate.format(DATE_FORMATTER));

        return postStatApi(accessToken, "/datacube/getupstreammsg", body)
                .doOnSuccess(result -> log.info("获取消息发送概况数据成功"))
                .doOnError(e -> log.error("获取消息发送概况数据失败", e));
    }

    /**
     * 获取消息发送月数据
     *
     * <p>获取指定月份范围内的群发消息月统计数据。
     * 月份格式为YYYYMM，如202501表示2025年1月。
     *
     * @param accessToken 微信全局AccessToken
     * @param beginMonth 统计起始月份
     * @param endMonth 统计结束月份
     * @return 消息发送月统计Map
     */
    public Mono<Map<String, Object>> getUpstreamMsgMonth(String accessToken, LocalDate beginMonth, LocalDate endMonth) {
        log.info("获取消息发送月数据, beginMonth: {}, endMonth: {}", beginMonth, endMonth);

        Map<String, Object> body = new HashMap<>(2);
        body.put("begin_date", beginMonth.format(MONTH_FORMATTER));
        body.put("end_date", endMonth.format(MONTH_FORMATTER));

        return postStatApi(accessToken, "/datacube/getupstreammsgmonth", body)
                .doOnSuccess(result -> log.info("获取消息发送月数据成功"))
                .doOnError(e -> log.error("获取消息发送月数据失败", e));
    }

    /**
     * 获取接口调用概况数据（日统计）
     *
     * <p>获取指定日期范围内的接口调用次数、失败次数、平均耗时、最大耗时等指标。
     * 可监控各微信API接口的使用情况和性能表现。
     *
     * <h3>返回数据字段</h3>
     * <ul>
     *   <li>{@code ref_date} - 数据的日期</li>
     *   <li>{@code callback_count} - 服务器回调的调用次数</li>
     *   <li>{@code fail_count} - 失败次数</li>
     *   <li>{@code total_time_cost} - 总耗时（毫秒）</li>
     *   <li>{@code max_time_cost} - 最大耗时（毫秒）</li>
     * </ul>
     *
     * <h3>使用建议</h3>
     * <ul>
     *   <li>定期（如每日）获取接口调用数据，及时发现异常</li>
     *   <li>监控失败率和平均耗时，设置告警阈值</li>
     *   <li>分析调用趋势，优化接口调用策略</li>
     * </ul>
     *
     * @param accessToken 微信全局AccessToken
     * @param beginDate 统计起始日期
     * @param endDate 统计结束日期
     * @return 接口调用统计Map
     */
    public Mono<Map<String, Object>> getInterfaceSummary(String accessToken, LocalDate beginDate, LocalDate endDate) {
        log.info("获取接口调用概况数据, beginDate: {}, endDate: {}", beginDate, endDate);

        Map<String, Object> body = new HashMap<>(2);
        body.put("begin_date", beginDate.format(DATE_FORMATTER));
        body.put("end_date", endDate.format(DATE_FORMATTER));

        return postStatApi(accessToken, "/datacube/getinterfacesummary", body)
                .doOnSuccess(result -> log.info("获取接口调用概况数据成功"))
                .doOnError(e -> log.error("获取接口调用概况数据失败", e));
    }

    /**
     * 获取接口调用小时数据
     *
     * <p>获取指定日期的接口调用小时级统计数据，精确到小时。
     * 可用于分析高峰时段，优化资源调度和API调用策略。
     *
     * <h3>使用场景</h3>
     * <ul>
     *   <li>分析用户活跃时段，优化推送时间</li>
     *   <li>发现突发流量和异常调用</li>
     *   <li>规划API调用频率限制策略</li>
     * </ul>
     *
     * @param accessToken 微信全局AccessToken
     * @param date 统计日期
     * @return 小时级接口调用统计Map
     */
    public Mono<Map<String, Object>> getInterfaceSummaryHour(String accessToken, LocalDate date) {
        log.info("获取接口调用小时数据, date: {}", date);

        Map<String, Object> body = new HashMap<>(2);
        body.put("begin_date", date.format(DATE_FORMATTER));
        body.put("end_date", date.format(DATE_FORMATTER));

        return postStatApi(accessToken, "/datacube/getinterfacesummaryhour", body)
                .doOnSuccess(result -> log.info("获取接口调用小时数据成功"))
                .doOnError(e -> log.error("获取接口调用小时数据失败", e));
    }

    /**
     * 获取图文群发每日数据
     *
     * <p>获取指定日期范围内的图文群发消息统计数据，包括送达人数、
     * 阅读人数、分享人数、收藏人数等。
     *
     * @param accessToken 微信全局AccessToken
     * @param beginDate 统计起始日期
     * @param endDate 统计结束日期
     * @return 图文群发统计数据Map
     */
    public Mono<Map<String, Object>> getArticleSummary(String accessToken, LocalDate beginDate, LocalDate endDate) {
        log.info("获取图文群发每日数据, beginDate: {}, endDate: {}", beginDate, endDate);

        Map<String, Object> body = new HashMap<>(2);
        body.put("begin_date", beginDate.format(DATE_FORMATTER));
        body.put("end_date", endDate.format(DATE_FORMATTER));

        return postStatApi(accessToken, "/datacube/getarticlesummary", body)
                .doOnSuccess(result -> log.info("获取图文群发每日数据成功"))
                .doOnError(e -> log.error("获取图文群发每日数据失败", e));
    }

    /**
     * 统一的统计数据POST请求方法
     *
     * <p>封装统计数据API的通用请求逻辑，包括错误处理和日志记录。
     *
     * @param accessToken 微信全局AccessToken
     * @param path API路径
     * @param body 请求体
     * @return 统计响应数据Map
     */
    private Mono<Map<String, Object>> postStatApi(String accessToken, String path, Map<String, Object> body) {
        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .transform(response -> WeChatJsonCallTemplate.requireSuccess(response, "统计数据API调用"));
    }
}
