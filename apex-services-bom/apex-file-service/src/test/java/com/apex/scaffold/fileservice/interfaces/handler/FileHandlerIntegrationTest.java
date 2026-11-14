package com.apex.scaffold.fileservice.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.file.application.dto.FileUploadResult;
import com.apex.scaffold.file.application.dto.PageResponse;
import com.apex.scaffold.file.application.dto.chunk.ChunkInitRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkInitResult;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkMergeResult;
import com.apex.scaffold.file.application.dto.chunk.ChunkUploadRequest;
import com.apex.scaffold.file.application.dto.chunk.ChunkUploadResult;
import com.apex.scaffold.file.application.dto.data.ChartDataResponse;
import com.apex.scaffold.file.application.dto.data.ChartQueryRequest;
import com.apex.scaffold.file.application.dto.data.ChartRequest;
import com.apex.scaffold.file.application.dto.data.ChartResponse;
import com.apex.scaffold.file.application.dto.data.DocumentQueryRequest;
import com.apex.scaffold.file.application.dto.data.DocumentRequest;
import com.apex.scaffold.file.application.dto.data.DocumentResponse;
import com.apex.scaffold.file.application.dto.share.FileShareContent;
import com.apex.scaffold.file.application.dto.share.FileShareCreated;
import com.apex.scaffold.file.application.dto.share.FileShareExchangeResult;
import com.apex.scaffold.file.application.service.ChartService;
import com.apex.scaffold.file.application.service.ChunkUploadService;
import com.apex.scaffold.file.application.service.DocumentService;
import com.apex.scaffold.file.application.service.FileService;
import com.apex.scaffold.file.application.service.FileShareService;
import com.apex.scaffold.fileservice.FileServiceSliceTestConfiguration;
import com.apex.scaffold.fileservice.interfaces.router.ChartRouter;
import com.apex.scaffold.fileservice.interfaces.router.ChunkRouter;
import com.apex.scaffold.fileservice.interfaces.router.DocumentRouter;
import com.apex.scaffold.fileservice.interfaces.router.FileRouter;
import com.apex.scaffold.fileservice.interfaces.router.FileShareRouter;
import com.apex.scaffold.fileservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.fileservice.interfaces.util.RequestHandlerUtil;
import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 文件服务 Handler 集成测试。
 *
 * <p>在 {@link WebFluxTest} 切片上下文中加载文件服务的 5 个 Handler 与 5 个 Router，
 * 通过 {@code WebTestClient} 以真实 HTTP 语义驱动全部函数式端点，覆盖迁移方案
 * 「文件域接口集成测试」要求：文件元数据、分片上传、图表与文档管理的完整链路，
 * 以及参数校验、未认证与服务异常分支。
 *
 * <h2>覆盖范围</h2>
 * <ul>
 *   <li>文件元数据：GET /api/v1/file/{url,content,preview,exists}、DELETE /api/v1/file/delete
 *       以及上传入口的参数错误分支</li>
 *   <li>分片上传：POST /api/v1/file/chunk/{init,upload,merge}、GET /api/v1/file/chunk/status/{fileId}</li>
 *   <li>图表管理：/admin-api/v1/charts 的 CRUD、状态变更与数据查询</li>
 *   <li>文档管理：/admin-api/v1/documents 的 CRUD、状态变更与下载</li>
 *   <li>文件分享：POST /api/v2/files/{fileId}/shares、DELETE /api/v2/file-shares/{shareId}、
 *       匿名兑换与下载 /api/v2/file-shares/{exchange,download}</li>
 * </ul>
 *
 * <p>说明：File/Chunk Handler 使用 {@link RequestHandlerUtil} 构建统一响应，故对
 * {@code buildSuccessResponse}/{@code buildErrorResponse}/{@code handleError}/{@code handleRequestBody}
 * 进行 mock；Chart/Document Handler 直接通过 {@link ServerResponse} 输出 {@link Result}，
 * 仅需 mock {@code handleRequestBody}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@WebFluxTest
