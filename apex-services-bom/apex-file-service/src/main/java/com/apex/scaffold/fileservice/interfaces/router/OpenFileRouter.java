package com.apex.scaffold.fileservice.interfaces.router;

import com.apex.scaffold.fileservice.interfaces.handler.OpenFileHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 开放文件路由（/open-api/file/**）。
 *
 * <p>与改写管理端路径不同，这里使用独立 Handler：开放调用只走网关 scope 断言、能力门禁与
 * 服务侧能力校验，不叠加管理端用户权限切面；文件属主仍由 {@code FileAccessContext} 约束。
 *
 * <p>{@code @RouterOperation} 只声明路由绑定；操作说明、参数、请求体与响应模型统一由
 * {@link OpenFileHandler} 方法上的 SpringDoc 注解决定。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenFileRouter {

    /**
     * 注册开放文件路由。
     *
     * @param handler 开放文件处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/open-api/file/upload",
                method = RequestMethod.POST,
                beanClass = OpenFileHandler.class,
                beanMethod = "upload"),
        @RouterOperation(
                path = "/open-api/file/content",
                method = RequestMethod.GET,
                beanClass = OpenFileHandler.class,
                beanMethod = "content")
    })
    public RouterFunction<ServerResponse> openFileRoutes(OpenFileHandler handler) {
        return RouterFunctions.route()
                .POST(
                        "/open-api/file/upload",
                        RequestPredicates.contentType(MediaType.MULTIPART_FORM_DATA),
                        handler::upload)
                .GET("/open-api/file/content", handler::content)
                .build();
    }
}
