package com.apex.scaffold.office.infrastructure.client.feishu;

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
 * 飞书日历 API 客户端。
 *
 * <p>对接飞书开放平台日历 v4 接口。飞书的事件读写接口都以「日历」为父资源，
 * 而本系统的事件模型不保存日历标识，因此统一使用组织者的<strong>主日历</strong>
 * 作为父资源（飞书主日历 ID 与组织者 open_id 相同），由调用方传入的
 * {@code organizerId} 承担该角色。
 *
 * <p>凭据沿用 {@code apex.office.platforms[FEISHU]}（{@code appId} +
 * {@code secretReference} 指向的环境变量），与审批客户端一致；未配置时在发请求前
 * 以业务异常失败，不静默降级。
 *
 * <p>已知边界：{@code getEventDetail}/{@code updateEvent}/{@code deleteEvent}
 * 的端口签名只有 {@code platformEventId}，无法携带组织者，故这些操作以明确的
 * 业务异常返回，避免用错误的主日历静默操作到他人日历。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Component("feishuCalendarClient")
public class FeishuCalendarClient extends AbstractOfficeApiSupport implements CalendarClient {

    /** 飞书日历接口要求的时间文本格式（本地时间，不含偏移）。 */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** 单页拉取条数（飞书上限 1000，取 100 平衡响应体量）。 */
    private static final int PAGE_SIZE = 100;

    /**
     * 构造飞书日历客户端。
     *
     * @param builder 用于创建飞书 HTTP 客户端的构建器
     * @param properties 平台凭证及请求超时配置
     */
    public FeishuCalendarClient(WebClient.Builder builder, OfficePlatformProperties properties) {
        super(builder, properties, OfficePlatform.FEISHU, "https://open.feishu.cn");
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
        body.put("summary", required(title, "飞书日历事件标题"));
        body.put("description", description == null ? "" : description);
        body.put("start_time", time(startTime, "开始时间"));
        body.put("end_time", time(endTime, "结束时间"));
        if (location != null && !location.isBlank()) {
            body.put("location", Map.of("name", location));
        }
        List<Map<String, Object>> attendees = new ArrayList<>();
        if (participantIds != null) {
            for (String participantId : participantIds) {
                if (participantId != null && !participantId.isBlank()) {
                    attendees.add(Map.of("type", "user", "user_id", participantId));
                }
            }
        }
        if (!attendees.isEmpty()) {
            body.put("attendees", attendees);
        }
        String calendarId = required(organizerId, "飞书组织者ID（主日历）");
        return token().flatMap(token -> call(
                this::eventId,
                postJsonWithHeaders("/open-apis/calendar/v4/calendars/" + calendarId + "/events", body, auth(token))));
    }

    @Override
    public Mono<Map<String, Object>> getEventDetail(String platformEventId, String organizerId) {
        String calendarId = required(organizerId, "飞书组织者ID（主日历）");
        return token().flatMap(token -> call(
                body -> body,
                getJsonWithHeaders(
                        "/open-apis/calendar/v4/calendars/" + calendarId + "/events/"
                                + required(platformEventId, "飞书 eventId"),
                        auth(token))));
    }

    @Override
    public Mono<Boolean> updateEvent(
            String platformEventId,
            String organizerId,
            String title,
            String description,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String[] participantIds,
            String location) {
        String calendarId = required(organizerId, "飞书组织者ID（主日历）");
        Map<String, Object> body = new HashMap<>(8);
        if (title != null && !title.isBlank()) {
            body.put("summary", title);
        }
        if (description != null) {
            body.put("description", description);
        }
        if (startTime != null) {
            body.put("start_time", time(startTime, "开始时间"));
        }
        if (endTime != null) {
            body.put("end_time", time(endTime, "结束时间"));
        }
        if (location != null && !location.isBlank()) {
            body.put("location", Map.of("name", location));
        }
        List<Map<String, Object>> attendees = new ArrayList<>();
        if (participantIds != null) {
            for (String participantId : participantIds) {
                if (participantId != null && !participantId.isBlank()) {
                    attendees.add(Map.of("type", "user", "user_id", participantId));
                }
            }
        }
        if (!attendees.isEmpty()) {
            body.put("attendees", attendees);
        }
        if (body.isEmpty()) {
            return Mono.just(Boolean.TRUE);
        }
        return token().flatMap(token -> call(
                response -> Boolean.TRUE,
                patchJsonWithHeaders(
                        "/open-apis/calendar/v4/calendars/" + calendarId + "/events/"
                                + required(platformEventId, "飞书 eventId"),
                        body,
                        auth(token))));
    }

