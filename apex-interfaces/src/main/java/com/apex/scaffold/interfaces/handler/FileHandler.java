package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.FileUploadResult;
import com.apex.scaffold.application.service.FileService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.dto.FileUploadRequestDoc;
import com.apex.scaffold.interfaces.dto.GenericFileUploadRequestDoc;
import com.apex.scaffold.interfaces.monitor.BusinessMetricsRecorder;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 文件处理器（函数式端点）
 *
 * <p>
 * 提供文件上传、删除、获取预签名 URL 和文件存在性检查等 RESTful API，
 * 基于 Spring WebFlux 函数式编程模型实现，底层集成对象存储服务。
 * 继承 {@link BaseHandler} 使用统一的方法进行参数安全解析和响应构建。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>文件上传：支持受控图片格式并执行魔数与违规内容检测</li>
 *   <li>获取文件访问 URL：生成预签名访问地址</li>
 *   <li>删除文件：从对象存储中移除指定文件</li>
 *   <li>检查文件存在：验证文件是否已存储</li>
 * </ul>
 *
 * <h2>文件限制</h2>
 * <ul>
 *   <li>文件大小限制由配置项 {@code file.upload.max-size-bytes} 控制，默认 10 MiB</li>
 *   <li>文件上传请求需通过 multipart/form-data 格式提交</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see FileService
 * @see BaseHandler
 */
@Slf4j
@Component
@Tag(name = "文件管理", description = "文件上传、下载、删除相关接口")
public class FileHandler extends BaseHandler {

    private final FileService fileService;

    /** 核心业务指标记录器。 */
    private final BusinessMetricsRecorder businessMetricsRecorder;

    @Value("${file.upload.max-size-bytes:10485760}")
    private long maxFileSize;

    @Value("${file.upload.enabled:true}")
    private boolean uploadEnabled;

    /**
     * 男性性别编码。
     */
    private static final int SEX_AM = 1;
    /**
     * 女性性别编码。
     */
    private static final int SEX_AF = 2;

    @Value("${storage.bucket-name:apex}")
    private String defaultBucketName;

    /**
     * 构造文件处理器
     *
     * @param fileService 文件服务
     * @param messageUtils 国际化消息工具
     */
    public FileHandler(
            FileService fileService, MessageUtils messageUtils, BusinessMetricsRecorder businessMetricsRecorder) {
        super(messageUtils);
        this.fileService = fileService;
        this.businessMetricsRecorder = businessMetricsRecorder;
    }

    /**
     * 检查文件信息是否缺失。
     *
     * <p>
     * 判断存储桶名称和文件名称是否有效，用于文件操作前的参数校验。
     *
     * @param bucketName 存储桶名称
     * @param fileName 文件名称
     * @return 如果任一参数为 {@code null} 或空白字符串则返回 {@code true}，否则返回 {@code false}
     */
    private boolean isFileInfoMissing(String bucketName, String fileName) {
        return bucketName == null || bucketName.isBlank() || fileName == null || fileName.isBlank();
    }

