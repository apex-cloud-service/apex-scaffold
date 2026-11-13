package com.apex.scaffold.fileservice.interfaces.router;

import com.apex.scaffold.fileservice.interfaces.handler.FileInternalHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 文件域内部契约路由（方案 D5 只读统计）。
 *
 * <p>{@code GET /internal/v1/file/stats}：集群内服务直连，不经网关；
 * 安全配置对 {@code /internal/v1/**} 放行，供 system 聚合门面消费。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class FileInternalRouter {

    /** 内部统计端点路径。 */
    public static final String STATS_PATH = "/internal/v1/file/stats";

    /**
     * 注册文件域内部统计路由。
     *
     * @param handler 文件内部 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = STATS_PATH,
            method = RequestMethod.GET,
            beanClass = FileInternalHandler.class,
            beanMethod = "stats")
    public RouterFunction<ServerResponse> fileInternalRoutes(FileInternalHandler handler) {
        return RouterFunctions.route().GET(STATS_PATH, handler::stats).build();
    }
}
