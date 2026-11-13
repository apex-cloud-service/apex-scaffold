package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeMeetingRoomHandler;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 会议室资源路由，文档契约由对应处理方法声明。
 *
 * <p>网关仅放行 {@code /api/v2/office/**}，因此会议室接口统一挂在 v2 版本域下。
 *
 * @author Apex Scaffold Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
@RequiredArgsConstructor
public class OfficeMeetingRoomRouter {

    private static final String BASE_PATH = "/api/v2/office/meeting-rooms";

    private final OfficeMeetingRoomHandler handler;

    /**
     * 注册会议室接口，静态 GET 路径（enabled/available）优先于会议室 ID 路径匹配。
     *
     * @return 会议室路由
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "createMeetingRoom"),
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.GET,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "listMeetingRooms"),
        @RouterOperation(
                path = BASE_PATH + "/enabled",
                method = RequestMethod.GET,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "listEnabledMeetingRooms"),
        @RouterOperation(
                path = BASE_PATH + "/available",
                method = RequestMethod.GET,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "findAvailableMeetingRooms"),
        @RouterOperation(
                path = BASE_PATH + "/{roomId}",
                method = RequestMethod.GET,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "getMeetingRoom"),
        @RouterOperation(
                path = BASE_PATH + "/{roomId}",
                method = RequestMethod.PUT,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "updateMeetingRoom"),
        @RouterOperation(
                path = BASE_PATH + "/{roomId}",
                method = RequestMethod.DELETE,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "deleteMeetingRoom"),
        @RouterOperation(
                path = BASE_PATH + "/{roomId}/bookings",
                method = RequestMethod.POST,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "bookMeetingRoom"),
        @RouterOperation(
                path = BASE_PATH + "/{roomId}/bookings/{eventId}",
                method = RequestMethod.DELETE,
                beanClass = OfficeMeetingRoomHandler.class,
                beanMethod = "cancelMeetingRoomBooking")
    })
    @Bean
    public RouterFunction<ServerResponse> officeMeetingRoomRoutes() {
        return RouterFunctions.route(
                        POST(BASE_PATH).and(accept(MediaType.APPLICATION_JSON)), handler::createMeetingRoom)
                .andRoute(GET(BASE_PATH), handler::listMeetingRooms)
                .andRoute(GET(BASE_PATH + "/enabled"), handler::listEnabledMeetingRooms)
                .andRoute(GET(BASE_PATH + "/available"), handler::findAvailableMeetingRooms)
                .andRoute(
                        POST(BASE_PATH + "/{roomId}/bookings").and(accept(MediaType.APPLICATION_JSON)),
                        handler::bookMeetingRoom)
                .andRoute(DELETE(BASE_PATH + "/{roomId}/bookings/{eventId}"), handler::cancelMeetingRoomBooking)
                .andRoute(
                        PUT(BASE_PATH + "/{roomId}").and(accept(MediaType.APPLICATION_JSON)),
                        handler::updateMeetingRoom)
                .andRoute(DELETE(BASE_PATH + "/{roomId}"), handler::deleteMeetingRoom)
                .andRoute(GET(BASE_PATH + "/{roomId}"), handler::getMeetingRoom);
    }
}
