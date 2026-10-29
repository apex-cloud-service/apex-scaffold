package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeSyncWizardHandler;
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
 * 办公平台组织同步向导路由（v2），接口契约由对应处理方法声明。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class OfficeSyncWizardRouter {

    private static final String BASE_PATH = "/api/v2/office/sync";
    private final OfficeSyncWizardHandler handler;

    /**
     * 注册同步预检、预览、任务管理与重试接口。
     *
     * @return 组织同步向导路由
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH + "/preflight",
                method = RequestMethod.POST,
                beanClass = OfficeSyncWizardHandler.class,
                beanMethod = "preflight"),
        @RouterOperation(
                path = BASE_PATH + "/preview",
                method = RequestMethod.POST,
                beanClass = OfficeSyncWizardHandler.class,
                beanMethod = "preview"),
        @RouterOperation(
                path = BASE_PATH + "/tasks",
                method = RequestMethod.POST,
                beanClass = OfficeSyncWizardHandler.class,
                beanMethod = "createTask"),
        @RouterOperation(
                path = BASE_PATH + "/tasks",
                method = RequestMethod.GET,
                beanClass = OfficeSyncWizardHandler.class,
                beanMethod = "listTasks"),
        @RouterOperation(
                path = BASE_PATH + "/tasks/{taskId}",
                method = RequestMethod.GET,
                beanClass = OfficeSyncWizardHandler.class,
                beanMethod = "getTask"),
        @RouterOperation(
                path = BASE_PATH + "/tasks/{taskId}/retry",
                method = RequestMethod.POST,
                beanClass = OfficeSyncWizardHandler.class,
                beanMethod = "retryTask")
    })
    @Bean
    public RouterFunction<ServerResponse> officeSyncWizardRoutes() {
        return RouterFunctions.route()
                .path(BASE_PATH, builder -> builder.POST(
                                "/preflight", accept(MediaType.APPLICATION_JSON), handler::preflight)
                        .POST("/preview", accept(MediaType.APPLICATION_JSON), handler::preview)
                        .POST("/tasks", accept(MediaType.APPLICATION_JSON), handler::createTask)
                        .GET("/tasks", handler::listTasks)
                        .GET("/tasks/{taskId}", handler::getTask)
                        .POST("/tasks/{taskId}/retry", handler::retryTask))
                .build();
    }
}
