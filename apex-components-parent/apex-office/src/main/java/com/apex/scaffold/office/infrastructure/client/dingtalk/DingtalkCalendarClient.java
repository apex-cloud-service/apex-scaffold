package com.apex.scaffold.office.infrastructure.client.dingtalk;

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
 * 钉钉日历 API 客户端。
 *
 * <p>对接钉钉日历开放接口。钉钉事件同样以日历为父资源，本系统事件模型不保存
 * 日历标识，因此统一使用组织者主日历（钉钉主日历 ID 与用户 unionId 一致），
 * 由调用方传入的 {@code organizerId} 承担该角色。
 *
 * <p>凭据沿用 {@code apex.office.platforms[DINGTALK]}，与钉钉审批客户端一致；
 * 未配置时在发请求前以业务异常失败。
 *
 * <p>已知边界：详情/更新端口签名缺组织者，无法定位父日历，故作明确拒绝。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Component("dingtalkCalendarClient")
public class DingtalkCalendarClient extends AbstractOfficeApiSupport implements CalendarClient {

    /** 钉钉日历接口时间格式（本地时间，不含偏移）。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 单页拉取条数。 */
    private static final int PAGE_SIZE = 100;

    /** 缺少父日历标识的提示后缀。 */
    private static final String NEEDS_CALENDAR = "；钉钉事件操作需要父日历标识（以组织者主日历为父资源），当前端口签名未提供组织者";

    /**
     * 构造钉钉日历客户端。
     *
     * @param builder 用于创建钉钉 HTTP 客户端的构建器
     * @param properties 平台凭证及请求超时配置
     */
    public DingtalkCalendarClient(WebClient.Builder builder, OfficePlatformProperties properties) {
        super(builder, properties, OfficePlatform.DINGTALK, "https://api.dingtalk.com");
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
        Map<String, Object> body = new HashMap<>(8);
        body.put("summary", required(title, "钉钉日历事件标题"));
        body.put("description", description == null ? "" : description);
        body.put("start", time(startTime, "开始时间"));
        body.put("end", time(endTime, "结束时间"));
        if (location != null && !location.isBlank()) {
            body.put("location", Map.of("displayName", location));
        }
        if (participantIds != null && participantIds.length > 0) {
            List<Map<String, Object>> attendees = new ArrayList<>();
            for (String participantId : participantIds) {
                if (participantId != null && !participantId.isBlank()) {
                    attendees.add(Map.of("id", participantId, "isOptional", false));
                }
            }
            body.put("attendees", attendees);
        }
        body.put("onlineMeetingInfo", Map.of("type", "none"));
        String calendarId = required(organizerId, "钉钉组织者 unionId（主日历）");
        return token().flatMap(token -> call(
                response -> text(response.get("id"), "钉钉 eventId"),
                postJson(
                        "/v1.0/calendar/users/" + calendarId + "/calendars/primary/events",
                        body,
                        Map.of("x-acs-dingtalk-access-token", token))));
    }

    @Override
    public Mono<Map<String, Object>> getEventDetail(String platformEventId) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉日历事件详情不可用" + NEEDS_CALENDAR));
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
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉日历事件更新不可用" + NEEDS_CALENDAR));
    }

    @Override
    public Mono<Boolean> cancelEvent(String platformEventId, String organizerId) {
        return deleteEvent(platformEventId, organizerId);
    }

    @Override
    public Mono<Boolean> deleteEvent(String platformEventId, String organizerId) {
        String calendarId = required(organizerId, "钉钉组织者 unionId（主日历）");
        return token().flatMap(token -> call(
                body -> Boolean.TRUE,
                deleteWithHeaders(
                        "/v1.0/calendar/users/" + calendarId + "/calendars/primary/events/"
                                + required(platformEventId, "钉钉 eventId"),
                        Map.of("x-acs-dingtalk-access-token", token))));
    }

    @Override
    public Mono<List<Map<String, Object>>> listEvents(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, String> params = new HashMap<>(6);
        params.put("timeMin", requireTime(startTime, "开始时间").format(TIME_FORMAT));
        params.put("timeMax", requireTime(endTime, "结束时间").format(TIME_FORMAT));
        params.put("maxResults", String.valueOf(PAGE_SIZE));
        String calendarId = required(userId, "钉钉用户 unionId（主日历）");
        return token().flatMap(token -> call(
                this::items,
                getJsonWithHeadersAndParams(
                        "/v1.0/calendar/users/" + calendarId + "/calendars/primary/events",
                        params,
                        Map.of("x-acs-dingtalk-access-token", token))));
    }

    @Override
    public Mono<Boolean> checkMeetingRoomAvailability(
            String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉会议室可用性查询需要会议室分组与房间标识，当前端口未提供该标识"));
    }

    @Override
    public String getPlatformType() {
        return "dingtalk";
    }

    /**
     * 获取企业内应用访问令牌。
     *
     * @return access_token 信号
     */
    private Mono<String> token() {
        return call(
                body -> text(body.get("accessToken"), "钉钉 accessToken"),
                postJson(
                        "/v1.0/oauth2/accessToken",
                        Map.of(
                                "appKey", required(properties.getAppId(), "钉钉 appKey"),
                                "appSecret", secret("钉钉"))));
    }

    /** 构造钉钉时间对象。 */
    private Map<String, Object> time(LocalDateTime value, String field) {
        Map<String, Object> time = new HashMap<>(4);
        time.put("dateTime", requireTime(value, field).format(TIME_FORMAT));
        time.put("timeZone", ZoneId.systemDefault().getId());
        return time;
    }

    /** 校验时间字段非空。 */
    private LocalDateTime requireTime(LocalDateTime value, String field) {
        if (value == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉" + field + "未配置");
        }
        return value;
    }

    /** 从列表响应读取事件集合。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> body) {
        Object items = body.get("events");
        return items instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
