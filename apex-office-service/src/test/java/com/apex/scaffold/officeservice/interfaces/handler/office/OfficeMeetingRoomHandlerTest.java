package com.apex.scaffold.officeservice.interfaces.handler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.application.OfficeMeetingRoomService;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.meetingroom.domain.entity.MeetingRoomEntity;
import com.apex.scaffold.officeservice.interfaces.assembler.office.MeetingRoomAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.BookMeetingRoomRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.CreateMeetingRoomRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.MeetingRoomBookingResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.meetingroom.MeetingRoomResponse;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 会议室资源处理器单元测试。
 *
 * <p>覆盖租户与操作人从认证主体取值的约束，以及主要用例的正向流转。
 * {@link MockServerRequest} 不支持直接设置 principal，因此统一经 {@link ServerRequest} mock 注入。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@DisplayName("会议室资源处理器单元测试")
class OfficeMeetingRoomHandlerTest {

    private static final String TENANT_ID = "41";

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OfficeMeetingRoomService meetingRoomService;
    private MeetingRoomAssembler assembler;
    private OfficeMeetingRoomHandler handler;

    @BeforeEach
    void setUp() {
        meetingRoomService = mock(OfficeMeetingRoomService.class);
        assembler = mock(MeetingRoomAssembler.class);
        handler = new OfficeMeetingRoomHandler(meetingRoomService, assembler, requestHandlerUtil);
    }

    private AuthenticatedUser user(long userId) {
        return new AuthenticatedUser(userId, "open-" + userId, TENANT_ID);
    }

    private MeetingRoomEntity room() {
        MeetingRoomEntity entity = new MeetingRoomEntity();
        entity.setId(10L);
        entity.setTenantId(TENANT_ID);
        entity.setRoomCode("R-101");
        entity.setRoomName("第一会议室");
        return entity;
    }

    private MeetingRoomResponse roomResponse() {
        return MeetingRoomResponse.builder()
                .id(10L)
                .roomCode("R-101")
                .roomName("第一会议室")
                .build();
    }

    /** 构造带认证主体、路径参数、查询参数与请求体的 ServerRequest。 */
    private ServerRequest request(
            Principal principal,
            Map<String, String> pathVariables,
            Map<String, String> queryParams,
            Object body,
            Class<?> bodyType) {
        ServerRequest request = mock(ServerRequest.class);
        // principal() 与 bodyToMono(Class<?>) 的返回类型含通配符，只能用 doReturn 规避捕获推断
        doReturn(principal == null ? Mono.<Principal>empty() : Mono.just(principal))
                .when(request)
                .principal();
        when(request.pathVariable(any())).thenAnswer(invocation -> pathVariables.get(invocation.getArgument(0)));
        when(request.queryParam(any()))
                .thenAnswer(invocation -> java.util.Optional.ofNullable(queryParams.get(invocation.getArgument(0))));
        if (bodyType != null) {
            doReturn(Mono.just(body)).when(request).bodyToMono(bodyType);
        }
        return request;
    }

    private ServerRequest request(Principal principal) {
        return request(principal, Map.of(), Map.of(), null, null);
    }

    private ServerRequest request(Principal principal, String roomId) {
        return request(principal, Map.of("roomId", roomId), Map.of(), null, null);
    }

    @Nested
    @DisplayName("认证上下文")
    class AuthenticationTest {

        /**
         * 未认证请求实际由安全过滤器拦截并返回 401；此处覆盖处理器自身的兜底路径，
         * 经 {@code RequestHandlerUtil.handleError} 收敛为 400，业务码仍为 {@code UNAUTHORIZED}。
         */
        @Test
        @DisplayName("缺少认证主体时拒绝且不触达应用服务")
        void shouldRejectWithoutPrincipal() {
            StepVerifier.create(handler.getMeetingRoom(request(null, "10")))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(400))
                    .verifyComplete();

            verifyNoInteractions(meetingRoomService);
        }

        @Test
        @DisplayName("认证主体缺少租户时拒绝")
        void shouldRejectWithoutTenant() {
            AuthenticatedUser noTenant = new AuthenticatedUser(100L, "open", " ");

            StepVerifier.create(handler.getMeetingRoom(request(noTenant, "10")))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(400))
                    .verifyComplete();

