package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.GenGenerateRequest;
import com.apex.scaffold.application.dto.admin.GenPreflightRequest;
import com.apex.scaffold.application.dto.admin.GenPreviewRequest;
import com.apex.scaffold.application.service.GeneratorWizardService;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 代码生成器执行处理器（函数式端点）
 *
 * <p>提供代码生成向导的 RESTful API：模板目录、结构化预检、纯渲染预览、
 * 按预览令牌执行生成，以及按产物 ID 下载。基于预先配置的生成配置和数据源，
 * 生成符合 DDD 分层架构的 Java 代码。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>模板目录：列出当前可见的在线模板及其引擎能力</li>
 *   <li>结构化预检：在写盘之前给出可读的阻塞项与提示</li>
 *   <li>纯渲染预览：只做内存渲染，不写服务器文件，返回输入摘要令牌</li>
 *   <li>执行生成：凭预览令牌写盘并发布产物，记录生成历史</li>
 *   <li>产物下载：浏览器只提交产物 ID，不接受任意路径</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>预览与执行必须使用相同参数，配置或模板版本变化后需重新预览</li>
 *   <li>默认拒绝覆盖既有文件，需显式传入 overwrite=true</li>
 *   <li>各端点分别要求 template:list、preflight、preview、generate、artifact:download 权限</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.2.0
 * @see com.apex.scaffold.application.service.GeneratorWizardService
 */
@Component
@Tag(name = "后台-代码生成器-执行", description = "代码生成向导与执行")
public class CodeGeneratorHandler extends BaseHandler {

    private final GeneratorWizardService generatorWizardService;
    private final UserService userService;

    /**
     * 创建代码生成执行处理器。
     *
     * @param generatorWizardService 代码生成向导服务
     * @param userService 用户服务，用于解析当前操作人
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public CodeGeneratorHandler(
            GeneratorWizardService generatorWizardService,
            UserService userService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.generatorWizardService = generatorWizardService;
        this.userService = userService;
    }

    /**
     * 查询可用模板目录。
     *
     * @param request 服务器请求对象
     * @return 模板目录列表
     */
    @RequirePermission("system:generator:template:list")
    @Operation(
            operationId = "adminListGenTemplates",
            summary = "查询代码生成模板目录",
            description = "返回当前可见的在线模板及其引擎能力，用于向导第一步选择模板")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:template:list 权限")
    })
    public Mono<ServerResponse> templates(ServerRequest request) {
        return generatorWizardService.templates().flatMap(this::buildSuccess);
    }

    /**
     * 结构化预检。
     *
     * @param request 服务器请求对象，请求体为 {@link GenPreflightRequest}
     * @return 预检结果
     */
    @RequirePermission("system:generator:preflight")
    @Operation(
            operationId = "adminGenPreflight",
            summary = "代码生成结构化预检",
            description = "校验配置、数据源、模板与目标表，返回阻塞项与提示，不写任何文件")
    @RequestBody(description = "预检参数", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "预检完成"),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:preflight 权限")
    })
    public Mono<ServerResponse> preflight(ServerRequest request) {
        return parseBody(request, GenPreflightRequest.class)
                .flatMap(generatorWizardService::preflight)
                .flatMap(this::buildSuccess);
    }

    /**
     * 纯渲染预览。
     *
     * @param request 服务器请求对象，请求体为 {@link GenPreviewRequest}
     * @return 预览结果，含输入摘要令牌
     */
    @RequirePermission("system:generator:preview")
    @Operation(
            operationId = "adminGenPreview",
            summary = "代码生成预览",
            description = "在内存中渲染目标文件并返回摘要令牌，不写服务器文件；执行时须携带同一令牌")
    @RequestBody(description = "预览参数", required = true, content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "预览成功"),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:preview 权限"),
        @ApiResponse(responseCode = "404", description = "生成配置不存在")
    })
    public Mono<ServerResponse> preview(ServerRequest request) {
        return parseBody(request, GenPreviewRequest.class)
                .flatMap(generatorWizardService::preview)
                .flatMap(this::buildSuccess);
    }

    /**
     * 执行代码生成。
     *
     * <p>路径中的配置 ID 为准，请求体中的 configId 会被覆盖。执行前校验预览令牌，
     * 配置或模板版本变化后令牌失配，须重新预览。</p>
     *
     * @param request 服务器请求对象，包含生成配置 ID 路径参数与 {@link GenGenerateRequest} 请求体
     * @return 包含代码生成结果的响应对象
     */
    @RequirePermission("system:generator:generate")
    @Operation(
            operationId = "adminGenerateCode",
            summary = "执行代码生成",
            description = "凭预览令牌执行代码生成，产物写入独立任务目录并记录生成历史；默认拒绝覆盖既有文件")
    @Parameter(
            name = "id",
            description = "生成配置ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "代码生成执行参数（预览令牌、字段映射覆盖、是否覆盖）",
            required = true,
            content = @Content(mediaType = "application/json"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "代码生成成功，返回产物列表与部分失败说明"),
        @ApiResponse(responseCode = "400", description = "参数错误或预览令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:generate 权限"),
        @ApiResponse(responseCode = "404", description = "生成配置不存在"),
        @ApiResponse(responseCode = "500", description = "代码生成失败，返回错误信息")
    })
    @OperationLog(
            module = "代码生成器-执行",
            type = OperationLog.OperationType.OTHER,
            description = "执行代码生成",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> generate(ServerRequest request) {
        long configId = requirePositiveId(request);
        return request.bodyToMono(GenGenerateRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .doOnNext(body -> body.setConfigId(configId))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> currentUserId(request, userService)
                        .flatMap(operatorId -> generatorWizardService.execute(body, operatorId)))
                .flatMap(this::buildSuccess);
    }

    /**
     * 按产物 ID 下载文件。
     *
     * @param request 服务器请求对象，包含产物 ID 路径参数
     * @return 文件流响应
     */
    @RequirePermission("system:generator:artifact:download")
    @Operation(
            operationId = "adminDownloadGenArtifact",
            summary = "下载生成产物",
            description = "按产物 ID 下载已发布的生成文件，只接受产物 ID，不接受任意路径")
    @Parameter(
            name = "id",
            description = "产物ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "下载成功，返回文件流"),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足或产物属于其他租户"),
        @ApiResponse(responseCode = "404", description = "产物不存在或未成功发布")
    })
    public Mono<ServerResponse> download(ServerRequest request) {
        long artifactId = requirePositiveId(request);
        return generatorWizardService.download(artifactId).flatMap(file -> ServerResponse.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        "Content-Disposition",
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .bodyValue(file.content()));
    }
}
