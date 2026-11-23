package com.apex.scaffold.knowledgeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentCleanupDTO;
import com.apex.scaffold.knowledge.application.dto.KnowledgeManualDocumentRequest;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentAppService;
import com.apex.scaffold.knowledge.application.service.KnowledgeDocumentCleanupService;
import com.apex.scaffold.knowledge.config.KnowledgeProperties;
import com.apex.scaffold.knowledgeservice.interfaces.util.KnowledgeRequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 知识文档管理处理器，适配上传、在线录入、查询、删除及重新摄取请求。
 *
 * <p>上传读取 multipart 中首个 {@code file} 文件字段，以
 * {@code apex.knowledge.ingest.max-upload-size-mb} 配置限制聚合字节数，不是流式直传对象存储。
 * 上传、在线录入与重建响应仅包含登记或状态更新后的 DTO，不等待摄取及索引任务执行完成。
 *
 * <p>成功响应为 HTTP 200 的统一结果包装；响应式链中的业务异常由
 * {@link KnowledgeRequestHandlerUtil} 映射为 HTTP 400 并保留业务错误码，其他异常映射为 HTTP 500。
 * 路径 ID 在构建链之前直接解析，解析异常不由方法末尾的错误恢复分支处理。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "知识库-文档管理", description = "知识文档上传、在线录入、查询、删除与重新摄取；权限码 kb:doc:*")
public class KnowledgeDocumentHandler {

    /** 文档分页的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbDocumentPageResult")
    public static class KnowledgeDocumentPageResult
            extends Result<java.util.List<com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDTO>> {}

    /** 单个文档案的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbDocumentResult")
    public static class KnowledgeDocumentResult
            extends Result<com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDTO> {}

    /** 文档详情的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbDocumentDetailResult")
    public static class KnowledgeDocumentDetailResult
            extends Result<com.apex.scaffold.knowledge.application.dto.KnowledgeDocumentDetailDTO> {}

    /** 文档写操作的布尔结果，仅用于接口文档。 */
    @Schema(name = "KbDocumentBooleanResult")
    public static class KnowledgeDocumentBooleanResult extends Result<Boolean> {}

    /** 清理记录分页的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbDocumentCleanupPageResult")
    public static class KnowledgeDocumentCleanupPageResult
            extends Result<java.util.List<KnowledgeDocumentCleanupDTO>> {}

    /** 清理记录详情的统一响应结构，仅用于接口文档。 */
    @Schema(name = "KbDocumentCleanupResult")
    public static class KnowledgeDocumentCleanupResult extends Result<KnowledgeDocumentCleanupDTO> {}

    private final KnowledgeDocumentAppService documentAppService;

    private final KnowledgeRequestHandlerUtil requestUtil;

    private final KnowledgeProperties properties;

    private final KnowledgeDocumentCleanupService cleanupService;

