package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.FileUploadResult;
import com.apex.scaffold.application.service.FileService;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.util.MessageUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link FileHandler} 的单元测试类
 *
 * <p>测试文件Handler的核心逻辑：参数解析、文件操作、响应构建。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("FileHandler 单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileHandlerTest {

    @Mock
    private FileService fileService;

    @Mock
    private MessageUtils messageUtils;

    private FileHandler handler;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        handler = new FileHandler(fileService, messageUtils);
        ReflectionTestUtils.setField(handler, "maxFileSize", 1024L);
        ReflectionTestUtils.setField(handler, "uploadEnabled", true);
        ReflectionTestUtils.setField(handler, "defaultBucketName", "apex");
        lenient()
                .when(messageUtils.get(anyString(), any(Object[].class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
    }

    /** 验证本地存储内容读取端点始终使用服务端固定桶。 */
    @Nested
    @DisplayName("content 方法")
    class ContentTests {

        /** 验证文件内容可按上传结果中的对象名流式返回。 */
        @Test
        @DisplayName("应从默认桶读取文件内容")
        void shouldReadContentFromDefaultBucket() {
            byte[] content = "image-content".getBytes(StandardCharsets.UTF_8);
            when(fileService.download("apex", "uuid-photo.jpg"))
                    .thenReturn(Mono.just(new ByteArrayInputStream(content)));

            WebTestClient.bindToRouterFunction(
                            RouterFunctions.route(RequestPredicates.GET("/files/content"), handler::content))
                    .build()
                    .get()
                    .uri("/files/content?fileName=uuid-photo.jpg")
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(byte[].class)
                    .value(actual -> assertThat(actual).isEqualTo(content));

            verify(fileService).download("apex", "uuid-photo.jpg");
        }
    }

    @Nested
    @DisplayName("upload 方法")
    class UploadTests {

        /**
         * 执行 {@code shouldUploadMultipartImageAndReturnUrl} 操作。
         */
        @Test
        @DisplayName("应上传multipart图片并返回临时访问URL")
        void shouldUploadMultipartImageAndReturnUrl() {
            when(fileService.upload(
                            eq("photo.jpg"),
                            any(byte[].class),
                            eq(MediaType.IMAGE_JPEG_VALUE),
                            eq(1),
                            any(String.class)))
                    .thenReturn(Mono.just(new FileUploadResult(
                            "apex", "AM/uuid-photo.jpg", "https://storage.example/AM/uuid-photo.jpg", 300, null)));

            ByteArrayResource image = new ByteArrayResource(new byte[] {1, 2, 3}) {
                /** {@inheritDoc} */
                @Override
                public String getFilename() {
                    return "photo.jpg";
                }
            };
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart.part("file", image).contentType(MediaType.IMAGE_JPEG);
            multipart.part("sex", "1");

            WebTestClient.bindToRouterFunction(
                            RouterFunctions.route(RequestPredicates.POST("/files/upload"), handler::upload))
                    .build()
                    .post()
                    .uri("/files/upload?bucketName=apex-files")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipart.build()))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0)
                    .jsonPath("$.data.bucketName")
                    .isEqualTo("apex")
                    .jsonPath("$.data.fileName")
                    .isEqualTo("AM/uuid-photo.jpg")
                    .jsonPath("$.data.expiresIn")
                    .isEqualTo(300);
        }

        /** 验证生产配置关闭上传时接口明确拒绝请求。 */
        @Test
        @DisplayName("上传功能关闭时应拒绝请求")
        void shouldRejectWhenUploadDisabled() {
            ReflectionTestUtils.setField(handler, "uploadEnabled", false);
            when(messageUtils.get(eq("文件上传功能未启用"), any(Object[].class))).thenReturn(Mono.just("文件上传功能未启用"));

            WebTestClient.bindToRouterFunction(
                            RouterFunctions.route(RequestPredicates.POST("/files/upload"), handler::upload))
                    .build()
                    .post()
                    .uri("/files/upload")
                    .exchange()
                    .expectStatus()
                    .is5xxServerError();
        }

        /**
         * 执行 {@code shouldRejectMultipartWithoutBoundary} 操作。
         */
        @Test
        @DisplayName("缺少boundary时应返回400")
        void shouldRejectMultipartWithoutBoundary() {
            WebTestClient.bindToRouterFunction(
                            RouterFunctions.route(RequestPredicates.POST("/files/upload"), handler::upload))
                    .build()
                    .post()
                    .uri("/files/upload")
                    .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE)
                    .bodyValue("broken-body")
                    .exchange()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(ResultCode.PARAM_ERROR.getCode())
                    .jsonPath("$.msg")
                    .isEqualTo("param.multipart_boundary_missing");
        }

        /**
         * 执行 {@code shouldAcceptUploadWithoutSex} 操作。
         */
        @Test
        @DisplayName("缺少sex时应由后端自动检测并上传成功")
        void shouldAcceptUploadWithoutSex() {
            when(fileService.upload(
                            eq("photo.jpg"),
                            any(byte[].class),
                            eq(MediaType.IMAGE_JPEG_VALUE),
                            eq(null),
                            any(String.class)))
                    .thenReturn(Mono.just(new FileUploadResult(
                            "apex", "AM/uuid-photo.jpg", "https://storage.example/AM/uuid-photo.jpg", 300, null)));

            ByteArrayResource image = new ByteArrayResource(new byte[] {1, 2, 3}) {
                /** {@inheritDoc} */
                @Override
                public String getFilename() {
                    return "photo.jpg";
                }
            };
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            multipart.part("file", image).contentType(MediaType.IMAGE_JPEG);

            WebTestClient.bindToRouterFunction(
                            RouterFunctions.route(RequestPredicates.POST("/files/upload"), handler::upload))
                    .build()
                    .post()
                    .uri("/files/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(multipart.build()))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(0)
                    .jsonPath("$.data.fileName")
                    .isEqualTo("AM/uuid-photo.jpg");
        }

        /**
         * 执行 {@code shouldRejectNonMultipartRequest} 操作。
         */
        @Test
        @DisplayName("非multipart请求应返回400")
        void shouldRejectNonMultipartRequest() {
            WebTestClient.bindToRouterFunction(
                            RouterFunctions.route(RequestPredicates.POST("/files/upload"), handler::upload))
                    .build()
                    .post()
                    .uri("/files/upload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"file\":\"abc\"}")
                    .exchange()
                    .expectStatus()
                    .isBadRequest()
                    .expectBody()
                    .jsonPath("$.code")
                    .isEqualTo(ResultCode.PARAM_ERROR.getCode())
                    .jsonPath("$.msg")
                    .isEqualTo("param.multipart_content_type_invalid");
        }
    }

    @Nested
    @DisplayName("getUrl 方法")
    class GetUrlTests {

        /**
         * 执行 {@code shouldGetUrlSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功获取文件URL并返回200")
        void shouldGetUrlSuccessfully() {
            when(fileService.getUrl(anyString(), anyString()))
                    .thenReturn(Mono.just("https://example.com/files/test.jpg"));

            MockServerHttpRequest httpRequest = MockServerHttpRequest.get("/files/url")
                    .queryParam("bucketName", "test-bucket")
                    .queryParam("fileName", "test.jpg")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange, HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.getUrl(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("delete 方法")
    class DeleteTests {

        /**
         * 执行 {@code shouldDeleteSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功删除文件并返回200")
        void shouldDeleteSuccessfully() {
            when(fileService.delete(anyString(), anyString())).thenReturn(Mono.just(true));

            MockServerHttpRequest httpRequest = MockServerHttpRequest.get("/files/delete")
                    .queryParam("bucketName", "test-bucket")
                    .queryParam("fileName", "test.jpg")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange, HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.delete(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("exists 方法")
    class ExistsTests {

        /**
         * 执行 {@code shouldCheckExistsSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功检查文件是否存在并返回200")
        void shouldCheckExistsSuccessfully() {
            when(fileService.exists(anyString(), anyString())).thenReturn(Mono.just(true));

            MockServerHttpRequest httpRequest = MockServerHttpRequest.get("/files/exists")
                    .queryParam("bucketName", "test-bucket")
                    .queryParam("fileName", "test.jpg")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange, HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.exists(request))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();
        }
    }
}
