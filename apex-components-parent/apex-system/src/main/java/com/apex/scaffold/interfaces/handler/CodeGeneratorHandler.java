package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.GenArtifactDTO;
import com.apex.scaffold.application.dto.admin.GenExecutionResponse;
import com.apex.scaffold.application.dto.admin.GenGenerateRequest;
import com.apex.scaffold.application.dto.admin.GenPreflightRequest;
import com.apex.scaffold.application.dto.admin.GenPreflightResponse;
import com.apex.scaffold.application.dto.admin.GenPreviewRequest;
import com.apex.scaffold.application.dto.admin.GenPreviewResponse;
import com.apex.scaffold.application.dto.admin.GenTaskDTO;
import com.apex.scaffold.application.dto.admin.GenTaskSubmitResponse;
import com.apex.scaffold.application.dto.admin.GenTemplateCatalogDTO;
import com.apex.scaffold.application.service.GeneratorTaskService;
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
 * <h2>职责范围</h2>
 * <ul>
 *   <li>模板目录：列出当前可见的在线模板及其引擎能力</li>
 *   <li>结构化预检：在写盘之前给出可读的阻塞项与提示</li>
 *   <li>纯渲染预览：只做内存渲染，不写服务器文件，返回输入摘要令牌</li>
 *   <li>执行生成：凭预览令牌写盘并发布产物，记录生成历史</li>
 *   <li>产物下载：浏览器只提交产物 ID，不接受任意路径</li>
 *   <li>异步任务：提交生成任务、轮询进度、取消任务，逐项推进进度</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>预览与执行必须使用相同参数，配置或模板版本变化后需重新预览</li>
 *   <li>默认拒绝覆盖既有文件，需显式传入 overwrite=true</li>
 *   <li>各端点分别要求 template:list、preflight、preview、generate、artifact:download 权限</li>
 *   <li>异步任务端点要求 task:create、task:read、task:cancel 权限</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.2.0
 * @see com.apex.scaffold.application.service.GeneratorWizardService
 */
@Component
@Tag(name = "后台-代码生成器-执行", description = "代码生成向导与执行")
public class CodeGeneratorHandler extends BaseHandler {

    private final GeneratorWizardService generatorWizardService;
    private final GeneratorTaskService generatorTaskService;
    private final UserService userService;

    /**
     * 创建代码生成执行处理器。
     *
     * @param generatorWizardService 代码生成向导服务
     * @param generatorTaskService 异步生成任务服务
     * @param userService 用户服务，用于解析当前操作人
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public CodeGeneratorHandler(
            GeneratorWizardService generatorWizardService,
            GeneratorTaskService generatorTaskService,
            UserService userService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.generatorWizardService = generatorWizardService;
        this.generatorTaskService = generatorTaskService;
        this.userService = userService;
    }

    /**
     * 查询可用模板目录
     *
     * <p>委托 {@code generatorWizardService} 返回当前可见的在线模板及其引擎能力，供代码生成向导
     * 第一步选择模板，结果统一由 {@code buildSuccess} 包装为带 traceId 的响应。
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
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为模板目录（含引擎能力）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenTemplateCatalogDTO.class))),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:template:list 权限")
    })
    public Mono<ServerResponse> templates(ServerRequest request) {
        return generatorWizardService.templates().flatMap(this::buildSuccess);
    }

    /**
     * 结构化预检
     *
     * <p>解析并校验 {@link GenPreflightRequest}，委托向导服务检查配置、数据源、模板与目标表，
     * 返回阻塞项与提示清单；该过程只读，不写任何文件。
     *
     * @param request 服务器请求对象，请求体为 {@link GenPreflightRequest}
     * @return 预检结果
     */
    @RequirePermission("system:generator:preflight")
    @Operation(
            operationId = "adminGenPreflight",
            summary = "代码生成结构化预检",
            description = "校验配置、数据源、模板与目标表，返回阻塞项与提示，不写任何文件")
    @RequestBody(
            description = "预检参数（配置、数据源、模板与目标表）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GenPreflightRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "预检完成，data 为阻塞项与提示清单",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenPreflightResponse.class))),
        @ApiResponse(responseCode = "400", description = "参数错误"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:preflight 权限")
    })
    public Mono<ServerResponse> preflight(ServerRequest request) {
        return parseBody(request, GenPreflightRequest.class)
                .flatMap(generatorWizardService::preflight)
                .flatMap(this::buildSuccess);
    }

