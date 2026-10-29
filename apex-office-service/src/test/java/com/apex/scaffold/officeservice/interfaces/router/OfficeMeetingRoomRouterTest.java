package com.apex.scaffold.officeservice.interfaces.router;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.OfficeMeetingRoomService;
import com.apex.scaffold.officeservice.interfaces.assembler.office.MeetingRoomAssembler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeMeetingRoomHandler;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeMeetingRoomRouter;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 会议室路由匹配回归测试。
 *
 * <p>复核 {@code /enabled}、{@code /available} 等静态路径与 {@code /{roomId}} 的匹配优先级。
 * 处理器从 {@code ServerRequest.principal()} 读取租户，而 WebTestClient 的安全上下文
 * 暴露为 {@code Authentication}，经类型过滤后必然回退为未授权，因此带认证主体的场景
 * 交由 {@code OfficeMeetingRoomHandlerTest} 覆盖，此处只断言路由与未授权落点。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@DisplayName("会议室路由匹配回归测试")
class OfficeMeetingRoomRouterTest {

    private static final String BASE_PATH = "/api/v2/office/meeting-rooms";

    private OfficeMeetingRoomService meetingRoomService;
    private MeetingRoomAssembler assembler;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        meetingRoomService = mock(OfficeMeetingRoomService.class);
        assembler = mock(MeetingRoomAssembler.class);
        OfficeMeetingRoomHandler handler = new OfficeMeetingRoomHandler(
                meetingRoomService,
                assembler,
                new RequestHandlerUtil(Validation.buildDefaultValidatorFactory().getValidator()));
        RouterFunction<ServerResponse> routes = new OfficeMeetingRoomRouter(handler).officeMeetingRoomRoutes();
        client = WebTestClient.bindToRouterFunction(routes).build();
    }

    /**
     * 断言请求被拒绝且未进入业务逻辑。
     *
     * <p>路由器单独绑定时没有安全过滤器，处理器兜底逻辑经 {@code RequestHandlerUtil.handleError}
     * 统一映射为 HTTP 400，未授权语义体现在响应体的业务码上。
     */
    private void expectUnauthorized(WebTestClient.RequestHeadersSpec<?> request) {
        request.exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(ResultCode.UNAUTHORIZED.getCode());
    }

    /** {@code /enabled} 必须匹配独立路由，而不是把 enabled 当作会议室 ID 落进详情。 */
    @Test
    @DisplayName("静态路径 /enabled 命中启用会议室路由")
    void shouldRouteEnabledToEnabledList() {
        expectUnauthorized(client.get().uri(BASE_PATH + "/enabled"));

        verify(meetingRoomService, never()).getMeetingRoom(anyString(), any());
        verifyNoInteractions(meetingRoomService);
    }

    /** {@code /available} 必须匹配独立路由，而不是被 {@code /{roomId}} 抢占。 */
    @Test
    @DisplayName("静态路径 /available 命中可用性路由")
    void shouldRouteAvailableToAvailabilityQuery() {
        expectUnauthorized(client.get().uri(builder -> builder.path(BASE_PATH + "/available")
                .queryParam("startTime", "2026-10-28T09:00:00")
                .queryParam("endTime", "2026-10-28T10:00:00")
                .build()));

        verifyNoInteractions(meetingRoomService);
    }

    /** 数字路径段应匹配会议室详情路由。 */
    @Test
    @DisplayName("数字路径段命中会议室详情路由")
    void shouldRouteNumericIdToDetail() {
        expectUnauthorized(client.get().uri(BASE_PATH + "/10"));

        verifyNoInteractions(meetingRoomService);
    }

    /** 列表路由与详情路由前缀相同，需确认根路径仍然可达。 */
    @Test
    @DisplayName("根路径命中会议室列表路由")
    void shouldRouteRootToList() {
        expectUnauthorized(client.get().uri(BASE_PATH));

        verifyNoInteractions(meetingRoomService);
    }

    /** 取消预订使用嵌套路径，需确认路由存在且不会落到详情。 */
    @Test
    @DisplayName("取消预订命中嵌套路径")
    void shouldRouteCancelBooking() {
        expectUnauthorized(client.delete().uri(BASE_PATH + "/10/bookings/99"));

        verifyNoInteractions(meetingRoomService);
    }

    /** 未认证请求不得触达应用服务。 */
    @Test
    @DisplayName("缺少认证上下文时回退为未授权")
    void shouldRejectUnauthenticatedRequest() {
        expectUnauthorized(client.get().uri(BASE_PATH + "/10"));

        verifyNoInteractions(meetingRoomService);
    }
}
