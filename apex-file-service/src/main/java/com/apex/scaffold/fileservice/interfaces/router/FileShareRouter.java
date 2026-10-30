package com.apex.scaffold.fileservice.interfaces.router;

import com.apex.scaffold.fileservice.interfaces.handler.FileShareHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 文件分享路由。
 *
 * <p>{@code /api/v2/file-shares/exchange} 与 {@code /api/v2/file-shares/download} 为匿名兑换入口，
 * 网关与服务安全配置只放行这两个精确路径，不放开整个文件 API。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Configuration
public class FileShareRouter {

    /** 匿名兑换路径。 */
    public static final String EXCHANGE_PATH = "/api/v2/file-shares/exchange";

    /** 匿名下载路径。 */
    public static final String DOWNLOAD_PATH = "/api/v2/file-shares/download";

    /**
     * 注册分享创建、撤销、兑换与下载接口。
     *
     * @param handler 分享处理器
     * @return 分享路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v2/files/{fileId}/shares",
                method = RequestMethod.POST,
                beanClass = FileShareHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/api/v2/files/{fileId}/shares",
                method = RequestMethod.GET,
                beanClass = FileShareHandler.class,
                beanMethod = "list"),
        @RouterOperation(
                path = "/api/v2/file-shares/{shareId}",
                method = RequestMethod.DELETE,
                beanClass = FileShareHandler.class,
                beanMethod = "revoke"),
        @RouterOperation(
                path = EXCHANGE_PATH,
                method = RequestMethod.POST,
                beanClass = FileShareHandler.class,
                beanMethod = "exchange"),
        @RouterOperation(
                path = DOWNLOAD_PATH,
                method = RequestMethod.POST,
                beanClass = FileShareHandler.class,
                beanMethod = "download")
    })
    @Bean
    public RouterFunction<ServerResponse> fileShareRoutes(FileShareHandler handler) {
        return RouterFunctions.route()
                .path("/api/v2/files", builder -> builder.POST("/{fileId}/shares", handler::create)
                        .GET("/{fileId}/shares", handler::list))
                .path("/api/v2/file-shares", builder -> builder.DELETE("/{shareId}", handler::revoke)
                        .POST("/exchange", handler::exchange)
                        .POST("/download", handler::download))
                .build();
    }
}
