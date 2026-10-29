package com.apex.scaffold.office.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.calendar.CalendarEvent;
import com.apex.scaffold.office.domain.calendar.CalendarEventRepository;
import com.apex.scaffold.office.meetingroom.domain.entity.MeetingRoomEntity;
import com.apex.scaffold.office.meetingroom.domain.repository.MeetingRoomRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 会议室资源应用服务单元测试。
 *
 * <p>覆盖编码/名称唯一性、停用与占用拒绝，以及取消预订的越权、幂等与归属校验。
 *
 * @author Apex Scaffold Team
 * @since 1.4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("会议室资源应用服务")
class OfficeMeetingRoomServiceTest {

    private static final String TENANT_ID = "41";
    private static final String ROOM_CODE = "R-101";

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private CalendarEventRepository calendarEventRepository;

    private OfficeMeetingRoomService service;

    private final LocalDateTime start = LocalDateTime.of(2026, 10, 28, 9, 0);
    private final LocalDateTime end = start.plusHours(1);

    @BeforeEach
    void setUp() {
        service = new OfficeMeetingRoomService(meetingRoomRepository, calendarEventRepository);
    }

    private MeetingRoomEntity room(Long id, String status) {
        MeetingRoomEntity entity = new MeetingRoomEntity();
        entity.setTenantId(TENANT_ID);
        entity.setRoomCode(ROOM_CODE);
        entity.setRoomName("第一会议室");
        entity.setStatus(status);
        entity.setId(id);
        return entity;
    }

    private CalendarEvent event(String organizerId, String roomCode, String status) {
        return CalendarEvent.builder()
                .id(99L)
                .tenantId(41L)
                .meetingRoomId(roomCode)
                .organizerId(organizerId)
                .status(status)
                .startTime(start)
                .endTime(end)
                .build();
    }

    @Nested
    @DisplayName("创建与更新")
    class WriteTest {