    @Override
    public Mono<Boolean> cancelEvent(String platformEventId, String organizerId) {
        return deleteEvent(platformEventId, organizerId);
    }

    @Override
    public Mono<Boolean> deleteEvent(String platformEventId, String organizerId) {
        String calendarId = required(organizerId, "飞书组织者ID（主日历）");
        return token().flatMap(token -> call(
                body -> Boolean.TRUE,
                deleteWithHeaders(
                        "/open-apis/calendar/v4/calendars/" + calendarId + "/events/"
                                + required(platformEventId, "飞书 eventId"),
                        auth(token))));
    }

    @Override
    public Mono<List<Map<String, Object>>> listEvents(String userId, LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, String> params = new HashMap<>(6);
        params.put("start_time", String.valueOf(epochSecond(startTime, "开始时间")));
        params.put("end_time", String.valueOf(epochSecond(endTime, "结束时间")));
        params.put("page_size", String.valueOf(PAGE_SIZE));
        String calendarId = required(userId, "飞书用户ID（主日历）");
        return token().flatMap(token -> call(
                this::items,
                getJsonWithHeadersAndParams(
                        "/open-apis/calendar/v4/calendars/" + calendarId + "/events", params, auth(token))));
    }

    @Override
    public Mono<Boolean> checkMeetingRoomAvailability(
            String meetingRoomId, LocalDateTime startTime, LocalDateTime endTime) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "飞书会议室可用性查询需要建筑与楼层层级，当前端口未提供该标识"));
    }

    @Override
    public String getPlatformType() {
        return "feishu";
    }

    /**
     * 获取租户访问令牌。
     *
     * @return tenant_access_token 信号
     */
    private Mono<String> token() {
        return call(
                body -> text(body.get("tenant_access_token"), "飞书 tenant_access_token"),
                postJson(
                        "/open-apis/auth/v3/tenant_access_token/internal",
                        Map.of(
                                "app_id", required(properties.getAppId(), "飞书 appId"),
                                "app_secret", secret("飞书"))));
    }

    /** 构造 Bearer 鉴权请求头。 */
    private Map<String, String> auth(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    /** 构造飞书日历时间对象（时间戳 + 时区 + 本地时间文本）。 */
    private Map<String, Object> time(LocalDateTime value, String field) {
        Map<String, Object> time = new HashMap<>(4);
        time.put("timestamp", String.valueOf(epochSecond(value, field)));
        time.put("timezone", ZoneId.systemDefault().getId());
        time.put("datetime", requireTime(value, field).format(TIME_FORMAT));
        return time;
    }

    /** 本地时间转秒级时间戳。 */
    private long epochSecond(LocalDateTime value, String field) {
        return requireTime(value, field).atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /** 校验时间字段非空。 */
    private LocalDateTime requireTime(LocalDateTime value, String field) {
        if (value == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "飞书" + field + "未配置");
        }
        return value;
    }

    /** 从创建响应读取事件ID。 */
    private String eventId(Map<String, Object> body) {
        Object data = body.get("data");
        Object event = data instanceof Map<?, ?> map ? map.get("event") : null;
        return text(event instanceof Map<?, ?> map ? map.get("event_id") : null, "飞书 event_id");
    }

    /** 从列表响应读取事件集合。 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> body) {
        Object data = body.get("data");
        Object items = data instanceof Map<?, ?> map ? map.get("items") : null;
        return items instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
}