    /**
     * 分页查看持久清理进度，page 从 1 开始，size 为 1 至 100。
     *
     * <p>仅返回管理端可展示字段，不暴露租户、对象定位、认领 owner 或密钥。
     *
     * @param request 含可选 page/size 查询参数的请求
     * @return 包装清理记录分页的成功响应；非法分页或链中错误转换为统一错误响应
     */
    @RequirePermission("kb:doc:read")
    @Operation(
            operationId = "kbPageDocumentCleanups",
            summary = "分页查询文档清理任务",
            description =
                    "查看持久清理进度，page 从 1 开始，size 为 1 至 100；仅返回管理端可展示字段，" + "不暴露租户、对象定位、认领 owner 或密钥。需 kb:doc:read 权限",
            parameters = {
                @Parameter(
                        name = "page",
                        in = ParameterIn.QUERY,
                        description = "页码，从 1 开始，缺省 1",
                        schema = @Schema(type = "integer", defaultValue = "1", minimum = "1")),
                @Parameter(
                        name = "size",
                        in = ParameterIn.QUERY,
                        description = "每页数量，1 至 100，缺省 20",
                        schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
            })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "分页查询成功，data 为清理记录分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentCleanupPageResult.class))),
        @ApiResponse(responseCode = "400", description = "分页参数无效"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:read 权限")
    })
    public Mono<ServerResponse> cleanupPage(ServerRequest request) {
        return Mono.defer(() -> cleanupService.page(
                        positiveInt(request.queryParam("page").orElse("1"), 1, Integer.MAX_VALUE),
                        positiveInt(request.queryParam("size").orElse("20"), 1, 100)))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 查询当前租户指定文档的清理凭证，不以主档可见性过滤已删除文档。
     *
     * @param request 含 docId 路径变量的请求
     * @return 包装清理进度的成功响应；凭证不存在或链中错误转换为统一错误响应
     */
    @RequirePermission("kb:doc:read")
    @Operation(
            operationId = "kbGetDocumentCleanup",
            summary = "查询文档清理详情",
            description = "查询当前租户指定文档的清理凭证，不以主档可见性过滤已删除文档。需 kb:doc:read 权限",
            responses =
                    @ApiResponse(
                            responseCode = "200",
                            description =
                                    "Result data 包含 documentId/title/status/errorMessage/attempts/updatedAt/nextRetryAt；"
                                            + "status 为 PENDING/PROCESSING/COMPLETED/FAILED",
                            content =
                                    @Content(schema = @Schema(implementation = KnowledgeDocumentCleanupResult.class))),
            parameters =
                    @Parameter(
                            name = "docId",
                            in = ParameterIn.PATH,
                            required = true,
                            description = "文档 ID",
                            schema = @Schema(type = "integer", format = "int64", minimum = "1")))
    @ApiResponses({
        @ApiResponse(responseCode = "400", description = "文档 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "清理凭证不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:read 权限")
    })
    public Mono<ServerResponse> cleanupDetail(ServerRequest request) {
        return Mono.defer(() -> cleanupService.detail(positiveId(request.pathVariable("docId"))))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 接受手工清理重试，活跃认领或已完成复核时拒绝。
     *
     * <p>返回 true 仅表示重试已接受并持久化，不代表远程索引或对象清理已完成。
     *
     * @param request 含 docId 路径变量的请求
     * @return 包装布尔接受结果的响应；拒绝或链中错误转换为统一错误响应
     */
    @RequirePermission("kb:doc:delete")
    @OperationLog(module = "知识库", type = OperationLog.OperationType.DELETE, description = "重试文档清理")
    @Operation(
            operationId = "kbRetryDocumentCleanup",
            summary = "重试文档清理",
            description = "接受手工清理重试，活跃认领或已完成复核时拒绝；返回 true 仅表示重试已接受并持久化，" + "不代表远程索引或对象清理已完成。需 kb:doc:delete 权限",
            parameters =
                    @Parameter(
                            name = "docId",
                            in = ParameterIn.PATH,
                            required = true,
                            description = "文档 ID",
                            schema = @Schema(type = "integer", format = "int64", minimum = "1")))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "重试已接受，data 为布尔接受结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentBooleanResult.class))),
        @ApiResponse(responseCode = "400", description = "文档 ID 格式非法，或凭证状态不允许重试"),
        @ApiResponse(responseCode = "404", description = "清理凭证不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:delete 权限")
    })
    public Mono<ServerResponse> retryCleanup(ServerRequest request) {
        return Mono.defer(() -> cleanupService.retry(positiveId(request.pathVariable("docId"))))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    private static Long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // 非法路径参数统一映射业务参数错误。
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "docId必须为正整数");
    }

    private static int positiveInt(String value, int min, int max) {
        try {
            int number = Integer.parseInt(value);
            if (number >= min && number <= max) return number;
        } catch (NumberFormatException ignored) {
            // 非法分页参数统一映射业务参数错误。
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "分页参数无效");
    }

