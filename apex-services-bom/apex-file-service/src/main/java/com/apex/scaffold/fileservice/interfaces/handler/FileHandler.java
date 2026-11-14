package com.apex.scaffold.fileservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.FileUploadResult;
import com.apex.scaffold.file.application.service.FileService;
import com.apex.scaffold.fileservice.interfaces.dto.FileUploadRequestDoc;
import com.apex.scaffold.fileservice.interfaces.dto.GenericFileUploadRequestDoc;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
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
 * 使用 {@link RequestHandlerUtil} 完成统一的参数处理和响应构建。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>文件上传：支持受控图片格式并执行魔数与违规内容检测</li>
 *   <li>获取文件访问 URL：生成预签名访问地址</li>
 *   <li>删除文件：从对象存储中移除指定文件</li>
 *   <li>检查文件存在：验证文件是否已存储</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>文件大小限制由配置项 {@code file.upload.max-size-bytes} 控制，默认 10 MiB</li>
 *   <li>文件上传请求需通过 multipart/form-data 格式提交</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see FileService
 * @see RequestHandlerUtil
 */
@Slf4j
@Component
@Tag(name = "文件管理", description = "文件上传、下载、删除相关接口")
public class FileHandler {

    private final FileService fileService;

    private final RequestHandlerUtil requestHandlerUtil;

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

    /**
     * PNG 图片扩展名。
     */
    private static final String EXT_PNG = ".png";
    /**
     * JPG 图片扩展名。
     */
    private static final String EXT_JPG = ".jpg";
    /**
     * JPEG 图片扩展名。
     */
    private static final String EXT_JPEG = ".jpeg";
    /**
     * GIF 图片扩展名。
     */
    private static final String EXT_GIF = ".gif";
    /**
     * WebP 图片扩展名。
     */
    private static final String EXT_WEBP = ".webp";
    /**
     * BMP 图片扩展名。
     */
    private static final String EXT_BMP = ".bmp";
    /**
     * SVG 图片扩展名。
     */
    private static final String EXT_SVG = ".svg";
    /**
     * PDF 文档扩展名。
     */
    private static final String EXT_PDF = ".pdf";
    /**
     * 纯文本扩展名。
     */
    private static final String EXT_TXT = ".txt";

    @Value("${storage.bucket-name:apex}")
    private String defaultBucketName;

