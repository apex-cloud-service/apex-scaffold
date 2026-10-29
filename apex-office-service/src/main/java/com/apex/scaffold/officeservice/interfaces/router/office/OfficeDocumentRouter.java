package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeDocumentHandler;
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
 * 办公平台文档路由，接口参数和响应契约由绑定的处理方法提供。
 *
 * @author Apex Scaffold
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class OfficeDocumentRouter {

    private static final String BASE_PATH = "/api/office/documents";

    private final OfficeDocumentHandler handler;

    /**
     * 注册创建、上传、查询、下载、删除、移动和复制文档的路由。
     *
     * @return 文档接口路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = BASE_PATH,
                method = RequestMethod.POST,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "createDocument"),
        @RouterOperation(
                path = BASE_PATH + "/upload",
                method = RequestMethod.POST,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "uploadFile"),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}",
                method = RequestMethod.GET,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "getDocumentDetail"),
        @RouterOperation(
                path = BASE_PATH + "/folder",
                method = RequestMethod.GET,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "getDocumentsInFolder"),
        @RouterOperation(
                path = BASE_PATH + "/my",
                method = RequestMethod.GET,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "getMyDocuments"),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}/download",
                method = RequestMethod.GET,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "downloadDocument"),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}",
                method = RequestMethod.DELETE,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "deleteDocument"),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}/move",
                method = RequestMethod.PUT,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "moveDocument"),
        @RouterOperation(
                path = BASE_PATH + "/{documentId}/copy",
                method = RequestMethod.POST,
                beanClass = OfficeDocumentHandler.class,
                beanMethod = "copyDocument")
    })
    @Bean
    public RouterFunction<ServerResponse> officeDocumentRoutes() {
        return RouterFunctions.route(POST(BASE_PATH).and(accept(MediaType.APPLICATION_JSON)), handler::createDocument)
                .andRoute(POST(BASE_PATH + "/upload").and(accept(MediaType.APPLICATION_JSON)), handler::uploadFile)
                .andRoute(GET(BASE_PATH + "/{documentId}"), handler::getDocumentDetail)
                .andRoute(GET(BASE_PATH + "/folder"), handler::getDocumentsInFolder)
                .andRoute(GET(BASE_PATH + "/my"), handler::getMyDocuments)
                .andRoute(GET(BASE_PATH + "/{documentId}/download"), handler::downloadDocument)
                .andRoute(DELETE(BASE_PATH + "/{documentId}"), handler::deleteDocument)
                .andRoute(PUT(BASE_PATH + "/{documentId}/move"), handler::moveDocument)
                .andRoute(POST(BASE_PATH + "/{documentId}/copy"), handler::copyDocument);
    }
}