        @Test
        @DisplayName("创建时补齐租户与默认启用状态")
        void shouldDefaultTenantAndStatus() {
            MeetingRoomEntity candidate = new MeetingRoomEntity();
            candidate.setRoomCode(ROOM_CODE);
            candidate.setRoomName("第一会议室");
            when(meetingRoomRepository.findByRoomCode(TENANT_ID, ROOM_CODE)).thenReturn(Mono.empty());
            when(meetingRoomRepository.findByRoomName(TENANT_ID, "第一会议室")).thenReturn(Mono.empty());
            when(meetingRoomRepository.insert(any(MeetingRoomEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.createMeetingRoom(TENANT_ID, candidate))
                    .assertNext(saved -> {
                        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
                        assertThat(saved.getStatus()).isEqualTo(MeetingRoomEntity.STATUS_ENABLED);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("编码重复时拒绝创建")
        void shouldRejectDuplicatedCode() {
            MeetingRoomEntity candidate = new MeetingRoomEntity();
            candidate.setRoomCode(ROOM_CODE);
            candidate.setRoomName("第一会议室");
            when(meetingRoomRepository.findByRoomCode(TENANT_ID, ROOM_CODE)).thenReturn(Mono.just(room(1L, "ENABLED")));

            StepVerifier.create(Mono.defer(() -> service.createMeetingRoom(TENANT_ID, candidate)))
                    .expectErrorSatisfies(error -> assertThat(error)
                            .isInstanceOf(BusinessException.class)
                            .hasMessageContaining("编码已存在"))
                    .verify();

            verify(meetingRoomRepository, never()).insert(any(MeetingRoomEntity.class));
        }

        @Test
        @DisplayName("容纳人数非正数时拒绝")
        void shouldRejectNonPositiveCapacity() {
            MeetingRoomEntity candidate = new MeetingRoomEntity();
            candidate.setRoomCode(ROOM_CODE);
            candidate.setRoomName("第一会议室");
            candidate.setCapacity(0);

            StepVerifier.create(Mono.defer(() -> service.createMeetingRoom(TENANT_ID, candidate)))
                    .expectError(BusinessException.class)
                    .verify();

            verify(meetingRoomRepository, never()).insert(any(MeetingRoomEntity.class));
        }

        @Test
        @DisplayName("更新不存在的会议室返回 NOT_FOUND")
        void shouldRejectUpdateWhenMissing() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L)).thenReturn(Mono.empty());

            StepVerifier.create(service.updateMeetingRoom(TENANT_ID, 10L, room(null, null)))
                    .expectErrorSatisfies(error ->
                            assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }

        @Test
        @DisplayName("删除先校验存在性再执行逻辑删除")
        void shouldDeleteExistingRoom() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L)).thenReturn(Mono.just(room(10L, "ENABLED")));
            when(meetingRoomRepository.deleteById(TENANT_ID, 10L)).thenReturn(Mono.just(true));

            StepVerifier.create(service.deleteMeetingRoom(TENANT_ID, 10L))
                    .expectNext(true)
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("可用性")
    class AvailabilityTest {

        @Test
        @DisplayName("时间区间非法时拒绝查询")
        void shouldRejectInvalidTimeRange() {
            when(calendarEventRepository.findByMeetingRoom(41L, ROOM_CODE, end, start))
                    .thenReturn(Mono.just(List.of()));

            StepVerifier.create(service.isAvailable(TENANT_ID, ROOM_CODE, end, start))
                    .expectError(BusinessException.class)
                    .verify();
        }

        @Test
        @DisplayName("无重叠事件时会议室可用")
        void shouldBeAvailableWithoutOverlap() {
            when(calendarEventRepository.findByMeetingRoom(41L, ROOM_CODE, start, end))
                    .thenReturn(Mono.just(List.of()));

            StepVerifier.create(service.isAvailable(TENANT_ID, ROOM_CODE, start, end))
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("存在重叠事件时会议室不可用")
        void shouldBeUnavailableWithOverlap() {
            when(calendarEventRepository.findByMeetingRoom(41L, ROOM_CODE, start, end))
                    .thenReturn(Mono.just(List.of(event("100", ROOM_CODE, "CONFIRMED"))));

            StepVerifier.create(service.isAvailable(TENANT_ID, ROOM_CODE, start, end))
                    .expectNext(false)
                    .verifyComplete();
        }

        @Test
        @DisplayName("可用查询过滤掉被占用的会议室")
        void shouldFilterOccupiedRooms() {
            MeetingRoomEntity occupied = room(10L, "ENABLED");
            MeetingRoomEntity free = room(11L, "ENABLED");
            free.setRoomCode("R-102");
            when(meetingRoomRepository.findAllEnabled(TENANT_ID)).thenReturn(Flux.just(occupied, free));
            when(calendarEventRepository.findByMeetingRoom(41L, ROOM_CODE, start, end))
                    .thenReturn(Mono.just(List.of(event("100", ROOM_CODE, "CONFIRMED"))));
            when(calendarEventRepository.findByMeetingRoom(41L, "R-102", start, end))
                    .thenReturn(Mono.just(List.of()));

            StepVerifier.create(service.findAvailableMeetingRooms(TENANT_ID, start, end))
                    .assertNext(rooms -> assertThat(rooms)
                            .extracting(MeetingRoomEntity::getRoomCode)
                            .containsExactly("R-102"))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("预订")
    class BookingTest {

        @Test
        @DisplayName("停用会议室拒绝预订")
        void shouldRejectDisabledRoom() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_DISABLED)));

            StepVerifier.create(service.bookMeetingRoom(TENANT_ID, 10L, "100", null, "周会", start, end))
                    .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("已停用"))
                    .verify();

            verify(calendarEventRepository, never()).save(any(CalendarEvent.class));
        }

        @Test
        @DisplayName("时间区间冲突拒绝预订")
        void shouldRejectConflict() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findByMeetingRoom(41L, ROOM_CODE, start, end))
                    .thenReturn(Mono.just(List.of(event("100", ROOM_CODE, "CONFIRMED"))));

