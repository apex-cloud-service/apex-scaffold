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
 * 办公平台组织同步向导的函数式路由（v2），接口契约由对应处理方法声明。
 *
 * <p>路径前缀固定为 {@value #BASE_PATH}，对应网关 {@code office-domain} 的
 * {@code /api/v2/office/**} 规则。接口按向导阶段划分：
 *
 * <ul>
 *   <li>{@code /preflight}、{@code /preview}：只读校验与差异预览，不落地同步任务；
 *   <li>{@code /tasks}：创建并查询同步任务，创建为写操作；
 *   <li>{@code /tasks/{taskId}/retry}：对失败任务发起重试。
 * </ul>
 *
 * <p>预检与预览要求 {@code Accept: application/json}；查询接口不加谓词限制。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class OfficeSyncWizardRouter {

    /** 组织同步向导的公共路径前缀，Bean 方法内路由与 OpenAPI 元数据共用，避免两处不一致。 */
    private static final String BASE_PATH = "/api/v2/office/sync";

    private final OfficeSyncWizardHandler handler;

    /**
     * 注册同步预检、预览、任务管理与重试接口。
     *
     * <p>处理器通过构造器注入而非 Bean 方法参数获取，故无 {@code handler} 参数；
     * 重试以任务标识定位，是否允许重复提交由 Handler 按任务状态判定，路由层不做幂等控制。
     *
     * <p>预检与预览为 JSON 请求体，任务查询与详情为 GET，任务标识由路径参数携带。
     *
     * @return 组织同步向导路由函数，挂载于 {@value #BASE_PATH}
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