    /**
     * 纯渲染预览
     *
     * <p>解析 {@link GenPreviewRequest} 后在内存中渲染目标文件并返回摘要令牌，不写服务器文件；
     * 后续执行须携带同一令牌，配置或模板版本变化会导致令牌失配。
     *
     * @param request 服务器请求对象，请求体为 {@link GenPreviewRequest}
     * @return 预览结果，含输入摘要令牌
     */
    @RequirePermission("system:generator:preview")
    @Operation(
            operationId = "adminGenPreview",
            summary = "代码生成预览",
            description = "在内存中渲染目标文件并返回摘要令牌，不写服务器文件；执行时须携带同一令牌")
    @RequestBody(
            description = "预览参数（配置、模板与字段映射覆盖）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GenPreviewRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "预览成功，data 为渲染结果与预览令牌",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenPreviewResponse.class))),
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
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GenGenerateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "代码生成成功，data 为产物列表与部分失败说明",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenExecutionResponse.class))),
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
     * 按产物 ID 下载文件
     *
     * <p>解析路径正整数产物 ID 后调用 {@code generatorWizardService.download} 获取文件流，以
     * {@code Content-Disposition: attachment} 输出原始文件；仅接受产物 ID，不接受任意路径。
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
        @ApiResponse(
                responseCode = "200",
                description = "下载成功，返回二进制文件流（Content-Disposition 附件，非 JSON）",
                content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)),
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

    /**
     * 提交异步生成任务。
     *
     * <p>提交后立即返回任务 ID，渲染、写盘与落库在后台按「表 × 模板」逐项执行，
     * 调用方通过进度接口轮询状态。</p>
     *
     * @param request 服务器请求对象，请求体为 {@link GenGenerateRequest}
     * @return 任务 ID 与初始状态
     */
    @RequirePermission("system:generator:task:create")
    @Operation(
            operationId = "adminSubmitGenTask",
            summary = "提交代码生成任务",
            description = "提交异步代码生成任务并立即返回任务 ID，后台按表与模板逐项生成并推进进度")
    @RequestBody(
            description = "异步生成参数（与同步执行一致，含配置 ID、表清单、模板与预览令牌）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GenGenerateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "任务已受理，data 为任务 ID 与初始状态",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenTaskSubmitResponse.class))),
        @ApiResponse(responseCode = "400", description = "参数错误或预览令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:task:create 权限")
    })
    @OperationLog(
            module = "代码生成器-执行",
            type = OperationLog.OperationType.OTHER,
            description = "提交代码生成任务",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> submit(ServerRequest request) {
        return parseBody(request, GenGenerateRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(body -> currentUserId(request, userService)
                        .flatMap(operatorId -> generatorTaskService.submit(body, operatorId)))
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询异步生成任务进度
     *
     * <p>解析路径中的正整数任务 ID，委托任务服务读取当前状态、总项数、已处理数与失败数；
     * 任务不存在或属于其他租户时统一返回 404。
     *
     * @param request 服务器请求对象，包含任务 ID 路径参数
     * @return 任务进度详情
     */
    @RequirePermission("system:generator:task:read")
    @Operation(
            operationId = "adminGetGenTaskProgress",
            summary = "查询代码生成任务进度",
            description = "按任务 ID 查询异步生成任务的当前状态、总项数、已处理数与失败数")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为任务进度（状态、总项数、已处理数、失败数）",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = GenTaskDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:task:read 权限"),
        @ApiResponse(responseCode = "404", description = "任务不存在或属于其他租户")
    })
    public Mono<ServerResponse> taskProgress(ServerRequest request) {
        long taskId = requirePositiveId(request);
        return generatorTaskService.getProgress(taskId).flatMap(this::buildSuccess);
    }

    /**
     * 查询异步生成任务的产物清单。
     *
     * <p>任务完成后的下载闭环依赖本接口：先按任务列出产物，再按产物 ID 调用下载端点。
     * 任务不存在或属于其他租户时统一返回 404，不泄露任务存在性。
     *
     * @param request 服务器请求对象，包含任务 ID 路径参数
     * @return 该任务已登记的产物列表
     */
    @RequirePermission("system:generator:task:read")
    @Operation(
            operationId = "adminListGenTaskArtifacts",
            summary = "查询代码生成任务产物清单",
            description = "按任务 ID 列出已登记的产物，供前端按产物 ID 发起下载；跨租户任务与不存在任务返回 404")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为产物清单",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = GenArtifactDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:task:read 权限"),
        @ApiResponse(responseCode = "404", description = "任务不存在或属于其他租户")
    })
    public Mono<ServerResponse> taskArtifacts(ServerRequest request) {
        long taskId = requirePositiveId(request);
        return generatorTaskService.getTaskArtifacts(taskId).flatMap(this::buildSuccess);
    }

    /**
     * 取消异步生成任务。
     *
     * <p>待执行任务直接置为已取消；执行中的任务在下一次推进进度时感知取消并停止。</p>
     *
     * @param request 服务器请求对象，包含任务 ID 路径参数
     * @return 是否成功提交取消
     */
    @RequirePermission("system:generator:task:cancel")
    @Operation(
            operationId = "adminCancelGenTask",
            summary = "取消代码生成任务",
            description = "取消待执行或执行中的异步生成任务，执行中任务在下一项推进时停止")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "取消请求已受理，data 为是否成功提交取消（布尔值）",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(type = "boolean", example = "true"))),
        @ApiResponse(responseCode = "400", description = "参数错误，ID必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 system:generator:task:cancel 权限"),
        @ApiResponse(responseCode = "404", description = "任务不存在或属于其他租户")
    })
    @OperationLog(
            module = "代码生成器-执行",
            type = OperationLog.OperationType.OTHER,
            description = "取消代码生成任务",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> cancelTask(ServerRequest request) {
        long taskId = requirePositiveId(request);
        return generatorTaskService.cancel(taskId).flatMap(this::buildSuccess);
    }
}
