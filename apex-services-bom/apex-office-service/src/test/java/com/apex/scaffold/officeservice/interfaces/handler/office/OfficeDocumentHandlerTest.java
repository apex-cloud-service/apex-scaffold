package com.apex.scaffold.officeservice.interfaces.handler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.application.OfficeDocumentService;
import com.apex.scaffold.office.domain.document.DocumentInfo;
import com.apex.scaffold.office.domain.document.DocumentType;
import com.apex.scaffold.officeservice.interfaces.assembler.office.DocumentInfoAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.document.CreateDocumentRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.document.DocumentInfoResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.document.UploadFileRequest;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeDocumentHandler 单元测试")
class OfficeDocumentHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OfficeDocumentService documentService;
    private DocumentInfoAssembler assembler;
    private OfficeDocumentHandler handler;

    @BeforeEach
    void setUp() {
        documentService = mock(OfficeDocumentService.class);
        assembler = mock(DocumentInfoAssembler.class);
        handler = new OfficeDocumentHandler(documentService, assembler, requestHandlerUtil);
    }

    private DocumentInfo documentInfo() {
        return DocumentInfo.builder()
                .id(10L)
                .tenantId(1L)
                .platform("feishu")
                .platformDocumentId("doc_1")
                .documentType(DocumentType.DOC)
                .title("标题")
                .ownerId("u1")
                .ownerName("张三")
                .build();
    }

    private DocumentInfoResponse response() {
        return new DocumentInfoResponse();
    }

    @Nested
    @DisplayName("createDocument")
    class CreateDocumentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            CreateDocumentRequest body = CreateDocumentRequest.builder()
                    .platform("feishu")
                    .folderId("folder_1")
                    .title("需求文档")
                    .documentType("DOC")
                    .ownerId("u1")
                    .ownerName("张三")
                    .build();
            when(documentService.createDocument("feishu", "folder_1", "需求文档", DocumentType.DOC, "u1", "张三"))
                    .thenReturn(Mono.just(documentInfo()));
            when(assembler.toResponse(any(DocumentInfo.class))).thenReturn(response());

            StepVerifier.create(
                            handler.createDocument(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务抛出异常时经 handleError 返回 HTTP 400")
        void serviceError() {
            CreateDocumentRequest body = CreateDocumentRequest.builder()
                    .platform("feishu")
                    .folderId("folder_1")
                    .title("需求文档")
                    .documentType("DOC")
                    .ownerId("u1")
                    .ownerName("张三")
                    .build();
            when(documentService.createDocument("feishu", "folder_1", "需求文档", DocumentType.DOC, "u1", "张三"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(
                            handler.createDocument(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法文档类型触发 IllegalArgumentException 映射为 HTTP 400")
        void invalidDocumentType() {
            CreateDocumentRequest body = CreateDocumentRequest.builder()
                    .platform("feishu")
                    .folderId("folder_1")
                    .title("需求文档")
                    .documentType("UNKNOWN")
                    .ownerId("u1")
                    .ownerName("张三")
                    .build();

            StepVerifier.create(
                            handler.createDocument(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("uploadFile")
    class UploadFileTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            UploadFileRequest body = UploadFileRequest.builder()
                    .platform("feishu")
                    .folderId("folder_1")
                    .fileName("a.xlsx")
                    .fileContent("base64")
                    .ownerId("u1")
                    .ownerName("张三")
                    .build();
            when(documentService.uploadFile("feishu", "folder_1", "a.xlsx", "base64", "u1", "张三"))
                    .thenReturn(Mono.just(documentInfo()));
            when(assembler.toResponse(any(DocumentInfo.class))).thenReturn(response());

            StepVerifier.create(handler.uploadFile(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            UploadFileRequest body = UploadFileRequest.builder()
                    .platform("feishu")
                    .folderId("folder_1")
                    .fileName("a.xlsx")
                    .fileContent("base64")
                    .ownerId("u1")
                    .ownerName("张三")
                    .build();
            when(documentService.uploadFile("feishu", "folder_1", "a.xlsx", "base64", "u1", "张三"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.uploadFile(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getDocumentDetail")
    class GetDocumentDetailTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(documentService.getDocumentDetail(10L)).thenReturn(Mono.just(documentInfo()));
            when(assembler.toResponse(any(DocumentInfo.class))).thenReturn(response());

            StepVerifier.create(handler.getDocumentDetail(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(documentService.getDocumentDetail(10L)).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getDocumentDetail(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getDocumentsInFolder")
    class GetDocumentsInFolderTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(documentService.listDocuments("folder_1")).thenReturn(Mono.just(List.of(documentInfo())));
            when(assembler.toResponseList(anyList())).thenReturn(List.of(response()));

            StepVerifier.create(handler.getDocumentsInFolder(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("folderId", "folder_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(documentService.listDocuments("folder_1")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getDocumentsInFolder(MockServerRequest.builder()
                            .queryParam("platform", "feishu")
                            .queryParam("folderId", "folder_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getMyDocuments")
    class GetMyDocumentsTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(documentService.getMyDocuments("u1")).thenReturn(Mono.just(List.of(documentInfo())));
            when(assembler.toResponseList(anyList())).thenReturn(List.of(response()));

            StepVerifier.create(handler.getMyDocuments(MockServerRequest.builder()
                            .queryParam("ownerId", "u1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(documentService.getMyDocuments("u1")).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getMyDocuments(MockServerRequest.builder()
                            .queryParam("ownerId", "u1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("downloadDocument")
    class DownloadDocumentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(documentService.downloadDocument(10L, "pdf")).thenReturn(Mono.just("http://url/download"));

            StepVerifier.create(handler.downloadDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .queryParam("format", "pdf")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(documentService.downloadDocument(10L, "pdf"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.downloadDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .queryParam("format", "pdf")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteDocument")
    class DeleteDocumentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(documentService.deleteDocument(10L)).thenReturn(Mono.just(true));

            StepVerifier.create(handler.deleteDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(documentService.deleteDocument(10L)).thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.deleteDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("moveDocument")
    class MoveDocumentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(documentService.moveDocument(10L, "target_1")).thenReturn(Mono.just(true));

            StepVerifier.create(handler.moveDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .queryParam("targetFolderId", "target_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(documentService.moveDocument(10L, "target_1"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.moveDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .queryParam("targetFolderId", "target_1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("copyDocument")
    class CopyDocumentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(documentService.copyDocument(10L, "target_1", "新标题")).thenReturn(Mono.just(documentInfo()));
            when(assembler.toResponse(any(DocumentInfo.class))).thenReturn(response());

            StepVerifier.create(handler.copyDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .queryParam("targetFolderId", "target_1")
                            .queryParam("newTitle", "新标题")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(documentService.copyDocument(10L, "target_1", "新标题"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.copyDocument(MockServerRequest.builder()
                            .pathVariable("documentId", "10")
                            .queryParam("targetFolderId", "target_1")
                            .queryParam("newTitle", "新标题")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }
}