            StepVerifier.create(service.bookMeetingRoom(TENANT_ID, 10L, "100", null, "周会", start, end))
                    .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("已被占用"))
                    .verify();

            verify(calendarEventRepository, never()).save(any(CalendarEvent.class));
        }

        @Test
        @DisplayName("主题为空时使用会议室名称并写入会议室编码")
        void shouldBookWithRoomNameFallback() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findByMeetingRoom(41L, ROOM_CODE, start, end))
                    .thenReturn(Mono.just(List.of()));
            when(calendarEventRepository.save(any(CalendarEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.bookMeetingRoom(TENANT_ID, 10L, "100", "张三", "  ", start, end))
                    .assertNext(saved -> {
                        assertThat(saved.getTitle()).isEqualTo("第一会议室");
                        assertThat(saved.getMeetingRoomId()).isEqualTo(ROOM_CODE);
                        assertThat(saved.getOrganizerId()).isEqualTo("100");
                        assertThat(saved.getTenantId()).isEqualTo(41L);
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("取消预订")
    class CancelTest {

        @Test
        @DisplayName("非预订人取消按越权拒绝")
        void shouldRejectOtherOperator() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findById(99L)).thenReturn(Mono.just(event("100", ROOM_CODE, "CONFIRMED")));

            StepVerifier.create(service.cancelBooking(TENANT_ID, 10L, 99L, "200"))
                    .expectErrorSatisfies(error ->
                            assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()))
                    .verify();

            verify(calendarEventRepository, never()).update(any(CalendarEvent.class));
        }

        @Test
        @DisplayName("预订记录不存在返回 NOT_FOUND")
        void shouldRejectMissingEvent() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findById(99L)).thenReturn(Mono.empty());

            StepVerifier.create(service.cancelBooking(TENANT_ID, 10L, 99L, "100"))
                    .expectErrorSatisfies(error ->
                            assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }

        @Test
        @DisplayName("预订活跃于其他会议室时返回 NOT_FOUND")
        void shouldRejectEventOfAnotherRoom() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findById(99L)).thenReturn(Mono.just(event("100", "R-999", "CONFIRMED")));

            StepVerifier.create(service.cancelBooking(TENANT_ID, 10L, 99L, "100"))
                    .expectErrorSatisfies(error -> assertThat(error).hasMessageContaining("不属于该会议室"))
                    .verify();
        }

        @Test
        @DisplayName("跨租户事件按不存在处理")
        void shouldHideCrossTenantEvent() {
            CalendarEvent foreign = event("100", ROOM_CODE, "CONFIRMED");
            foreign.setTenantId(99L);
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findById(99L)).thenReturn(Mono.just(foreign));

            StepVerifier.create(service.cancelBooking(TENANT_ID, 10L, 99L, "100"))
                    .expectErrorSatisfies(error ->
                            assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()))
                    .verify();
        }

        @Test
        @DisplayName("重复取消幂等返回已取消事件")
        void shouldBeIdempotent() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findById(99L)).thenReturn(Mono.just(event("100", ROOM_CODE, "CANCELLED")));
            when(calendarEventRepository.update(any(CalendarEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.cancelBooking(TENANT_ID, 10L, 99L, "100"))
                    .assertNext(event -> assertThat(event.isCancelled()).isTrue())
                    .verifyComplete();
        }

        @Test
        @DisplayName("首次取消置为 CANCELLED")
        void shouldCancelConfirmedEvent() {
            when(meetingRoomRepository.findById(TENANT_ID, 10L))
                    .thenReturn(Mono.just(room(10L, MeetingRoomEntity.STATUS_ENABLED)));
            when(calendarEventRepository.findById(99L)).thenReturn(Mono.just(event("100", ROOM_CODE, "CONFIRMED")));
            when(calendarEventRepository.update(any(CalendarEvent.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            StepVerifier.create(service.cancelBooking(TENANT_ID, 10L, 99L, "100"))
                    .assertNext(event -> assertThat(event.getStatus()).isEqualTo("CANCELLED"))
                    .verifyComplete();

            verify(calendarEventRepository, never()).findByMeetingRoom(any(), anyString(), any(), any());
        }
    }
}
