package com.apex.scaffold.office.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.infrastructure.client.dingtalk.DingtalkCalendarClient;
import com.apex.scaffold.office.infrastructure.client.feishu.FeishuCalendarClient;
import com.apex.scaffold.office.infrastructure.client.wecom.WecomCalendarClient;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 三平台日历客户端回归测试。
 *
 * <p>覆盖：Bean 名与 {@code OfficeCalendarService#getCalendarClient} 的
 * {@code <platform>CalendarClient} 查找口径一致；创建/删除/范围查询的请求方法与
 * 路径；平台标识；未配置凭据与端口签名缺组织者时的显式失败语义。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("三平台日历客户端")
class CalendarClientContractTest {

    /** 记录被拦截的请求，便于断言方法与路径。 */
    private final List<ClientRequest> requests = new ArrayList<>();

    /**
     * 构造按响应序列作答的 WebClient.Builder。
     *
     * @param bodies 依次返回的响应体 JSON
     * @return 已安装 exchangeFunction 的构建器
     */
    private WebClient.Builder builder(String... bodies) {
        return WebClient.builder().exchangeFunction(request -> {
            requests.add(request);
            String body = bodies[Math.min(requests.size() - 1, bodies.length - 1)];
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        });
    }

    /** 构造已配置 appId 与密钥环境变量的平台配置。 */
    private OfficePlatformProperties properties(OfficePlatform platform, String appId, String secretReference) {
        OfficePlatformProperties properties = new OfficePlatformProperties();
        OfficePlatformProperties.Platform config = new OfficePlatformProperties.Platform();
        config.setAppId(appId);
        config.setSecretReference(secretReference);
        properties.getPlatforms().put(platform, config);
        return properties;
    }

    @Test
    @DisplayName("飞书创建事件：取 tenant_access_token 后 POST 到组织者主日历 events 并解析 event_id")
    void feishuCreateEvent() {
        // PATH 指向必然存在且稳定的环境变量，避免测试依赖外部配置
        FeishuCalendarClient client = new FeishuCalendarClient(
                builder(
                        "{\"code\":0,\"tenant_access_token\":\"t-feishu\"}",
                        "{\"code\":0,\"data\":{\"event\":{\"event_id\":\"evt-1\"}}}"),
                properties(OfficePlatform.FEISHU, "app-1", "PATH"));

        StepVerifier.create(client.createEvent(
                        "ou-organizer",
                        "周会",
                        "描述",
                        LocalDateTime.of(2026, 11, 20, 10, 0),
                        LocalDateTime.of(2026, 11, 20, 11, 0),
                        new String[] {"ou-1"},
                        "A座301",
                        15))
                .expectNext("evt-1")
                .verifyComplete();

        assertThat(client.getPlatformType()).isEqualTo("feishu");
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).method()).isEqualTo(HttpMethod.POST);
        assertThat(requests.get(0).url().getPath()).isEqualTo("/open-apis/auth/v3/tenant_access_token/internal");
        assertThat(requests.get(1).method()).isEqualTo(HttpMethod.POST);
        assertThat(requests.get(1).url().getPath()).isEqualTo("/open-apis/calendar/v4/calendars/ou-organizer/events");
        assertThat(requests.get(1).headers().getFirst("Authorization")).isEqualTo("Bearer t-feishu");
    }

    @Test
    @DisplayName("飞书范围查询：GET 组织者主日历 events 并带时间与分页参数")
    void feishuListEvents() {
        FeishuCalendarClient client = new FeishuCalendarClient(
                builder(
                        "{\"code\":0,\"tenant_access_token\":\"t\"}",
                        "{\"code\":0,\"data\":{\"items\":[{\"event_id\":\"e1\"}]}}"),
                properties(OfficePlatform.FEISHU, "app-1", "PATH"));

        StepVerifier.create(client.listEvents(
                        "ou-organizer",
                        LocalDateTime.of(2026, 11, 1, 0, 0),
                        LocalDateTime.of(2026, 11, 30, 23, 59, 59)))
                .assertNext(items -> assertThat(items).hasSize(1))
                .verifyComplete();

        assertThat(requests.get(1).method()).isEqualTo(HttpMethod.GET);
        assertThat(requests.get(1).url().getPath()).isEqualTo("/open-apis/calendar/v4/calendars/ou-organizer/events");
        assertThat(requests.get(1).url().getQuery()).contains("start_time=", "end_time=", "page_size=100");
    }

    @Test
    @DisplayName("飞书详情：GET 主日历下的事件详情")
    void feishuGetEventDetail() {
        FeishuCalendarClient client = new FeishuCalendarClient(
                builder("{\"code\":0,\"tenant_access_token\":\"t\"}", "{\"code\":0,\"data\":{\"event\":{}}}"),
                properties(OfficePlatform.FEISHU, "app-1", "PATH"));

        StepVerifier.create(client.getEventDetail("evt-1", "ou-organizer"))
                .assertNext(body -> assertThat(body).containsKey("data"))
                .verifyComplete();

        assertThat(requests.get(1).method()).isEqualTo(HttpMethod.GET);
        assertThat(requests.get(1).url().getPath())
                .isEqualTo("/open-apis/calendar/v4/calendars/ou-organizer/events/evt-1");
    }

    @Test
    @DisplayName("飞书更新：PATCH 主日历下的事件，空载荷时短路不发请求")
    void feishuUpdateEvent() {
        FeishuCalendarClient client = new FeishuCalendarClient(
                builder("{\"code\":0,\"tenant_access_token\":\"t\"}", "{\"code\":0}"),
                properties(OfficePlatform.FEISHU, "app-1", "PATH"));

        StepVerifier.create(client.updateEvent("evt-1", "ou-organizer", "新标题", null, null, null, null, null))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
        assertThat(requests.get(1).method()).isEqualTo(HttpMethod.PATCH);
        assertThat(requests.get(1).url().getPath())
                .isEqualTo("/open-apis/calendar/v4/calendars/ou-organizer/events/evt-1");

        // 全部字段为空时不产生第二次业务请求
        int before = requests.size();
        StepVerifier.create(client.updateEvent("evt-1", "ou-organizer", null, null, null, null, null, null))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
        assertThat(requests).hasSize(before);
    }

    @Test
    @DisplayName("缺组织者时详情/更新显式拒绝，不发平台请求")
    void feishuRejectsOperationsWithoutOrganizer() {
        FeishuCalendarClient client =
                new FeishuCalendarClient(builder("{}"), properties(OfficePlatform.FEISHU, "app-1", "PATH"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getEventDetail("evt-1", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织者ID");
        // 组织者校验同样在组装阶段同步 fail-fast，故断言同步异常
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> client.updateEvent("evt-1", " ", "标题", null, LocalDateTime.now(), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("组织者ID");
        assertThat(requests).isEmpty();
    }

    @Test
    @DisplayName("钉钉创建事件：POST 到主日历 events 并解析 id")
    void dingtalkCreateEvent() {
        DingtalkCalendarClient client = new DingtalkCalendarClient(
                builder("{\"accessToken\":\"t-ding\"}", "{\"id\":\"evt-ding\"}"),
                properties(OfficePlatform.DINGTALK, "ding-app", "PATH"));

        StepVerifier.create(client.createEvent(
                        "union-organizer",
                        "评审",
                        null,
                        LocalDateTime.of(2026, 11, 21, 9, 0),
                        LocalDateTime.of(2026, 11, 21, 10, 0),
                        null,
                        null,
                        10))
                .expectNext("evt-ding")
                .verifyComplete();

        assertThat(client.getPlatformType()).isEqualTo("dingtalk");
        assertThat(requests.get(1).url().getPath())
                .isEqualTo("/v1.0/calendar/users/union-organizer/calendars/primary/events");
        assertThat(requests.get(1).headers().getFirst("x-acs-dingtalk-access-token"))
                .isEqualTo("t-ding");
    }

    @Test
    @DisplayName("企业微信创建日程：POST add 并解析 schedule_id；删除按 schedule_id + userid")
    void wecomCreateAndDelete() {
        WecomCalendarClient client = new WecomCalendarClient(
                builder(
                        "{\"errcode\":0,\"access_token\":\"t-wecom\"}",
                        "{\"errcode\":0,\"schedule_id\":\"sch-1\"}",
                        "{\"errcode\":0,\"access_token\":\"t-wecom\"}",
                        "{\"errcode\":0}"),
                properties(OfficePlatform.WECOM, "corp-1", "PATH"));

        StepVerifier.create(client.createEvent(
                        "zhangsan",
                        "周报会",
                        null,
                        LocalDateTime.of(2026, 11, 22, 14, 0),
                        LocalDateTime.of(2026, 11, 22, 15, 0),
                        new String[] {"lisi"},
                        null,
                        5))
                .expectNext("sch-1")
                .verifyComplete();
        StepVerifier.create(client.deleteEvent("sch-1", "zhangsan"))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        assertThat(client.getPlatformType()).isEqualTo("wecom");
        assertThat(requests.get(1).url().getPath()).isEqualTo("/cgi-bin/oa/schedule/add");
        assertThat(requests.get(3).url().getPath()).isEqualTo("/cgi-bin/oa/schedule/del");
    }

    @Test
    @DisplayName("未配置 appId 时在发请求前以业务异常失败（fail-closed）")
    void failsClosedWithoutCredentials() {
        FeishuCalendarClient client = new FeishuCalendarClient(builder("{}"), new OfficePlatformProperties());

        // 凭据校验在组装 Mono 时同步抛出（发请求前 fail-closed），因此断言同步异常
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.createEvent(
                        "o", "t", null, LocalDateTime.now(), LocalDateTime.now().plusHours(1), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appId");
        assertThat(requests).isEmpty();
    }
}