    /**
     * 构造文件处理器。
     *
     * @param fileService        文件服务
     * @param requestHandlerUtil 请求处理工具
     */
    public FileHandler(FileService fileService, RequestHandlerUtil requestHandlerUtil) {
        this.fileService = fileService;
        this.requestHandlerUtil = requestHandlerUtil;
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
     * 上传完成后返回桶名、唯一对象名和访问 URL；云存储返回签名有效秒数，Local 返回需认证授权的内容地址且 expiresIn 为 0。
     *
     * <p><strong>处理流程</strong></p>
     * <ul>
     *   <li>从请求中提取文件 Part，存储桶仅由服务端配置决定</li>
     *   <li>流式读取文件数据，边读边检查大小</li>
     *   <li>如果文件大小超过限制，立即返回错误响应</li>
     *   <li>调用 {@link FileService} 执行上传</li>
     *   <li>生成并返回文件访问 URL</li>
     * </ul>
     *
     * <p><strong>文件大小限制</strong></p>
     * <p>文件大小限制由配置项 {@code file.upload.max-size-bytes} 控制，默认 10 MiB。
     * 逐块累计大小，超限后释放已持有的缓冲区；限制内的文件会完整合并到内存字节数组。</p>
     *
     * @param request 服务器请求对象，包含 multipart 表单数据中的 file 字段
     * @return 包含完整 {@link FileUploadResult} 的统一响应对象
     */
    @Operation(
            operationId = "fileUpload",
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
                                schema = @Schema(implementation = FileUploadResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "文件为空、格式不支持或图片内容违规",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @Deprecated(since = "2.2.0", forRemoval = true)
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.UPLOAD,
            description = "upload",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> upload(ServerRequest request) {
        if (!uploadEnabled) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.ERROR, "文件上传功能未启用");
        }
        if (!isMultipartRequest(request)) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "multipart 请求 Content-Type 无效");
        }
        if (!hasMultipartBoundary(request)) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "multipart 请求缺少 boundary 参数");
        }

        return request.multipartData()
                .flatMap(parts -> {
                    Integer clientSex = extractUploadSex(parts, request);
                    Part part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "上传文件不能为空");
                    }
                    String filename = filePart.filename();
                    log.info("上传文件请求, filename: {}", filename);
                    return handleGenericUpload(filename, filePart, clientSex, request);
                })
                .onErrorResume(DecodingException.class, ex -> {
                    log.warn("上传文件 multipart 解析失败: {}", ex.getMessage());
                    return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "multipart 请求解析失败");
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
                    sex 必填：1=男（AM 目录），2=女（AF 目录）。接口仅解析 file 和 sex。
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
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileUploadResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "表单、文件格式或审核结果不符合要求",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.UPLOAD,
            description = "uploadGenericFile",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> uploadGenericFile(ServerRequest request) {
        if (!uploadEnabled) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.ERROR, "文件上传功能未启用");
        }
        if (!isMultipartRequest(request) || !hasMultipartBoundary(request)) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "multipart 请求解析失败");
        }
        return request.multipartData()
                .flatMap(parts -> {
                    Integer sex = extractUploadSex(parts, request);
                    if (sex == null) {
                        return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "sex 参数不能为空");
                    }
                    Part part = parts.getFirst("file");
                    if (!(part instanceof FilePart filePart)) {
                        return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "上传文件不能为空");
                    }
                    String filename = filePart.filename();
                    log.info("普通图片上传请求, filename: {}", filename);
                    return handleGenericUpload(filename, filePart, sex, request);
                })
                .onErrorResume(DecodingException.class, ex -> {
                    log.warn("普通图片 multipart 解析失败: {}", ex.getMessage());
                    return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "multipart 请求解析失败");
                });
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
    private Integer extractUploadSex(MultiValueMap<String, Part> parts, ServerRequest request) {
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
     * <p>文件服务微服务中 {@code AuthenticatedUser.openId} 恒为 {@code null}，
     * 此处统一以空串作为对象存储的 openId 传递给文件服务。</p>
     *
     * @param filename  原始文件名
     * @param filePart  multipart 文件部分
     * @param sex       性别路由参数
     * @param request 当前服务器请求
     * @return 上传结果响应
     */
    private Mono<ServerResponse> handleGenericUpload(
            String filename, FilePart filePart, Integer sex, ServerRequest request) {
        return readFileBytes(filePart, filename)
                .flatMap(bytes -> {
                    String contentType = filePart.headers().getContentType() != null
                            ? filePart.headers().getContentType().toString()
                            : "application/octet-stream";
                    return fileService.upload(filename, bytes, contentType, sex, "");
                })
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .doOnError(e -> log.error("文件上传失败, filename: {}, error: {}", filename, e.getMessage(), e));
    }

    /**
     * 流式读取 multipart 文件字节内容，带大小校验。
     *
     * @param filePart multipart 文件部分
     * @param filename 原始文件名
     * @return 文件字节数组
     */
    private Mono<byte[]> readFileBytes(FilePart filePart, String filename) {
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
                        return Mono.error(new BusinessException(
                                ResultCode.PARAM_ERROR, "文件大小超过限制（最大 " + maxFileSize / 1024 / 1024 + " MB）"));
                    }
                    if (acc.totalSize == 0) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "文件不能为空"));
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
        List<DataBuffer> buffers = new ArrayList<>();
    }

    /**
     * 获取文件 URL
     *
     * <p>
     * 根据已授权的存储桶名称和文件名获取访问地址。
     * 云存储返回临时预签名 URL；Local 返回每次访问仍需携带认证凭证的内容地址，不适用签名 TTL。
     *
     * @param request 服务器请求对象，包含 {@code bucketName} 和 {@code fileName} 查询参数
     * @return 包含文件访问 URL 的响应对象
     */
    @Operation(
            operationId = "getFileUrl",
            summary = "获取文件URL",
            description = "云存储返回临时预签名 URL；Local 返回需携带认证凭证的内容地址，不适用签名 TTL")
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
                description = "获取成功，返回云预签名 URL 或需认证授权的 Local 内容地址",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileUrlResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，bucketName或fileName为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.SELECT,
            description = "getUrl",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getUrl(ServerRequest request) {
        String bucketName = request.queryParam("bucketName").orElse(null);
        String fileName = request.queryParam("fileName").orElse(null);
        if (isFileInfoMissing(bucketName, fileName)) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "存储桶名称和文件名称不能为空");
        }
        return fileService.getUrl(bucketName, fileName).flatMap(requestHandlerUtil::buildSuccessResponse);
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
                description = "返回删除操作的布尔结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileBooleanResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，bucketName或fileName为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.DELETE,
            description = "delete",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> delete(ServerRequest request) {
        String bucketName = request.queryParam("bucketName").orElse(null);
        String fileName = request.queryParam("fileName").orElse(null);
        if (isFileInfoMissing(bucketName, fileName)) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "存储桶名称和文件名称不能为空");
        }
        return fileService.delete(bucketName, fileName).flatMap(requestHandlerUtil::buildSuccessResponse);
    }

    /**
     * 重试所有者的孤儿对象清理，失败时保留清理记录。
     * @param request 包含清理记录编号的请求
     * @return 本次对象清理结果
     */
    @Operation(operationId = "retryOrphanCleanup", summary = "重试孤儿对象清理", description = "仅文件所有者可重试孤儿对象清理，失败时保留元数据")
    @Parameter(
            name = "fileId",
            description = "清理记录编号",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "对象清理的布尔结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileBooleanResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数或清理失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.OTHER,
            description = "retryOrphanCleanup",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> retryOrphanCleanup(ServerRequest request) {
        return Mono.fromCallable(() -> Long.parseLong(request.pathVariable("fileId")))
                .flatMap(fileService::retryOrphanCleanup)
                .flatMap(requestHandlerUtil::buildSuccessResponse)
                .onErrorResume(requestHandlerUtil::handleError);
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
    @Operation(operationId = "fileExists", summary = "检查文件是否存在", description = "查询指定文件是否已存在于对象存储系统中，返回布尔值表示文件是否存在")
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
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileBooleanResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，bucketName或fileName为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.SELECT,
            description = "exists",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> exists(ServerRequest request) {
        String bucketName = request.queryParam("bucketName").orElse(null);
        String fileName = request.queryParam("fileName").orElse(null);
        if (isFileInfoMissing(bucketName, fileName)) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "存储桶名称和文件名称不能为空");
        }
        return fileService.exists(bucketName, fileName).flatMap(requestHandlerUtil::buildSuccessResponse);
    }

    /**
     * 读取经过文件归属授权的文件内容。
     *
     * <p>未指定桶名时使用配置默认桶；指定桶名仅用于定位历史文件，
     * 下载前仍校验持久化记录中的租户与用户归属。</p>
     *
     * @param request 包含 fileName 及可选 bucketName 查询参数的服务器请求
     * @return 流式文件响应
     */
    @Operation(
            operationId = "getLocalFileContent",
            summary = "读取本地存储文件",
            description = "按持久化文件归属授权后流式读取文件内容，未指定桶名时使用默认桶")
    @Parameters({
        @Parameter(
                name = "bucketName",
                description = "历史文件的存储桶名称，省略时使用默认桶",
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
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
                content =
                        @Content(
                                mediaType = "application/octet-stream",
                                schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，fileName缺失或为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.DOWNLOAD,
            description = "content",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> content(ServerRequest request) {
        String fileName = request.queryParam("fileName").orElse(null);
        if (fileName == null || fileName.isBlank()) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "文件名称不能为空");
        }
        String bucketName = request.queryParam("bucketName").orElse(defaultBucketName);
        return fileService.download(bucketName, fileName).flatMap(inputStream -> {
            Flux<DataBuffer> content = DataBufferUtils.readInputStream(
                            () -> inputStream, DefaultDataBufferFactory.sharedInstance, 8192)
                    .subscribeOn(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.storage())
                    .doFinally(signal -> {
                        try {
                            inputStream.close();
                        } catch (IOException exception) {
                            log.warn("关闭本地文件读取流失败, fileName: {}", fileName, exception);
                        }
                    });
            return ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(content, DataBuffer.class);
        });
    }

    /**
     * 预览默认存储桶中的文件内容。
     *
     * <p>与 {@link #content(ServerRequest)} 相同，均以流式方式读取文件内容，区别在于
     * 预览端点会以 {@code Content-Disposition: inline} 返回并尽量解析正确的媒体类型，
     * 使浏览器可以直接内联渲染（如图片、PDF）而非触发下载。物理桶名由服务端配置固定，
     * 客户端只能提交上传结果中的 UUID 对象名，不能跨桶读取。</p>
     *
     * @param request 包含 fileName、可选 contentType 查询参数的服务器请求
     * @return 流式文件预览响应
     */
    @Operation(
            operationId = "previewFile",
            summary = "预览文件",
            description = "仅用于 local 存储模式，按服务端固定桶流式读取文件内容并以 inline 方式返回，供浏览器直接预览")
    @Parameters({
        @Parameter(
                name = "fileName",
                description = "上传后返回的对象名称（UUID格式）",
                required = true,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string")),
        @Parameter(
                name = "contentType",
                description = "可选，显式指定媒体类型；缺省时按文件扩展名推断",
                required = false,
                in = ParameterIn.QUERY,
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "预览成功，媒体类型由 contentType 或扩展名确定，Content-Disposition 为 inline",
                content = @Content(mediaType = "*/*", schema = @Schema(type = "string", format = "binary"))),
        @ApiResponse(
                responseCode = "400",
                description = "参数错误，fileName缺失或为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = FileErrorResponse.class)))
    })
    @OperationLog(
            module = "File",
            type = OperationLog.OperationType.DOWNLOAD,
            description = "preview",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> preview(ServerRequest request) {
        String fileName = request.queryParam("fileName").orElse(null);
        if (fileName == null || fileName.isBlank()) {
            return requestHandlerUtil.buildErrorResponse(ResultCode.PARAM_ERROR, "文件名称不能为空");
        }
        String contentType = request.queryParam("contentType")
                .filter(value -> !value.isBlank())
                .orElseGet(() -> resolveContentType(fileName));
        return fileService.download(defaultBucketName, fileName).flatMap(inputStream -> {
            Flux<DataBuffer> content = DataBufferUtils.readInputStream(
                            () -> inputStream, DefaultDataBufferFactory.sharedInstance, 8192)
                    .subscribeOn(com.apex.scaffold.common.scheduler.BlockingIoSchedulers.storage())
                    .doFinally(signal -> {
                        try {
                            inputStream.close();
                        } catch (IOException exception) {
                            log.warn("关闭预览文件读取流失败, fileName: {}", fileName, exception);
                        }
                    });
            return ServerResponse.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.inline()
                                    .filename(fileName)
                                    .build()
                                    .toString())
                    .body(content, DataBuffer.class);
        });
    }

    /**
     * 根据文件扩展名推断媒体类型。
     *
     * <p>用于预览端点在不显式指定 {@code contentType} 时确定响应媒体类型，仅覆盖常见
     * 可预览格式，未识别时回退为 {@code application/octet-stream}。</p>
     *
     * @param fileName 文件名或对象名
     * @return 推断出的媒体类型字符串
     */
    private String resolveContentType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(EXT_PNG)) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        if (lower.endsWith(EXT_JPG) || lower.endsWith(EXT_JPEG)) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (lower.endsWith(EXT_GIF)) {
            return MediaType.IMAGE_GIF_VALUE;
        }
        if (lower.endsWith(EXT_WEBP)) {
            return "image/webp";
        }
        if (lower.endsWith(EXT_BMP)) {
            return "image/bmp";
        }
        if (lower.endsWith(EXT_SVG)) {
            return "image/svg+xml";
        }
        if (lower.endsWith(EXT_PDF)) {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        if (lower.endsWith(EXT_TXT)) {
            return MediaType.TEXT_PLAIN_VALUE;
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    /** 上传结果统一响应的文档模型。 */
    @Schema(name = "FileUploadResponse")
    public static class FileUploadResponse extends Result<FileUploadResult> {}

    /** 文件地址统一响应的文档模型。 */
    @Schema(name = "FileUrlResponse")
    public static class FileUrlResponse extends Result<String> {}

    /** 文件布尔结果统一响应的文档模型。 */
    @Schema(name = "FileBooleanResponse")
    public static class FileBooleanResponse extends Result<Boolean> {}

    /** 文件错误统一响应的文档模型。 */
    @Schema(name = "FileErrorResponse")
    public static class FileErrorResponse extends Result<Void> {}
}