    /**
     * 查询文档分页，使用 {@code page}/{@code size} 而非应用服务的参数名。
     *
     * <p>{@code spaceId} 无法解析时不附加空间过滤；{@code page}/{@code size}
     * 缺失、无法解析或为负数时分别使用 1 与 20，零值会直接传给应用服务。
     * 解析状态与关键词按原值透传，此处不校验状态枚举。
     *
     * @param request 包含可选 {@code spaceId}、{@code parseStatus}、{@code keyword}、
     *     {@code page} 与 {@code size} 查询参数的请求
     * @return 包装文档分页数据的成功响应，或链中错误对应的统一错误响应
     */
    @RequirePermission("kb:doc:read")
    @Operation(
            operationId = "kbPageDocuments",
            summary = "分页查询知识文档",
            description = "按空间、解析状态与关键字分页查询文档；spaceId 无法解析时不附加空间过滤，" + "page/size 非法时回退为 1/20。需 kb:doc:read 权限")
    @Parameter(
            name = "spaceId",
            in = ParameterIn.QUERY,
            description = "限定空间 ID，可空",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(
            name = "parseStatus",
            in = ParameterIn.QUERY,
            description = "解析状态过滤，按原值透传不做枚举校验",
            schema = @Schema(type = "string", example = "SUCCESS"))
    @Parameter(
            name = "keyword",
            in = ParameterIn.QUERY,
            description = "文档标题检索关键字，可空",
            schema = @Schema(type = "string"))
    @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始，缺省 1",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1", minimum = "1"))
    @Parameter(
            name = "size",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 20",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "分页查询成功，data 为文档分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentPageResult.class))),
        @ApiResponse(responseCode = "400", description = "查询参数格式非法"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:read 权限")
    })
    public Mono<ServerResponse> page(ServerRequest request) {
        Long spaceId = KnowledgeRequestHandlerUtil.safeParseLong(
                request.queryParam("spaceId").orElse(null));
        String parseStatus = request.queryParam("parseStatus").orElse(null);
        String keyword = request.queryParam("keyword").orElse(null);
        int pageNum = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("page").orElse(null), 1);
        int pageSize = KnowledgeRequestHandlerUtil.safeParseInt(
                request.queryParam("size").orElse(null), 20);
        return documentAppService
                .findPage(spaceId, parseStatus, keyword, pageNum, pageSize)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 查询指定文档详情及数据库分块预览。
     *
     * @param request 含 {@code docId} 路径变量的请求
     * @return 包装文档详情的成功响应；不存在等链中错误转换为统一错误响应
     * @throws NumberFormatException {@code docId} 不能解析为 {@link Long} 时同步抛出
     */
    @RequirePermission("kb:doc:read")
    @Operation(
            operationId = "kbGetDocument",
            summary = "查询知识文档详情",
            description = "返回文档基础信息及数据库分块预览（不含向量），分块按 seq 升序。需 kb:doc:read 权限")
    @Parameter(
            name = "docId",
            in = ParameterIn.PATH,
            required = true,
            description = "文档 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 包含 document 与 chunks",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentDetailResult.class))),
        @ApiResponse(responseCode = "400", description = "文档 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "文档不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:read 权限")
    })
    public Mono<ServerResponse> detail(ServerRequest request) {
        Long docId = Long.valueOf(request.pathVariable("docId"));
        return documentAppService
                .findById(docId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 聚合上传文件字节，登记文档后返回，不等待摄取完成。
     *
     * <p>仅取首个 {@code file} 字段，并要求其为 {@link FilePart}；
     * 缺失或类型不符时返回 {@code PARAM_ERROR}，未提供内容类型时使用
     * {@code application/octet-stream}。扩展名及非空大小校验由应用服务执行。
     *
     * @param request 含 {@code spaceId} 路径变量及 multipart {@code file} 字段的请求
     * @return 包装登记后文档 DTO 的成功响应；读取、校验或登记链中的错误转换为统一错误响应
     * @throws NumberFormatException {@code spaceId} 不能解析为 {@link Long} 时同步抛出
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.UPLOAD, description = "上传知识文档")
    @RequirePermission("kb:doc:upload")
    @Operation(
            operationId = "kbUploadDocument",
            summary = "上传知识文档",
            description = "以 multipart/form-data 上传单个文件（字段名 file），登记文档后立即返回，不等待摄取与索引完成；"
                    + "聚合字节上限由 apex.knowledge.ingest.max-upload-size-mb 控制。需 kb:doc:upload 权限")
    @Parameter(
            name = "spaceId",
            in = ParameterIn.PATH,
            required = true,
            description = "目标空间 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @RequestBody(
            required = true,
            description = "multipart 表单；file 字段必填且须为文件类型",
            content =
                    @Content(
                            mediaType = "multipart/form-data",
                            schema = @Schema(type = "object", description = "包含 file 文件字段的 multipart 表单")))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "登记成功，data 为登记后的文档（摄取尚未完成）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentResult.class))),
        @ApiResponse(responseCode = "400", description = "缺少 file 字段、类型不符或文件超限"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:upload 权限")
    })
    public Mono<ServerResponse> upload(ServerRequest request) {
        Long spaceId = Long.valueOf(request.pathVariable("spaceId"));
        long maxBytes = (long) properties.getIngest().getMaxUploadSizeMb() * 1024 * 1024;
        return request.multipartData()
                .flatMap(parts -> {
                    Part part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "缺少文件字段 file"));
                    }
                    String contentType = filePart.headers().getContentType() == null
                            ? "application/octet-stream"
                            : filePart.headers().getContentType().toString();
                    return readBytes(filePart, maxBytes)
                            .map(bytes -> new UploadPayload(filePart.filename(), contentType, bytes));
                })
                .flatMap(payload -> documentAppService.registerUpload(
                        spaceId, payload.fileName(), payload.contentType(), payload.content()))
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 解码并校验在线录入 JSON，将正文交由应用服务入库后触发摄取。
     *
     * @param request 请求体为 {@link KnowledgeManualDocumentRequest} 的请求
     * @return 包装登记后文档 DTO 的成功响应，不等待摄取完成；链中错误转换为统一错误响应
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.INSERT, description = "在线录入知识文档")
    @RequirePermission("kb:doc:upload")
    @Operation(
            operationId = "kbRegisterManualDocument",
            summary = "在线录入知识文档",
            description = "以纯文本正文直接登记文档（MANUAL 来源，不经对象存储），登记后立即返回并异步触发摄取；"
                    + "title 最长 200 字符，content 最长 400000 字符。需 kb:doc:upload 权限")
    @RequestBody(
            required = true,
            description = "在线录入请求",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = KnowledgeManualDocumentRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "登记成功，data 为登记后的文档（摄取尚未完成）",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentResult.class))),
        @ApiResponse(responseCode = "400", description = "空间 ID 缺失、标题或正文空白、超长或请求体缺失"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:upload 权限")
    })
    public Mono<ServerResponse> registerManual(ServerRequest request) {
        return requestUtil
                .handleRequestBody(request, KnowledgeManualDocumentRequest.class)
                .flatMap(documentAppService::registerManual)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 接受文档删除意图，在短事务中持久保存清理凭证并逻辑删除主档。
     *
     * <p>成功Boolean仅表示已接受，不表示对象、分块或索引已清除；实际进度通过清理接口查询。
     * 缺可信租户拒绝，不静默跳过索引。历史对象缺定位快照保留失败凭证，不能以当前默认存储删除。
     *
     * @param request 含 {@code docId} 路径变量的请求
     * @return 包装accepted布尔结果；链中错误转换为统一错误响应
     * @throws NumberFormatException {@code docId} 不能解析为 {@link Long} 时同步抛出
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.DELETE, description = "删除知识文档")
    @RequirePermission("kb:doc:delete")
    @Operation(
            operationId = "kbDeleteDocument",
            summary = "删除知识文档",
            description = "接受删除意图：短事务内持久化清理凭证并逻辑删除主档，返回 true 仅表示已接受，" + "对象/分块/索引的实际清除进度请通过清理接口查询。需 kb:doc:delete 权限")
    @Parameter(
            name = "docId",
            in = ParameterIn.PATH,
            required = true,
            description = "文档 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除已受理（异步清理），data 为 accepted 布尔值",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentBooleanResult.class))),
        @ApiResponse(responseCode = "400", description = "文档 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "文档不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:delete 权限")
    })
    public Mono<ServerResponse> delete(ServerRequest request) {
        Long docId = Long.valueOf(request.pathVariable("docId"));
        return documentAppService
                .delete(docId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 更新文档重建状态并独立触发重新摄取，有非空正文时摄取管线跳过解析。
     *
     * @param request 含 {@code docId} 路径变量的请求
     * @return 包装状态更新后文档 DTO 的成功响应，不等待摄取及索引任务执行完成；
     *     链中错误转换为统一错误响应
     * @throws NumberFormatException {@code docId} 不能解析为 {@link Long} 时同步抛出
     */
    @OperationLog(module = "知识库", type = OperationLog.OperationType.UPDATE, description = "重建知识文档索引")
    @RequirePermission("kb:doc:reindex")
    @Operation(
            operationId = "kbReindexDocument",
            summary = "重建知识文档索引",
            description = "更新重建状态并独立触发重新摄取；非空正文时摄取管线跳过解析。" + "返回后不等待摄取与索引任务完成。需 kb:doc:reindex 权限")
    @Parameter(
            name = "docId",
            in = ParameterIn.PATH,
            required = true,
            description = "文档 ID",
            schema = @Schema(type = "integer", format = "int64", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "重建已受理，data 为状态更新后的文档",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = KnowledgeDocumentResult.class))),
        @ApiResponse(responseCode = "400", description = "文档 ID 格式非法"),
        @ApiResponse(responseCode = "404", description = "文档不存在"),
        @ApiResponse(responseCode = "401", description = "未认证或访问令牌失效"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 kb:doc:reindex 权限")
    })
    public Mono<ServerResponse> reindex(ServerRequest request) {
        Long docId = Long.valueOf(request.pathVariable("docId"));
        return documentAppService
                .reindex(docId)
                .flatMap(requestUtil::buildSuccessResponse)
                .onErrorResume(requestUtil::handleError);
    }

    /**
     * 限制聚合大小，将文件内容缓冲复制为完整字节数组。
     *
     * <p>上游缓冲的聚合与超限处理委托
     * {@link DataBufferUtils#join(org.reactivestreams.Publisher, int)}；
     * 聚合缓冲在复制后由 {@code finally} 释放，复制抛错时同样释放。
     * 超限产生的 {@link DataBufferLimitException} 转为 {@code PARAM_ERROR} 业务错误信号，
     * 其他读取或复制错误原样向下游传播。此方法不实现速率限流，也不进行流式对象上传。
     *
     * @param filePart multipart 文件字段
     * @param maxBytes 聚合字节上限；当前调用强制转为 {@code int}，配置须避免转换溢出
     * @return 完整文件字节；超限或读取失败时发出错误信号
     */
    private Mono<byte[]> readBytes(FilePart filePart, long maxBytes) {
        return DataBufferUtils.join(filePart.content(), (int) maxBytes)
                .map(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .onErrorMap(
                        DataBufferLimitException.class,
                        e -> new BusinessException(
                                ResultCode.PARAM_ERROR,
                                "文件大小超出限制: " + properties.getIngest().getMaxUploadSizeMb() + "MB"));
    }

    /**
     * 缓冲聚合后传给应用服务的内部上传载荷，不持有待释放的 {@code DataBuffer}。
     *
     * @param fileName 原始文件名
     * @param contentType 上传内容类型
     * @param content 已复制出的文件字节
     */
    private record UploadPayload(String fileName, String contentType, byte[] content) {}
}
