package com.apex.scaffold.interfaces.router;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import com.apex.scaffold.interfaces.handler.CodeGeneratorHandler;
import com.apex.scaffold.interfaces.handler.GenConfigHandler;
import com.apex.scaffold.interfaces.handler.GenDatasourceHandler;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 代码生成器管理端路由配置。
 *
 * <p>提供代码生成器的数据源管理、生成配置管理和代码生成执行相关的路由注册。
 * 所有路由基于函数式端点模式实现，统一使用 {@code /admin-api/v1/generator} 前缀。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>数据源管理</b> - {@link GenDatasourceHandler}</li>
 *   <ul>
 *     <li>GET /admin-api/v1/generator/datasources - 查询数据源列表（分页）</li>
 *     <li>POST /admin-api/v1/generator/datasources - 新增数据源</li>
 *     <li>PUT /admin-api/v1/generator/datasources/{id} - 更新数据源</li>
 *     <li>DELETE /admin-api/v1/generator/datasources/{id} - 删除数据源</li>
 *     <li>POST /admin-api/v1/generator/datasources/{id}/test - 测试数据源连接</li>
 *   </ul>
 *   <li><b>生成配置管理</b> - {@link GenConfigHandler}</li>
 *   <ul>
 *     <li>GET /admin-api/v1/generator/configs - 查询生成配置列表</li>
 *     <li>POST /admin-api/v1/generator/configs - 新增生成配置</li>
 *     <li>PUT /admin-api/v1/generator/configs/{id} - 更新生成配置</li>
 *     <li>DELETE /admin-api/v1/generator/configs/{id} - 删除生成配置</li>
 *     <li>GET /admin-api/v1/generator/datasources/{id}/tables - 查询数据库表结构</li>
 *     <li>GET /admin-api/v1/generator/datasources/{id}/tables/{tableName}/columns - 查询表字段信息</li>
 *     <li>GET /admin-api/v1/generator/configs/{id}/history - 查询生成历史</li>
 *   </ul>
 *   <li><b>代码生成执行</b> - {@link CodeGeneratorHandler}</li>
 *   <ul>
 *     <li>GET /admin-api/v1/generator/templates - 查询模板目录</li>
 *     <li>POST /admin-api/v1/generator/preflight - 结构化预检</li>
 *     <li>POST /admin-api/v1/generator/preview - 纯渲染预览</li>
 *     <li>POST /admin-api/v1/generator/configs/{id}/generate - 执行代码生成</li>
 *     <li>GET /admin-api/v1/generator/artifacts/{id}/download - 按产物 ID 下载</li>
 *     <li>POST /admin-api/v1/generator/tasks - 提交异步生成任务</li>
 *     <li>GET /admin-api/v1/generator/tasks/{id} - 查询任务进度</li>
 *     <li>POST /admin-api/v1/generator/tasks/{id}/cancel - 取消任务</li>
 *   </ul>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class GeneratorAdminRouter {

    /**
     * 配置代码生成器管理端路由，包括数据源、配置和执行。
     *
     * @param datasourceHandler 数据源处理器
     * @param configHandler 生成配置处理器
     * @param generatorHandler 代码生成执行处理器
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/generator/datasources",
                beanClass = GenDatasourceHandler.class,
                beanMethod = "list"),
        @RouterOperation(
                path = "/admin-api/v1/generator/datasources",
                beanClass = GenDatasourceHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/admin-api/v1/generator/datasources/{id}",
                beanClass = GenDatasourceHandler.class,
                beanMethod = "update"),
        @RouterOperation(
                path = "/admin-api/v1/generator/datasources/{id}",
                beanClass = GenDatasourceHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/admin-api/v1/generator/datasources/{id}/test",
                beanClass = GenDatasourceHandler.class,
                beanMethod = "testConnection"),
        @RouterOperation(
                path = "/admin-api/v1/generator/configs",
                beanClass = GenConfigHandler.class,
                beanMethod = "list"),
        @RouterOperation(
                path = "/admin-api/v1/generator/configs",
                beanClass = GenConfigHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/admin-api/v1/generator/configs/{id}",
                beanClass = GenConfigHandler.class,
                beanMethod = "update"),
        @RouterOperation(
                path = "/admin-api/v1/generator/configs/{id}",
                beanClass = GenConfigHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/admin-api/v1/generator/datasources/{id}/tables",
                beanClass = GenConfigHandler.class,
                beanMethod = "getTables"),
        @RouterOperation(
                path = "/admin-api/v1/generator/datasources/{id}/tables/{tableName}/columns",
                beanClass = GenConfigHandler.class,
                beanMethod = "getColumns"),
        @RouterOperation(
                path = "/admin-api/v1/generator/configs/{id}/history",
                beanClass = GenConfigHandler.class,
                beanMethod = "listHistory"),
        @RouterOperation(
                path = "/admin-api/v1/generator/configs/{id}/generate",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "generate"),
        @RouterOperation(
                path = "/admin-api/v1/generator/templates",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "templates"),
        @RouterOperation(
                path = "/admin-api/v1/generator/preflight",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "preflight"),
        @RouterOperation(
                path = "/admin-api/v1/generator/preview",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "preview"),
        @RouterOperation(
                path = "/admin-api/v1/generator/artifacts/{id}/download",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "download"),
        @RouterOperation(
                path = "/admin-api/v1/generator/tasks",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "submit"),
        @RouterOperation(
                path = "/admin-api/v1/generator/tasks/{id}",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "taskProgress"),
        @RouterOperation(
                path = "/admin-api/v1/generator/tasks/{id}/cancel",
                beanClass = CodeGeneratorHandler.class,
                beanMethod = "cancelTask")
    })
    @Bean
    @SuppressWarnings("PMD.MethodTooLongRule")
    public RouterFunction<ServerResponse> generatorAdminRoutes(
            GenDatasourceHandler datasourceHandler,
            GenConfigHandler configHandler,
            CodeGeneratorHandler generatorHandler) {
        return SpringdocRouteBuilder.route()
                .GET("/admin-api/v1/generator/datasources", datasourceHandler::list, ops -> ops.operationId(
                                "listGenDatasources")
                        .summary("查询数据源列表")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .example("20"))
                        .parameter(parameterBuilder()
                                .name("name")
                                .description("数据源名称")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/generator/datasources", datasourceHandler::create, ops -> ops.operationId(
                                "createGenDatasource")
                        .summary("新增数据源")
                        .tag("代码生成器管理")
                        .requestBody(requestBodyBuilder()
                                .description("数据源配置信息")
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("创建成功")))
                .PUT("/admin-api/v1/generator/datasources/{id}", datasourceHandler::update, ops -> ops.operationId(
                                "updateGenDatasource")
                        .summary("更新数据源")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("数据源ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .requestBody(requestBodyBuilder()
                                .description("数据源配置信息")
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("更新成功")))
                .DELETE("/admin-api/v1/generator/datasources/{id}", datasourceHandler::delete, ops -> ops.operationId(
                                "deleteGenDatasource")
                        .summary("删除数据源")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("数据源ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("删除成功")))
                .POST(
                        "/admin-api/v1/generator/datasources/{id}/test",
                        datasourceHandler::testConnection,
                        ops -> ops.operationId("testGenDatasourceConnection")
                                .summary("测试数据源连接")
                                .tag("代码生成器管理")
                                .parameter(parameterBuilder()
                                        .name("id")
                                        .description("数据源ID")
                                        .in(ParameterIn.PATH)
                                        .required(true))
                                .response(responseBuilder().responseCode("200").description("测试完成")))
                .GET("/admin-api/v1/generator/configs", configHandler::list, ops -> ops.operationId("listGenConfigs")
                        .summary("查询生成配置列表")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .example("20"))
                        .parameter(parameterBuilder()
                                .name("configName")
                                .description("配置名称")
                                .in(ParameterIn.QUERY))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/generator/configs", configHandler::create, ops -> ops.operationId(
                                "createGenConfig")
                        .summary("新增生成配置")
                        .tag("代码生成器管理")
                        .requestBody(requestBodyBuilder()
                                .description("生成配置信息")
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("创建成功")))
                .PUT("/admin-api/v1/generator/configs/{id}", configHandler::update, ops -> ops.operationId(
                                "updateGenConfig")
                        .summary("更新生成配置")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("生成配置ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .requestBody(requestBodyBuilder()
                                .description("生成配置信息")
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("更新成功")))
                .DELETE("/admin-api/v1/generator/configs/{id}", configHandler::delete, ops -> ops.operationId(
                                "deleteGenConfig")
                        .summary("删除生成配置")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("生成配置ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("删除成功")))
                .GET(
                        "/admin-api/v1/generator/datasources/{id}/tables",
                        configHandler::getTables,
                        ops -> ops.operationId("listGenTables")
                                .summary("查询数据库表结构")
                                .tag("代码生成器管理")
                                .parameter(parameterBuilder()
                                        .name("id")
                                        .description("数据源ID")
                                        .in(ParameterIn.PATH)
                                        .required(true))
                                .response(responseBuilder().responseCode("200").description("查询成功")))
                .GET(
                        "/admin-api/v1/generator/datasources/{id}/tables/{tableName}/columns",
                        configHandler::getColumns,
                        ops -> ops.operationId("listGenColumns")
                                .summary("查询表字段信息")
                                .tag("代码生成器管理")
                                .parameter(parameterBuilder()
                                        .name("id")
                                        .description("数据源ID")
                                        .in(ParameterIn.PATH)
                                        .required(true))
                                .parameter(parameterBuilder()
                                        .name("tableName")
                                        .description("表名")
                                        .in(ParameterIn.PATH)
                                        .required(true))
                                .response(responseBuilder().responseCode("200").description("查询成功")))
                .GET("/admin-api/v1/generator/configs/{id}/history", configHandler::listHistory, ops -> ops.operationId(
                                "listGenHistory")
                        .summary("查询生成历史")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("生成配置ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .parameter(parameterBuilder()
                                .name("page")
                                .description("页码")
                                .in(ParameterIn.QUERY)
                                .example("1"))
                        .parameter(parameterBuilder()
                                .name("size")
                                .description("每页数量")
                                .in(ParameterIn.QUERY)
                                .example("20"))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST(
                        "/admin-api/v1/generator/configs/{id}/generate",
                        generatorHandler::generate,
                        ops -> ops.operationId("generateCode")
                                .summary("执行代码生成")
                                .tag("代码生成器管理")
                                .parameter(parameterBuilder()
                                        .name("id")
                                        .description("生成配置ID")
                                        .in(ParameterIn.PATH)
                                        .required(true))
                                .requestBody(requestBodyBuilder()
                                        .description("代码生成参数：预览令牌、字段映射覆盖、是否覆盖（可选）")
                                        .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                                .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                                .response(responseBuilder().responseCode("200").description("代码生成成功")))
                .GET("/admin-api/v1/generator/templates", generatorHandler::templates, ops -> ops.operationId(
                                "listGenTemplates")
                        .summary("查询代码生成模板目录")
                        .tag("代码生成器管理")
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/generator/preflight", generatorHandler::preflight, ops -> ops.operationId(
                                "genPreflight")
                        .summary("代码生成结构化预检")
                        .tag("代码生成器管理")
                        .requestBody(requestBodyBuilder()
                                .description("预检参数：生成配置 ID、字段映射覆盖、是否覆盖")
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("预检完成")))
                .POST("/admin-api/v1/generator/preview", generatorHandler::preview, ops -> ops.operationId("genPreview")
                        .summary("代码生成预览")
                        .tag("代码生成器管理")
                        .requestBody(requestBodyBuilder()
                                .description("预览参数：生成配置 ID、字段映射覆盖")
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("预览成功")))
                .GET(
                        "/admin-api/v1/generator/artifacts/{id}/download",
                        generatorHandler::download,
                        ops -> ops.operationId("downloadGenArtifact")
                                .summary("下载生成产物")
                                .tag("代码生成器管理")
                                .parameter(parameterBuilder()
                                        .name("id")
                                        .description("产物ID")
                                        .in(ParameterIn.PATH)
                                        .required(true))
                                .response(responseBuilder().responseCode("200").description("下载成功")))
                .POST("/admin-api/v1/generator/tasks", generatorHandler::submit, ops -> ops.operationId("submitGenTask")
                        .summary("提交代码生成任务")
                        .tag("代码生成器管理")
                        .requestBody(requestBodyBuilder()
                                .description("异步生成参数：生成配置 ID、表清单、模板与预览令牌")
                                .required(true)
                                .content(org.springdoc.core.fn.builders.content.Builder.contentBuilder()
                                        .mediaType(MediaType.APPLICATION_JSON_VALUE)))
                        .response(responseBuilder().responseCode("200").description("任务已受理")))
                .GET("/admin-api/v1/generator/tasks/{id}", generatorHandler::taskProgress, ops -> ops.operationId(
                                "getGenTaskProgress")
                        .summary("查询代码生成任务进度")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("任务ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("查询成功")))
                .POST("/admin-api/v1/generator/tasks/{id}/cancel", generatorHandler::cancelTask, ops -> ops.operationId(
                                "cancelGenTask")
                        .summary("取消代码生成任务")
                        .tag("代码生成器管理")
                        .parameter(parameterBuilder()
                                .name("id")
                                .description("任务ID")
                                .in(ParameterIn.PATH)
                                .required(true))
                        .response(responseBuilder().responseCode("200").description("取消请求已受理")))
                .build();
    }
}