    /**
     * 上传文件
     *
     * <p>
     * 处理 multipart/form-data 文件上传请求，将文件数据读取后上传到服务端固定对象存储桶。
     * 上传完成后返回桶名、唯一对象名、预签名访问 URL 和有效期。
     *
     * <h3>处理流程</h3>
     * <ul>
     *   <li>从请求中提取文件 Part，存储桶仅由服务端配置决定</li>
     *   <li>流式读取文件数据，边读边检查大小</li>
     *   <li>如果文件大小超过限制，立即返回错误响应</li>
     *   <li>调用 {@link FileService} 执行上传</li>
     *   <li>生成并返回文件访问 URL</li>
     * </ul>
     *
     * <h3>文件大小限制</h3>
     * <p>文件大小限制由配置项 {@code file.upload.max-size-bytes} 控制，默认 10 MiB。
     * 使用流式检查方式，避免将大文件全部读入内存导致内存溢出。</p>
     *
     * @param request 服务器请求对象，包含 multipart 表单数据中的 file 字段
     * @return 包含完整 {@link FileUploadResult} 的统一响应对象
     */
    @Operation(
            summary = "上传图片",
            deprecated = true,
            description =
                    """
                    已过时的兼容上传入口。普通图片请使用 POST /api/v1/files/generic。
                    该入口将在客户端完成迁移后移除。
                    """,
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "图片上传表单",
                            content =
                                    @Content(
                                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                            schema = @Schema(implementation = FileUploadRequestDoc.class))))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "上传成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileUploadResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "文件为空、格式不支持或图片内容违规",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "上传失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @Deprecated(since = "2.2.0", forRemoval = true)
    @OperationLog(module = "文件管理", type = OperationLog.OperationType.UPLOAD, description = "上传图片（兼容入口）")
    public Mono<ServerResponse> upload(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        if (!uploadEnabled) {
            return buildError(ResultCode.ERROR.getCode(), "文件上传功能未启用", traceId);
        }
        if (!isMultipartRequest(request)) {
            return buildError(ResultCode.PARAM_ERROR.getCode(), "param.multipart_content_type_invalid", traceId);
        }
        if (!hasMultipartBoundary(request)) {
            return buildError(ResultCode.PARAM_ERROR.getCode(), "param.multipart_boundary_missing", traceId);
        }

        return request.multipartData()
                .flatMap(parts -> {
                    Integer clientSex = extractUploadSex(parts, request);
                    Part part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return buildError(ResultCode.PARAM_ERROR.getCode(), "param.file_not_null", traceId);
                    }
                    String filename = filePart.filename();
                    log.info("上传文件请求, filename: {}, traceId: {}", filename, traceId);
                    return handleGenericUpload(filename, filePart, clientSex, traceId, request);
                })
                .onErrorResume(DecodingException.class, ex -> {
                    log.warn("上传文件 multipart 解析失败: {}, traceId: {}", ex.getMessage(), traceId);
                    return buildError(ResultCode.PARAM_ERROR.getCode(), "param.multipart_invalid", traceId);
                });
    }

    /**
     * 上传普通业务图片。
     *
     * <p>这是旧 {@code /api/v1/file/upload} 的替代入口。接口固定执行
     * 格式、大小和魔数校验后写入普通对象存储目录，不区分业务分支。</p>
     *
     * @param request 包含 file 和 sex 的 multipart 请求
     * @return 普通对象存储上传结果
     */
    @Operation(
            operationId = "uploadGenericFile",
            summary = "上传普通图片",
            description =
                    """
                    普通图片上传的独立接口。对象存储前依次执行百度云图片审核和
                    Python FGCLIP 违规检测；审核通过后仅写入普通对象存储。
                    sex 必填：0=男（AM 目录），1=女（AF 目录）。接口不接受 usage。
                    """,
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            description = "普通图片上传表单",
                            content =
                                    @Content(
                                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                                            schema = @Schema(implementation = GenericFileUploadRequestDoc.class))))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "百度和 FGCLIP 审核通过，普通对象上传成功",
                content = @Content(schema = @Schema(implementation = FileUploadResult.class))),
        @ApiResponse(responseCode = "400", description = "表单、文件格式或审核结果不符合要求"),
        @ApiResponse(responseCode = "401", description = "未登录"),
        @ApiResponse(responseCode = "503", description = "百度或 Python 审核服务不可用")
    })
    @OperationLog(module = "文件管理", type = OperationLog.OperationType.UPLOAD, description = "上传普通图片")
    public Mono<ServerResponse> uploadGenericFile(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        if (!uploadEnabled) {
            return buildError(ResultCode.ERROR.getCode(), "文件上传功能未启用", traceId);
        }
        if (!isMultipartRequest(request) || !hasMultipartBoundary(request)) {
            return buildError(ResultCode.PARAM_ERROR.getCode(), "param.multipart_invalid", traceId);
        }
        return request.multipartData()
                .flatMap(parts -> {
                    Integer sex = extractUploadSex(parts, request);
                    if (sex == null) {
                        return buildError(ResultCode.PARAM_ERROR.getCode(), "param.sex_not_null", traceId);
                    }
                    Part part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return buildError(ResultCode.PARAM_ERROR.getCode(), "param.file_not_null", traceId);
                    }
                    String filename = filePart.filename();
                    log.info("普通图片上传请求, filename: {}, traceId: {}", filename, traceId);
                    return handleGenericUpload(filename, filePart, sex, traceId, request);
                })
                .onErrorResume(DecodingException.class, ex -> {
                    log.warn("普通图片 multipart 解析失败: {}, traceId: {}", ex.getMessage(), traceId);
                    return buildError(ResultCode.PARAM_ERROR.getCode(), "param.multipart_invalid", traceId);
                });
    }

    /**
     * 解析文件部分的 Content-Type。
     *
     * @param filePart multipart 中的文件部分
     * @return 内容类型字符串，未声明时返回 {@code application/octet-stream}
     */
    private String contentType(FilePart filePart) {
        return filePart.headers().getContentType() != null
                ? filePart.headers().getContentType().toString()
                : "application/octet-stream";
    }

    /**
     * 判断请求是否声明为 multipart/form-data。
     *
     * @param request 当前请求
     * @return 如果 Content-Type 与 multipart/form-data 兼容则返回 {@code true}
     */
    private boolean isMultipartRequest(ServerRequest request) {
        return request.headers()
                .contentType()
                .map(contentType -> contentType.isCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .orElse(false);
    }

    /**
     * 提取上传请求中的 sex 参数。
     *
     * <p>优先从 multipart 表单字段读取；若调用方仍通过查询参数传递，则作为兼容性兜底。
     * sex 仅支持 1 或 2，非法值返回 {@code null}，由调用方统一转换为参数错误响应。</p>
     *
     * @param parts multipart 表单字段
     * @param request 当前请求
     * @return 1 或 2；缺失或非法时返回 {@code null}
     */
    private Integer extractUploadSex(
            org.springframework.util.MultiValueMap<String, Part> parts, ServerRequest request) {
        Part sexPart = parts.getFirst("sex");
        if (sexPart instanceof FormFieldPart formFieldPart) {
            return parseUploadSex(formFieldPart.value());
        }
        return request.queryParam("sex").map(this::parseUploadSex).orElse(null);
    }

    /**
     * 解析上传 sex 参数。
     *
     * @param rawSex 原始参数值
     * @return 1（男性）或 2（女性）；其他情况返回 {@code null}
     */
    private Integer parseUploadSex(String rawSex) {
        if (rawSex == null || rawSex.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(rawSex.trim());
            return (value == SEX_AM || value == SEX_AF) ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 判断 multipart 请求头是否携带 boundary 参数。
     *
     * <p>缺失 boundary 时，WebFlux 底层会在解析 multipart 时抛出
     * {@link DecodingException}。这里提前校验可直接向客户端返回明确的 400 错误。</p>
     *
     * @param request 当前请求
     * @return 如果请求头中存在非空 boundary 参数则返回 {@code true}
     */
    private boolean hasMultipartBoundary(ServerRequest request) {
        return request.headers()
                .contentType()
                .map(contentType -> {
                    String boundary = contentType.getParameter("boundary");
                    return boundary != null && !boundary.isBlank();
                })
                .orElse(false);
    }

    /**
     * 处理通用文件上传。
     *
     * @param filename  原始文件名
     * @param filePart  multipart 文件部分
     * @param sex       性别路由参数
     * @param traceId   追踪 ID
     * @return 上传结果响应
     */
    private Mono<ServerResponse> handleGenericUpload(
            String filename, FilePart filePart, Integer sex, String traceId, ServerRequest request) {
        return currentOpenId(request)
                .defaultIfEmpty("")
                .flatMap(openId -> readFileBytes(filePart, filename, traceId).flatMap(bytes -> {
                    String contentType = filePart.headers().getContentType() != null
                            ? filePart.headers().getContentType().toString()
                            : "application/octet-stream";
                    return fileService.upload(filename, bytes, contentType, sex, openId);
                }))
                .flatMap(uploadResult -> buildOk(Result.success(uploadResult, traceId)))
                .doOnSuccess(response -> businessMetricsRecorder.recordUpload())
                .doOnError(e -> log.error("文件上传失败, filename: {}, error: {}", filename, e.getMessage(), e));
    }

    /**
     * 流式读取 multipart 文件字节内容，带大小校验。
     *
     * @param filePart multipart 文件部分
     * @param filename 原始文件名
     * @param traceId  追踪 ID
     * @return 文件字节数组
     */
    private Mono<byte[]> readFileBytes(FilePart filePart, String filename, String traceId) {
        return filePart.content()
                .reduce(new UploadAccumulator(), (acc, buffer) -> {
                    if (acc.overflow) {
                        DataBufferUtils.release(buffer);
                        return acc;
                    }
                    acc.totalSize += buffer.readableByteCount();
                    if (acc.totalSize > maxFileSize) {
                        acc.overflow = true;
                        DataBufferUtils.release(buffer);
                        acc.buffers.forEach(DataBufferUtils::release);
                        acc.buffers.clear();
                        return acc;
                    }
                    acc.buffers.add(buffer);
                    return acc;
                })
                .flatMap(acc -> {
                    if (acc.overflow) {
                        return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR,
                                "文件大小超过限制（最大 " + maxFileSize / 1024 / 1024 + " MB）"));
                    }
                    if (acc.totalSize == 0) {
                        return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "文件不能为空"));
                    }
                    byte[] bytes = new byte[(int) acc.totalSize];
                    int offset = 0;
                    for (DataBuffer buffer : acc.buffers) {
                        int readable = buffer.readableByteCount();
                        buffer.read(bytes, offset, readable);
                        offset += readable;
                    }
                    acc.buffers.forEach(DataBufferUtils::release);
                    acc.buffers.clear();
                    return Mono.just(bytes);
                });
    }

    /**
     * 文件上传累加器，用于流式读取时累计文件大小和数据。
     *
     * <p>增加 overflow 标记，当文件大小超限时在 reduce 中立即中断累积并释放已持有的 buffer，
     * 防止超限场景下的内存泄漏。所有 buffer 在合并为字节数组后统一释放。
     *
     * @author Apex Team
     */
    private static class UploadAccumulator {
        long totalSize = 0;
        boolean overflow = false;
        java.util.List<org.springframework.core.io.buffer.DataBuffer> buffers = new java.util.ArrayList<>();
    }

    /**
     * 获取文件 URL
     *
     * <p>
     * 根据存储桶名称和文件名获取文件的预签名访问地址。
     * 预签名 URL 具有时效性，可直接用于文件下载。
     *
     * @param request 服务器请求对象，包含 {@code bucketName} 和 {@code fileName} 查询参数
     * @return 包含文件预签名 URL 的响应对象
     */
    @Operation(
            operationId = "getFileUrl",
            summary = "获取文件URL",
            description = "根据存储桶名称和文件名获取文件的预签名访问地址，预签名 URL 具有时效性，可直接用于文件下载")
    @Parameters({
        @Parameter(
                name = "bucketName",
                description = "存储桶名称",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "fileName",
                description = "文件名称",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "获取成功，返回文件预签名URL",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，bucketName或fileName为空",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getUrl(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        String bucketName = request.queryParam("bucketName").orElse(null);
        String fileName = request.queryParam("fileName").orElse(null);
        if (isFileInfoMissing(bucketName, fileName)) {
            return buildError(ResultCode.PARAM_ERROR.getCode(), "param.bucket_file_not_null", traceId);
        }
        return fileService.getUrl(bucketName, fileName).flatMap(fileUrl -> buildOk(Result.success(fileUrl, traceId)));
    }

    /**
     * 删除文件
     *
     * <p>
     * 从对象存储系统中删除指定的文件，删除操作不可逆。
     *
     * @param request 服务器请求对象，包含 {@code bucketName} 和 {@code fileName} 查询参数
     * @return 包含删除操作结果的响应对象，成功时返回 {@code true}
     */
    @Operation(operationId = "deleteFile", summary = "删除文件", description = "从对象存储系统中删除指定的文件，删除操作不可逆，请谨慎使用")
    @Parameters({
        @Parameter(
                name = "bucketName",
                description = "存储桶名称",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "fileName",
                description = "文件名称",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "删除成功，返回操作结果",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，bucketName或fileName为空",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "文件不存在",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(module = "文件管理", type = OperationLog.OperationType.DELETE, description = "删除文件")
    public Mono<ServerResponse> delete(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        String bucketName = request.queryParam("bucketName").orElse(null);
        String fileName = request.queryParam("fileName").orElse(null);
        if (isFileInfoMissing(bucketName, fileName)) {
            return buildError(ResultCode.PARAM_ERROR.getCode(), "param.bucket_file_not_null", traceId);
        }
        return fileService.delete(bucketName, fileName).flatMap(success -> buildOk(Result.success(success, traceId)));
    }

    /**
     * 检查文件是否存在
     *
     * <p>
     * 查询指定文件是否已存在于对象存储系统中。
     *
     * @param request 服务器请求对象，包含 {@code bucketName} 和 {@code fileName} 查询参数
     * @return 包含文件存在状态的响应对象，成功时返回 {@code true} 或 {@code false}
     */
    @Operation(operationId = "checkFileExists", summary = "检查文件是否存在", description = "查询指定文件是否已存在于对象存储系统中，返回布尔值表示文件是否存在")
    @Parameters({
        @Parameter(
                name = "bucketName",
                description = "存储桶名称",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "fileName",
                description = "文件名称",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，返回文件是否存在",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，bucketName或fileName为空",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "服务器内部错误",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> exists(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        String bucketName = request.queryParam("bucketName").orElse(null);
        String fileName = request.queryParam("fileName").orElse(null);
        if (isFileInfoMissing(bucketName, fileName)) {
            return buildError(ResultCode.PARAM_ERROR.getCode(), "param.bucket_file_not_null", traceId);
        }
        return fileService.exists(bucketName, fileName).flatMap(exists -> buildOk(Result.success(exists, traceId)));
    }

    /**
     * 读取默认存储桶中的文件内容。
     *
     * <p>该端点主要为本地存储模式提供可访问 URL。物理桶名由服务端配置固定，
     * 客户端只能提交上传结果中的 UUID 对象名，不能跨桶读取。</p>
     *
     * @param request 包含 fileName 查询参数的服务器请求
     * @return 流式文件响应
     */
    @Operation(
            operationId = "getFileContent",
            summary = "读取本地存储文件",
            description = "仅用于 local 存储模式，按服务端固定桶流式读取文件内容并返回，主要为本地存储提供可访问 URL")
    @Parameters({
        @Parameter(
                name = "fileName",
                description = "上传后返回的对象名称（UUID格式）",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "读取成功，返回文件内容流",
                content = @Content(mediaType = "application/octet-stream")),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，fileName缺失或为空",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "文件不存在",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "文件读取失败",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> content(ServerRequest request) {
        String traceId = TraceIdUtil.getTraceId();
        String fileName = request.queryParam("fileName").orElse(null);
        if (fileName == null || fileName.isBlank()) {
            return buildError(ResultCode.PARAM_ERROR.getCode(), "文件名称不能为空", traceId);
        }
        return fileService.download(defaultBucketName, fileName).flatMap(inputStream -> {
            Flux<DataBuffer> content = DataBufferUtils.readInputStream(
                            () -> inputStream, DefaultDataBufferFactory.sharedInstance, 8192)
                    .doFinally(signal -> {
                        try {
                            inputStream.close();
                        } catch (java.io.IOException exception) {
                            log.warn("关闭本地文件读取流失败, fileName: {}", fileName, exception);
                        }
                    });
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content, DataBuffer.class);
        });
    }
}
