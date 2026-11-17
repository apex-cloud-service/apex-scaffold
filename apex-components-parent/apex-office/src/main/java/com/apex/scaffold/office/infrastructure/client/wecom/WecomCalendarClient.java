package com.apex.scaffold.office.infrastructure.client.wecom;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.infrastructure.client.AbstractOfficeApiSupport;
import com.apex.scaffold.office.infrastructure.client.CalendarClient;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 企业微信日程 API 客户端。
 *
 * <p>企业微信的日程接口以「组织者」为主体，事件归属组织者个人日程（日程 ID 由平台在
 * 创建后返回），因此创建可直接使用 {@code organizerId}；详情/更新/删除仍需组织者
 * 定位日程归属，当前端口签名未提供，故作明确拒绝而非猜测归属。
 *
 * <p>凭据沿用 {@code apex.office.platforms[WECOM]}，与企业微信消息客户端一致；
 * 未配置时在发请求前以业务异常失败。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Component("wecomCalendarClient")
public class WecomCalendarClient extends AbstractOfficeApiSupport implements CalendarClient {

    /** 企业微信日程时间格式（本地时间，不含偏移）。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 单页拉取条数。 */
    private static final int PAGE_SIZE = 100;

    /** 缺少组织者标识的提示后缀。 */
    private static final String NEEDS_ORGANIZER = "；企业微信日程操作需要组织者定位日程归属，当前端口签名未提供组织者";

    /**
     * 构造企业微信日程客户端。
     *
     * @param builder 用于创建企业微信 HTTP 客户端的构建器
     * @param properties 平台凭证及请求超时配置
     */
    public WecomCalendarClient(WebClient.Builder builder, OfficePlatformProperties properties) {
        super(builder, properties, OfficePlatform.WECOM, "https://qyapi.weixin.qq.com");
    }

    @Override
    public Mono<String> createEvent(
            String organizerId,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String[] participantIds,
            String location,
            Integer reminderMinutes) {
        Map<String, Object> schedule = new HashMap<>(8);
        schedule.put("summary", required(title, "企业微信日程标题"));
        schedule.put("description", description == null ? "" : description);
        schedule.put("start_time", epochSecond(startTime, "开始时间"));
        schedule.put("end_time", epochSecond(endTime, "结束时间"));
        schedule.put("admins", List.of(required(organizerId, "企业微信组织者ID")));
        if (location != null && !location.isBlank()) {
            schedule.put("location", Map.of("name", location));
        }
        List<Map<String, Object>> attendees = new ArrayList<>();
        if (participantIds != null) {
            for (String participantId : participantIds) {
                if (participantId != null && !participantId.isBlank()) {
                    attendees.add(Map.of("userid", participantId));
                }
            }
        }
        if (!attendees.isEmpty()) {
            schedule.put("attendees", attendees);
        }
        return token().flatMap(token -> call(
                body -> text(body.get("schedule_id"), "企业微信 schedule_id"),
                postJson("/cgi-bin/oa/schedule/add?access_token=" + token, Map.of("schedule", schedule))));
    }

    @Override
    public Mono<Map<String, Object>> getEventDetail(String platformEventId) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信日程详情不可用" + NEEDS_ORGANIZER));
    }

    @Override
    public Mono<Boolean> updateEvent(
            String platformEventId,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String[] participantIds,
            String location) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信日程更新不可用" + NEEDS_ORGANIZER));
    }

    @Override
    public Mono<Boolean> cancelEvent(String platformEventId, String organizerId) {
        return deleteEvent(platformEventId, organizerId);
    }

    @Override
    public Mono<Boolean> deleteEvent(String platformEventId, String organizerId) {
        return token().flatMap(token -> call(
                body -> Boolean.TRUE,
                postJson(
                        "/cgi-bin/oa/schedule/del?access_token=" + token,
                        Map.of(
                                "schedule_id", required(platformEventId, "企业微信 schedule_id"),
                                "userid", required(organizerId, "企业微信组织者ID")))));
    }

    @Override
    public Mono<List<Map<String, Object>>> listEvents(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> body = new HashMap<>(6);
        body.put("userid", required(userId, "企业微信用户ID"));
        body.put("start_time", epochSecond(startTime, "开始时间"));
        body.put("end_time", epochSecond(endTime, "结束时间"));
        body.put("limit", PAGE_SIZE);
        return token().flatMap(token ->
                call(this::items, postJson("/cgi-bin/oa/schedule/get_by_range?access_token=" + token, body)));
    }

    @Override
    public Mono<Boolean> checkMeetingRoomAvailability(
            String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信会议室可用性查询需要会议室预订接口的独立应用权限，当前端口未提供"));
    }

    @Override
    public String getPlatformType() {
        return "wecom";
    }

    /**
     * 获取企业微信 access_token。
     *
     * @return access_token 信号
     */
    private Mono<String> token() {
        return call(
                body -> text(body.get("access_token"), "企业微信 access_token"),
                getJson(
                        "/cgi-bin/gettoken",
                        Map.of(
                                "corpid", required(properties.getAppId(), "企业微信 corpid"),
                                "corpsecret", secret("企业微信"))));
    }

    /** 本地时间转秒级时间戳。 */
    private long epochSecond(LocalDateTime value, String field) {
        return requireTime(value, field).atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /** 校验时间字段非空。 */
    private LocalDateTime requireTime(LocalDateTime value, String field) {
        if (value == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信" + field + "未配置");
        }
        return value;
    }

    /** 从范围查询响应读取日程集合。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> body) {
        Object scheduleList = body.get("schedule_list");
        return scheduleList instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