            verifyNoInteractions(meetingRoomService);
        }
    }

    @Nested
    @DisplayName("查询")
    class QueryTest {

        @Test
        @DisplayName("详情查询使用认证租户")
        void shouldGetDetailWithTrustedTenant() {
            when(meetingRoomService.getMeetingRoom(TENANT_ID, 10L)).thenReturn(Mono.just(room()));
            when(assembler.toResponse(any(MeetingRoomEntity.class))).thenReturn(roomResponse());

            StepVerifier.create(handler.getMeetingRoom(request(user(100L), "10")))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(meetingRoomService).getMeetingRoom(TENANT_ID, 10L);
        }

        @Test
        @DisplayName("分页查询缺省页码为 1、每页 20")
        void shouldUseDefaultPaging() {
            PageResponse<MeetingRoomEntity> page = PageResponse.of(List.of(room()), 1L, 1, 20);
            when(meetingRoomService.listMeetingRooms(TENANT_ID, 1, 20, null, null))
                    .thenReturn(Mono.just(page));

            StepVerifier.create(handler.listMeetingRooms(request(user(100L))))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(meetingRoomService).listMeetingRooms(TENANT_ID, 1, 20, null, null);
        }

        @Test
        @DisplayName("可用会议室查询透传时间区间")
        void shouldFindAvailableRooms() {
            LocalDateTime start = LocalDateTime.of(2026, 10, 28, 9, 0);
            LocalDateTime end = start.plusHours(1);
            when(meetingRoomService.findAvailableMeetingRooms(TENANT_ID, start, end))
                    .thenReturn(Mono.just(List.of(room())));
            when(assembler.toResponseList(any())).thenReturn(List.of(roomResponse()));

            StepVerifier.create(handler.findAvailableMeetingRooms(request(
                            user(100L),
                            Map.of(),
                            Map.of("startTime", start.toString(), "endTime", end.toString()),
                            null,
                            null)))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(meetingRoomService).findAvailableMeetingRooms(TENANT_ID, start, end);
        }

        @Test
        @DisplayName("时间参数格式错误返回 400")
        void shouldRejectMalformedTime() {
            StepVerifier.create(handler.findAvailableMeetingRooms(request(
                            user(100L),
                            Map.of(),
                            Map.of("startTime", "not-a-time", "endTime", "also-bad"),
                            null,
                            null)))
                    .assertNext(response ->
                            assertThat(response.statusCode().is4xxClientError()).isTrue())
                    .verifyComplete();

            verifyNoInteractions(meetingRoomService);
        }
    }

    @Nested
    @DisplayName("写入")
    class WriteTest {

        @Test
        @DisplayName("创建会议室使用认证租户")
        void shouldCreateWithTrustedTenant() {
            CreateMeetingRoomRequest request = CreateMeetingRoomRequest.builder()
                    .roomCode("R-101")
                    .roomName("第一会议室")
                    .build();
            when(assembler.toEntity(request)).thenReturn(room());
            when(meetingRoomService.createMeetingRoom(eq(TENANT_ID), any(MeetingRoomEntity.class)))
                    .thenReturn(Mono.just(room()));
            when(assembler.toResponse(any(MeetingRoomEntity.class))).thenReturn(roomResponse());

            StepVerifier.create(handler.createMeetingRoom(
                            request(user(100L), Map.of(), Map.of(), request, CreateMeetingRoomRequest.class)))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(meetingRoomService).createMeetingRoom(eq(TENANT_ID), any(MeetingRoomEntity.class));
        }

        @Test
        @DisplayName("预订会议室以认证用户作为预订人")
        void shouldBookWithAuthenticatedUser() {
            LocalDateTime start = LocalDateTime.of(2026, 10, 28, 9, 0);
            LocalDateTime end = start.plusHours(1);
            BookMeetingRoomRequest request = BookMeetingRoomRequest.builder()
                    .title("周会")
                    .startTime(start)
                    .endTime(end)
                    .build();
            CalendarEvent event = CalendarEvent.builder().id(99L).build();
            MeetingRoomBookingResponse booking =
                    MeetingRoomBookingResponse.builder().eventId(99L).build();
            when(meetingRoomService.bookMeetingRoom(TENANT_ID, 10L, "100", null, "周会", start, end))
                    .thenReturn(Mono.just(event));
            when(meetingRoomService.getMeetingRoom(TENANT_ID, 10L)).thenReturn(Mono.just(room()));
            when(assembler.toBookingResponse(any(CalendarEvent.class), any(MeetingRoomEntity.class)))
                    .thenReturn(booking);

            StepVerifier.create(handler.bookMeetingRoom(request(
                            user(100L), Map.of("roomId", "10"), Map.of(), request, BookMeetingRoomRequest.class)))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(meetingRoomService).bookMeetingRoom(TENANT_ID, 10L, "100", null, "周会", start, end);
        }

        @Test
        @DisplayName("取消预订同时透传认证用户作为操作人")
        void shouldCancelWithOperator() {
            CalendarEvent event = CalendarEvent.builder().id(99L).build();
            MeetingRoomBookingResponse booking =
                    MeetingRoomBookingResponse.builder().eventId(99L).build();
            when(meetingRoomService.cancelBooking(TENANT_ID, 10L, 99L, "100")).thenReturn(Mono.just(event));
            when(meetingRoomService.getMeetingRoom(TENANT_ID, 10L)).thenReturn(Mono.just(room()));
            when(assembler.toBookingResponse(any(CalendarEvent.class), any(MeetingRoomEntity.class)))
                    .thenReturn(booking);

            StepVerifier.create(handler.cancelMeetingRoomBooking(
                            request(user(100L), Map.of("roomId", "10", "eventId", "99"), Map.of(), null, null)))
                    .assertNext(response ->
                            assertThat(response.statusCode().value()).isEqualTo(200))
                    .verifyComplete();

            verify(meetingRoomService).cancelBooking(TENANT_ID, 10L, 99L, "100");
        }
    }
}