@Import({
    org.springdoc.core.configuration.SpringDocConfiguration.class,
    org.springdoc.webflux.core.configuration.SpringDocWebFluxConfiguration.class,
    org.springdoc.core.properties.SpringDocConfigProperties.class,
    FileHandler.class,
    FileRouter.class,
    ChunkHandler.class,
    ChunkRouter.class,
    ChartHandler.class,
    ChartRouter.class,
    DocumentHandler.class,
    DocumentRouter.class,
    FileShareHandler.class,
    FileShareRouter.class,
    FileServiceSliceTestConfiguration.class,
    com.apex.scaffold.fileservice.config.FileSecurityConfig.class
})
@DisplayName("文件服务Handler集成测试")
class FileHandlerIntegrationTest {

    @Test
    void exposesTypedFileOpenApi() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.paths.*.*.operationId")
                .value(value -> org.assertj.core.api.Assertions.assertThat((List<?>) value)
                        .hasSize(32)
                        .doesNotHaveDuplicates())
                .jsonPath("$.paths['/admin-api/v1/documents'].get.operationId")
                .isEqualTo("queryDocuments")
                .jsonPath("$.paths['/admin-api/v1/charts/{id}/data'].get.operationId")
                .isEqualTo("getChartData")
                .jsonPath(
                        "$.paths['/api/v1/file/chunk/upload'].post.requestBody.content['application/octet-stream'].schema.format")
                .isEqualTo("binary")
                .jsonPath(
                        "$.paths['/api/v1/file/content'].get.responses['200'].content['application/octet-stream'].schema.format")
                .isEqualTo("binary")
                .jsonPath("$.paths['/api/v1/file/preview'].get.responses['200'].content['*/*'].schema.format")
                .isEqualTo("binary")
                .jsonPath(
                        "$.paths['/api/v1/files/generic'].post.requestBody.content['multipart/form-data'].schema['$ref']")
                .isEqualTo("#/components/schemas/GenericFileUploadRequest")
                .jsonPath("$.components.schemas.FileUploadResponse.properties.data['$ref']")
                .isEqualTo("#/components/schemas/FileUploadResult")
                .jsonPath("$.components.schemas.FileBooleanResponse.properties.data.type")
                .isEqualTo("boolean")
                .jsonPath("$.components.schemas.DocumentUrlResult.properties.data.type")
                .isEqualTo("string")
                .jsonPath("$.components.schemas.DocumentPageResult.properties.data['$ref']")
                .exists();
    }

    private static final long USER_ID = 100L;

    @Resource
    private WebTestClient webTestClient;

    private WebTestClient client;

    @MockBean
    private FileService fileService;

    @MockBean
    private ChunkUploadService chunkUploadService;

    @MockBean
    private ChartService chartService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private FileShareService fileShareService;

    @MockBean
    private RequestHandlerUtil requestHandlerUtil;

    @BeforeEach
    void setUp() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(USER_ID, "openid-1", "default");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(authenticatedUser, "", List.of());
        client = webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(authentication));

        // 请求体解析：直接透传真实 ServerRequest 的 bodyToMono，跳过 Bean Validation。
        when(requestHandlerUtil.handleRequestBody(any(), any())).thenAnswer(invocation -> {
            ServerRequest request = invocation.getArgument(0);
            Class<?> clazz = invocation.getArgument(1);
            return request.bodyToMono(clazz);
        });

        // 成功响应：HTTP 200 + Result.success(data)。
        when(requestHandlerUtil.buildSuccessResponse(any())).thenAnswer(invocation -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(invocation.getArgument(0))));

        // 错误响应（int code）：HTTP 400 + Result.error(code, message)。
        when(requestHandlerUtil.buildErrorResponse(anyInt(), anyString()))
                .thenAnswer(invocation -> ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(
                                Result.error((Integer) invocation.getArgument(0), (String) invocation.getArgument(1))));

        // 错误响应（ResultCode）：HTTP 400 + Result.error(code, message)。
        when(requestHandlerUtil.buildErrorResponse(any(ResultCode.class), anyString()))
                .thenAnswer(invocation -> {
                    ResultCode resultCode = invocation.getArgument(0);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.error(resultCode.getCode(), (String) invocation.getArgument(1)));
                });

        // 统一错误处理：HTTP 400。
        when(requestHandlerUtil.handleError(any(Throwable.class)))
                .thenReturn(ServerResponse.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.error("error")));
    }

    /** 清理入口仅接收记录编号，实际对象定位由应用服务授权后恢复。 */
    @Test
    void cleanupRetryRoutes() {
        when(chunkUploadService.retryCleanup(1L)).thenReturn(Mono.empty());
        when(fileService.retryOrphanCleanup(2L)).thenReturn(Mono.just(false));
        client.post()
                .uri("/api/v1/file/chunk/cleanup/1/retry")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        client.post()
                .uri("/api/v1/file/orphan/2/retry")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data")
                .isEqualTo(false);
        org.mockito.Mockito.verify(chunkUploadService).retryCleanup(1L);
        org.mockito.Mockito.verify(fileService).retryOrphanCleanup(2L);
    }

    /** 未认证请求不能触发清理服务。 */
    @Test
    void cleanupRetryRequiresAuthentication() {
        webTestClient
                .post()
                .uri("/api/v1/file/chunk/cleanup/1/retry")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        webTestClient
                .post()
                .uri("/api/v1/file/orphan/2/retry")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        org.mockito.Mockito.verifyNoInteractions(chunkUploadService, fileService);
    }

    // ===================== 文件元数据 =====================

    @Test
    @DisplayName("获取文件URL - 成功")
    void getUrl_Success() {
        when(fileService.getUrl("apex", "test.png")).thenReturn(Mono.just("https://cdn/test.png"));

        client.get()
                .uri("/api/v1/file/url?bucketName=apex&fileName=test.png")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isEqualTo("https://cdn/test.png");
    }

    @Test
    @DisplayName("获取文件URL - 缺少参数返回400")
    void getUrl_MissingParams() {
        client.get()
                .uri("/api/v1/file/url")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("存储桶名称和文件名称不能为空");
    }

    @Test
    @DisplayName("删除文件 - 成功")
    void deleteFile_Success() {
        when(fileService.delete("apex", "test.png")).thenReturn(Mono.just(true));

        client.delete()
                .uri("/api/v1/file/delete?bucketName=apex&fileName=test.png")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("删除文件 - 缺少参数返回400")
    void deleteFile_MissingParams() {
        client.delete()
                .uri("/api/v1/file/delete")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @DisplayName("检查文件是否存在 - 成功")
    void fileExists_Success() {
        when(fileService.exists("apex", "test.png")).thenReturn(Mono.just(true));

        client.get()
                .uri("/api/v1/file/exists?bucketName=apex&fileName=test.png")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("检查文件是否存在 - 缺少参数返回400")
    void fileExists_MissingParams() {
        client.get()
                .uri("/api/v1/file/exists")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @DisplayName("读取本地存储文件内容 - 成功返回流")
    void content_Success() {
        byte[] payload = "hello-file".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/content?fileName=test.png")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .expectBody(byte[].class)
                .isEqualTo(payload);
    }

    /** 下载地址中的历史桶名必须传递至归属授权服务。 */
    @Test
    void content_UsesRecordedBucket() {
        byte[] payload = "historical-file".getBytes(StandardCharsets.UTF_8);
        when(fileService.download("old-bucket", "test.png"))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));
        client.get()
                .uri("/api/v1/file/content?bucketName=old-bucket&fileName=test.png")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(byte[].class)
                .isEqualTo(payload);
        org.mockito.Mockito.verify(fileService).download("old-bucket", "test.png");
    }

    @Test
    @DisplayName("读取本地存储文件内容 - 缺少fileName返回400")
    void content_MissingFileName() {
        client.get()
                .uri("/api/v1/file/content")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("文件名称不能为空");
    }

    @Test
    @DisplayName("预览文件 - 成功返回流并推断媒体类型")
    void preview_Success() {
        byte[] payload = "preview-data".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/preview?fileName=photo.png")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_PNG)
                .expectBody(byte[].class)
                .isEqualTo(payload);
    }

    @Test
    @DisplayName("上传图片 - 非multipart请求返回400")
    void upload_NotMultipart() {
        client.post()
                .uri("/api/v1/file/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("multipart 请求 Content-Type 无效");
    }

    @Test
    @DisplayName("上传普通图片 - 缺少boundary返回400")
    void uploadGenericFile_MissingBoundary() {
        client.post()
                .uri("/api/v1/files/generic")
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("multipart 请求解析失败");
    }

    @Test
    @DisplayName("预览文件 - 缺少fileName返回400")
    void preview_MissingFileName() {
        client.get()
                .uri("/api/v1/file/preview")
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("文件名称不能为空");
    }

    @Test
    @DisplayName("预览文件 - 显式指定contentType")
    void preview_WithExplicitContentType() {
        byte[] payload = "preview".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/preview?fileName=photo.bin&contentType=image/png")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_PNG);
    }

    @Test
    @DisplayName("预览文件 - jpg扩展名推断image/jpeg")
    void preview_JpgExtension() {
        byte[] payload = "jpg".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/preview?fileName=photo.jpg")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_JPEG);
    }

    @Test
    @DisplayName("预览文件 - gif扩展名推断image/gif")
    void preview_GifExtension() {
        byte[] payload = "gif".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/preview?fileName=anim.gif")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.IMAGE_GIF);
    }

    @Test
    @DisplayName("预览文件 - pdf扩展名推断application/pdf")
    void preview_PdfExtension() {
        byte[] payload = "pdf".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/preview?fileName=doc.pdf")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_PDF);
    }

    @Test
    @DisplayName("预览文件 - txt扩展名推断text/plain")
    void preview_TxtExtension() {
        byte[] payload = "txt".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/preview?fileName=note.txt")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.TEXT_PLAIN);
    }

    @Test
    @DisplayName("预览文件 - 未知扩展名回退octet-stream")
    void preview_UnknownExtension() {
        byte[] payload = "raw".getBytes(StandardCharsets.UTF_8);
        when(fileService.download(anyString(), anyString()))
                .thenReturn(Mono.just((InputStream) new ByteArrayInputStream(payload)));

        client.get()
                .uri("/api/v1/file/preview?fileName=data.xyz")
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
    }

    @Test
    @DisplayName("上传普通图片 - sex=1成功")
    void uploadGenericFile_Success_Sex1() {
        FileUploadResult result = new FileUploadResult("apex", "uuid.png", "https://cdn/uuid.png", 300, null);
        when(fileService.upload(anyString(), any(byte[].class), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(result));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("hello".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "test.png";
            }
        });
        builder.part("sex", "1");

        client.post()
                .uri("/api/v1/files/generic")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.fileName")
                .isEqualTo("uuid.png");
    }

    @Test
    @DisplayName("上传普通图片 - sex=2成功")
    void uploadGenericFile_Success_Sex2() {
        FileUploadResult result = new FileUploadResult("apex", "uuid2.png", "https://cdn/uuid2.png", 300, null);
        when(fileService.upload(anyString(), any(byte[].class), anyString(), anyInt(), anyString()))
                .thenReturn(Mono.just(result));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("data".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "pic.png";
            }
        });
        builder.part("sex", "2");

        client.post()
                .uri("/api/v1/files/generic")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.bucketName")
                .isEqualTo("apex");
    }

    @Test
    @DisplayName("上传普通图片 - sex非法返回400")
    void uploadGenericFile_InvalidSex() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("data".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "pic.png";
            }
        });
        builder.part("sex", "9");

        client.post()
                .uri("/api/v1/files/generic")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("sex 参数不能为空");
    }

    @Test
    @DisplayName("上传普通图片 - 缺少sex返回400")
    void uploadGenericFile_MissingSex() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("data".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "pic.png";
            }
        });

        client.post()
                .uri("/api/v1/files/generic")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("sex 参数不能为空");
    }

    @Test
    @DisplayName("上传图片(兼容入口) - 成功")
    void upload_Success() {
        FileUploadResult result = new FileUploadResult("apex", "old.png", "https://cdn/old.png", 300, null);
        when(fileService.upload(anyString(), any(byte[].class), anyString(), any(), anyString()))
                .thenReturn(Mono.just(result));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("compat".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "old.png";
            }
        });

        client.post()
                .uri("/api/v1/file/upload")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.fileName")
                .isEqualTo("old.png");
    }

    // ===================== 分片上传 =====================

    @Test
    @DisplayName("初始化分片上传 - 成功")
    void initChunk_Success() {
        ChunkInitResult result = new ChunkInitResult(9001L, "apex", 5242880L, 20, List.of());
        when(chunkUploadService.init(any(ChunkInitRequest.class))).thenReturn(Mono.just(result));

        client.post()
                .uri("/api/v1/file/chunk/init")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("fileName", "report.pdf", "fileSize", 104857600, "totalChunks", 20))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.fileId")
                .isEqualTo(9001)
                .jsonPath("$.data.bucketName")
                .isEqualTo("apex")
                .jsonPath("$.data.totalChunks")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("上传分片 - 成功")
    void uploadChunk_Success() {
        ChunkUploadResult result = new ChunkUploadResult(9001L, 0, true, "chunk-0", 5242880L);
        when(chunkUploadService.uploadChunk(any(ChunkUploadRequest.class), any(byte[].class)))
                .thenReturn(Mono.just(result));

        client.post()
                .uri("/api/v1/file/chunk/upload?fileId=9001&chunkIndex=0&totalChunks=20&md5=abc")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("chunk-bytes".getBytes(StandardCharsets.UTF_8))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.chunkIndex")
                .isEqualTo(0)
                .jsonPath("$.data.uploaded")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("上传分片 - 缺少fileId与chunkIndex返回400")
    void uploadChunk_MissingParams() {
        client.post()
                .uri("/api/v1/file/chunk/upload")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("chunk-bytes".getBytes(StandardCharsets.UTF_8))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("fileId 与 chunkIndex 不能为空");
    }

    @Test
    @DisplayName("合并分片 - 成功")
    void mergeChunks_Success() {
        ChunkMergeResult result =
                new ChunkMergeResult(9001L, "apex", "merged.pdf", "https://cdn/merged.pdf", 104857600L, "md5");
        when(chunkUploadService.merge(any(ChunkMergeRequest.class))).thenReturn(Mono.just(result));

        client.post()
                .uri("/api/v1/file/chunk/merge")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("fileId", 9001))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.fileId")
                .isEqualTo(9001)
                .jsonPath("$.data.fileName")
                .isEqualTo("merged.pdf");
    }

    @Test
    @DisplayName("查询分片上传进度 - 成功")
    void getChunkStatus_Success() {
        ChunkInitResult result = new ChunkInitResult(9001L, "apex", 5242880L, 20, List.of(0, 1, 2));
        when(chunkUploadService.status(9001L)).thenReturn(Mono.just(result));

        client.get()
                .uri("/api/v1/file/chunk/status/9001")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.uploadedChunks.length()")
                .isEqualTo(3);
    }

    // ===================== 图表管理 =====================

    @Test
    @DisplayName("创建图表 - 成功")
    void createChart_Success() {
        ChartResponse response = ChartResponse.builder()
                .id(1L)
                .chartName("月度销售")
                .chartType("line")
                .build();
        when(chartService.create(any(ChartRequest.class))).thenReturn(Mono.just(response));

        client.post()
                .uri("/admin-api/v1/charts")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("chartName", "月度销售", "chartType", "line", "category", "business"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(1)
                .jsonPath("$.data.chartName")
                .isEqualTo("月度销售");
    }

    @Test
    @DisplayName("更新图表 - 成功")
    void updateChart_Success() {
        ChartResponse response = ChartResponse.builder().id(1L).chartName("更新后").build();
        when(chartService.update(anyLong(), any(ChartRequest.class))).thenReturn(Mono.just(response));

        client.put()
                .uri("/admin-api/v1/charts/1")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("chartName", "更新后", "chartType", "bar", "category", "tech"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.chartName")
                .isEqualTo("更新后");
    }

    @Test
    @DisplayName("删除图表 - 成功")
    void deleteChart_Success() {
        when(chartService.delete(1L)).thenReturn(Mono.empty());

        client.delete()
                .uri("/admin-api/v1/charts/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("查询图表详情 - 成功")
    void getChartById_Success() {
        ChartResponse response =
                ChartResponse.builder().id(1L).chartName("详情").chartType("pie").build();
        when(chartService.getById(1L)).thenReturn(Mono.just(response));

        client.get()
                .uri("/admin-api/v1/charts/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.chartType")
                .isEqualTo("pie");
    }

    @Test
    @DisplayName("查询图表数据 - 成功")
    void getChartData_Success() {
        ChartDataResponse response = ChartDataResponse.builder()
                .chartId("1")
                .chartType("line")
                .title("趋势")
                .xAxis(List.of("1月", "2月"))
                .build();
        when(chartService.incrementViewCount(1L)).thenReturn(Mono.empty());
        when(chartService.getChartData(1L)).thenReturn(Mono.just(response));

        client.get()
                .uri("/admin-api/v1/charts/1/data")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.title")
                .isEqualTo("趋势")
                .jsonPath("$.data.xaxis.length()")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("分页查询图表列表 - 成功")
    void queryCharts_Success() {
        ChartResponse item = ChartResponse.builder().id(1L).chartName("列表项").build();
        PageResponse<ChartResponse> page = PageResponse.of(List.of(item), 1L, 1, 20);
        when(chartService.query(any(ChartQueryRequest.class))).thenReturn(Mono.just(page));

        client.get()
                .uri("/admin-api/v1/charts?page=1&size=20")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.total")
                .isEqualTo(1)
                .jsonPath("$.data.list[0].id")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("更新图表状态 - 成功")
    void updateChartStatus_Success() {
        when(chartService.updateStatus(1L, 0)).thenReturn(Mono.empty());

        client.put()
                .uri("/admin-api/v1/charts/1/status")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", 0))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    // ===================== 文档管理 =====================

    @Test
    @DisplayName("创建文档 - 成功")
    void createDocument_Success() {
        DocumentResponse response = DocumentResponse.builder()
                .id(1L)
                .title("财务报告")
                .docType("report")
                .build();
        when(documentService.create(any(DocumentRequest.class))).thenReturn(Mono.just(response));

        client.post()
                .uri("/admin-api/v1/documents")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "财务报告", "docType", "report", "category", "finance"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.title")
                .isEqualTo("财务报告");
    }

    @Test
    @DisplayName("更新文档 - 成功")
    void updateDocument_Success() {
        DocumentResponse response =
                DocumentResponse.builder().id(1L).title("更新后").build();
        when(documentService.update(anyLong(), any(DocumentRequest.class))).thenReturn(Mono.just(response));

        client.put()
                .uri("/admin-api/v1/documents/1")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("title", "更新后", "docType", "report", "category", "finance"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.title")
                .isEqualTo("更新后");
    }

    @Test
    @DisplayName("删除文档 - 成功")
    void deleteDocument_Success() {
        when(documentService.delete(1L)).thenReturn(Mono.empty());

        client.delete()
                .uri("/admin-api/v1/documents/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("查询文档详情 - 成功")
    void getDocumentById_Success() {
        DocumentResponse response =
                DocumentResponse.builder().id(1L).title("详情").build();
        when(documentService.incrementViewCount(1L)).thenReturn(Mono.empty());
        when(documentService.getById(1L)).thenReturn(Mono.just(response));

        client.get()
                .uri("/admin-api/v1/documents/1")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.title")
                .isEqualTo("详情");
    }

    @Test
    @DisplayName("分页查询文档列表 - 成功")
    void queryDocuments_Success() {
        DocumentResponse item = DocumentResponse.builder().id(1L).title("列表项").build();
        PageResponse<DocumentResponse> page = PageResponse.of(List.of(item), 1L, 1, 20);
        when(documentService.query(any(DocumentQueryRequest.class))).thenReturn(Mono.just(page));

        client.get()
                .uri("/admin-api/v1/documents?page=1&size=20")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.total")
                .isEqualTo(1)
                .jsonPath("$.data.list[0].id")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("更新文档状态 - 成功")
    void updateDocumentStatus_Success() {
        when(documentService.updateStatus(1L, 2)).thenReturn(Mono.empty());

        client.put()
                .uri("/admin-api/v1/documents/1/status")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", 2))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("下载文档 - 有附件返回URL")
    void downloadDocument_HasAttachment() {
        DocumentResponse response =
                DocumentResponse.builder().id(1L).fileUrl("https://cdn/doc.pdf").build();
        when(documentService.incrementDownloadCount(1L)).thenReturn(Mono.empty());
        when(documentService.getById(1L)).thenReturn(Mono.just(response));

        client.get()
                .uri("/admin-api/v1/documents/1/download")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data")
                .isEqualTo("https://cdn/doc.pdf");
    }

    @Test
    @DisplayName("下载文档 - 无附件返回400")
    void downloadDocument_NoAttachment() {
        DocumentResponse response =
                DocumentResponse.builder().id(1L).fileUrl(null).build();
        when(documentService.incrementDownloadCount(1L)).thenReturn(Mono.empty());
        when(documentService.getById(1L)).thenReturn(Mono.just(response));

        client.get()
                .uri("/admin-api/v1/documents/1/download")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.msg")
                .isEqualTo("文档无附件");
    }

    // ===================== 认证与异常 =====================

    @Test
    @DisplayName("未认证访问受保护接口 - 返回401")
    void protectedEndpoint_Unauthenticated() {
        webTestClient
                .get()
                .uri("/api/v1/file/exists?bucketName=apex&fileName=test.png")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("获取文件URL - 服务异常返回5xx")
    void getUrl_ServiceError() {
        when(fileService.getUrl(anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.get()
                .uri("/api/v1/file/url?bucketName=apex&fileName=test.png")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("初始化分片上传 - 服务异常由handleError降级为400")
    void initChunk_ServiceError() {
        when(chunkUploadService.init(any(ChunkInitRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.post()
                .uri("/api/v1/file/chunk/init")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("fileName", "report.pdf", "fileSize", 100, "totalChunks", 1))
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @DisplayName("创建图表 - 服务异常返回5xx")
    void createChart_ServiceError() {
        when(chartService.create(any(ChartRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.post()
                .uri("/admin-api/v1/charts")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("chartName", "x", "chartType", "line", "category", "b"))
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("创建文件分享 - 返回明文令牌与失效时间")
    void createFileShare() {
        when(fileShareService.create(7L, 3600))
                .thenReturn(Mono.just(new FileShareCreated(11L, 7L, "share-token", null)));

        client.post()
                .uri("/api/v2/files/7/shares")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("expireSeconds", 3600))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.shareId")
                .isEqualTo(11)
                .jsonPath("$.data.token")
                .isEqualTo("share-token");
    }

    @Test
    @DisplayName("撤销文件分享 - 重复撤销视为成功")
    void revokeFileShare() {
        when(fileShareService.revoke(11L)).thenReturn(Mono.empty());

        client.delete()
                .uri("/api/v2/file-shares/11")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
        org.mockito.Mockito.verify(fileShareService).revoke(11L);
    }

    @Test
    @DisplayName("匿名兑换分享 - 无需认证即可访问")
    void exchangeFileShare_Anonymous() {
        when(fileShareService.exchange("share-token"))
                .thenReturn(
                        Mono.just(new FileShareExchangeResult(11L, 7L, "report.pdf", "application/pdf", 100L, null)));

        webTestClient
                .post()
                .uri("/api/v2/file-shares/exchange")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", "share-token"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.originalName")
                .isEqualTo("report.pdf");
    }

    @Test
    @DisplayName("匿名下载分享 - 代理输出文件流")
    void downloadFileShare_Anonymous() {
        when(fileShareService.download("share-token"))
                .thenReturn(Mono.just(new FileShareContent(
                        "report.pdf",
                        "application/pdf",
                        5L,
                        new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))));

        webTestClient
                .post()
                .uri("/api/v2/file-shares/download")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("token", "share-token"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType("application/pdf")
                .expectBody(String.class)
                .isEqualTo("hello");
    }
}
