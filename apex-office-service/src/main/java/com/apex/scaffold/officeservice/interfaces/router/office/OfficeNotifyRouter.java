package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeNotifyHandler;
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
 * 统一通知管理路由（统一消息门面 / 批次 D1），文档契约由对应处理方法声明。
 *
 * <p>网关仅放行 {@code /api/v2/office/**}，因此通知管理接口统一挂在 v2 版本域下；
 * 按模板投递走内部契约 {@code POST /internal/v1/notify}，不在此注册。
 *
 * @author Apex Scaffold Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Configuration
@RequiredArgsConstructor
public class OfficeNotifyRouter {

    private static final String BASE_PATH = "/api/v2/office/notify";
    private static final String TEMPLATES_PATH = BASE_PATH + "/templates";
    private static final String TEMPLATE_ID_PATH = TEMPLATES_PATH + "/{templateId}";

    private final OfficeNotifyHandler handler;

    /**
     * 注册通知管理接口；模板集合路径先于模板 ID 路径注册，避免路径吞并。
     *
     * @return 通知管理路由
     */
    @RouterOperations({
        @RouterOperation(
                path = TEMPLATES_PATH,
                method = RequestMethod.POST,
                beanClass = OfficeNotifyHandler.class,
                beanMethod = "createTemplate"),
        @RouterOperation(
                path = TEMPLATES_PATH,
                method = RequestMethod.GET,
                beanClass = OfficeNotifyHandler.class,
                beanMethod = "listTemplates"),
        @RouterOperation(
                path = BASE_PATH + "/requests",
                method = RequestMethod.GET,
                beanClass = OfficeNotifyHandler.class,
                beanMethod = "listRecords"),
        @RouterOperation(
                path = TEMPLATE_ID_PATH,
                method = RequestMethod.GET,
                beanClass = OfficeNotifyHandler.class,
                beanMethod = "getTemplate"),
        @RouterOperation(
                path = TEMPLATE_ID_PATH,
                method = RequestMethod.PUT,
                beanClass = OfficeNotifyHandler.class,
                beanMethod = "updateTemplate"),
        @RouterOperation(
                path = TEMPLATE_ID_PATH,
                method = RequestMethod.DELETE,
                beanClass = OfficeNotifyHandler.class,
                beanMethod = "deleteTemplate")
    })
    @Bean
    public RouterFunction<ServerResponse> officeNotifyRoutes() {
        return RouterFunctions.route(
                        POST(TEMPLATES_PATH).and(accept(MediaType.APPLICATION_JSON)), handler::createTemplate)
                .andRoute(GET(TEMPLATES_PATH), handler::listTemplates)
                .andRoute(GET(BASE_PATH + "/requests"), handler::listRecords)
                .andRoute(PUT(TEMPLATE_ID_PATH).and(accept(MediaType.APPLICATION_JSON)), handler::updateTemplate)
                .andRoute(DELETE(TEMPLATE_ID_PATH), handler::deleteTemplate)
                .andRoute(GET(TEMPLATE_ID_PATH), handler::getTemplate);
    }
}
