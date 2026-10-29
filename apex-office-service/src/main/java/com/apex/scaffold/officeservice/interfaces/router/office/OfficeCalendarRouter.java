package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeCalendarHandler;
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
 * 办公平台日历事件路由，文档契约由对应处理方法声明。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class OfficeCalendarRouter {
    private static final String BASE_PATH = "/api/office/calendar/events";
    private final OfficeCalendarHandler handler;

    /**
     * 注册日历事件接口，静态 GET 路径优先于事件 ID 路径匹配。
     * @return 日历事件路由
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                beanClass = OfficeCalendarHandler.class,
                beanMethod = "createCalendarEvent"),
        @RouterOperation(
                path = BASE_PATH + "/{eventId}",
                method = RequestMethod.GET,
                beanClass = OfficeCalendarHandler.class,
                beanMethod = "getCalendarEventDetail"),
        @RouterOperation(
                path = BASE_PATH + "/{eventId}",
                method = RequestMethod.PUT,
                beanClass = OfficeCalendarHandler.class,
                beanMethod = "updateCalendarEvent"),
        @RouterOperation(
                path = BASE_PATH + "/{eventId}",
                method = RequestMethod.DELETE,
                beanClass = OfficeCalendarHandler.class,
                beanMethod = "deleteCalendarEvent"),
        @RouterOperation(
                path = BASE_PATH + "/range",
                method = RequestMethod.GET,
                beanClass = OfficeCalendarHandler.class,
                beanMethod = "getCalendarEventsByTimeRange"),
        @RouterOperation(
                path = BASE_PATH + "/my",
                method = RequestMethod.GET,
                beanClass = OfficeCalendarHandler.class,
                beanMethod = "getMyCalendarEvents"),
        @RouterOperation(
                path = BASE_PATH + "/upcoming",
                method = RequestMethod.GET,
                beanClass = OfficeCalendarHandler.class,
                beanMethod = "getUpcomingEvents")
    })
    @Bean
    public RouterFunction<ServerResponse> officeCalendarRoutes() {
        return RouterFunctions.route(
                        POST(BASE_PATH).and(accept(MediaType.APPLICATION_JSON)), handler::createCalendarEvent)
                .andRoute(
                        PUT(BASE_PATH + "/{eventId}").and(accept(MediaType.APPLICATION_JSON)),
                        handler::updateCalendarEvent)
                .andRoute(DELETE(BASE_PATH + "/{eventId}"), handler::deleteCalendarEvent)
                .andRoute(GET(BASE_PATH + "/range"), handler::getCalendarEventsByTimeRange)
                .andRoute(GET(BASE_PATH + "/my"), handler::getMyCalendarEvents)
                .andRoute(GET(BASE_PATH + "/upcoming"), handler::getUpcomingEvents)
                .andRoute(GET(BASE_PATH + "/{eventId}"), handler::getCalendarEventDetail);
    }
}
