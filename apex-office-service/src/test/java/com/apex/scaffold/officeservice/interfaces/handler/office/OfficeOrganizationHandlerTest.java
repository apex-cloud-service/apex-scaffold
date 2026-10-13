package com.apex.scaffold.officeservice.interfaces.handler.office;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.office.application.OfficeOrganizationSyncService;
import com.apex.scaffold.office.domain.OfficeOrganizationMapping;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.OfficeSyncTask;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncOrganizationRequest;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OfficeOrganizationHandler 单元测试")
class OfficeOrganizationHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OfficeOrganizationSyncService organizationSyncService;
    private OfficeOrganizationHandler handler;

    @BeforeEach
    void setUp() {
        organizationSyncService = mock(OfficeOrganizationSyncService.class);
        handler = new OfficeOrganizationHandler(organizationSyncService, requestHandlerUtil);
    }

    private SyncOrganizationRequest syncRequest(String syncType) {
        SyncOrganizationRequest request = new SyncOrganizationRequest();
        request.setPlatform("FEISHU");
        request.setSyncType(syncType);
        request.setFullSync(false);
        return request;
    }

    private OfficeOrganizationSyncService.SyncTaskResult taskResult() {
        return OfficeOrganizationSyncService.SyncTaskResult.success(100L, 5, "同步完成");
    }

    private OfficeOrganizationMapping mapping() {
        return OfficeOrganizationMapping.builder()
                .id(1L)
                .tenantId("1")
                .platform(OfficePlatform.FEISHU)
                .externalOrgId("od-1")
                .externalUserId("ou-1")
                .apexDeptId("dept-1")
                .apexUserId("user-1")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private OfficeSyncTask syncTask() {
        return OfficeSyncTask.builder()
                .id(1L)
                .tenantId("1")
                .platform(OfficePlatform.FEISHU)
                .state("SUCCESS")
                .version(0)
                .deleted(0)
                .build();
    }

    @Nested
    @DisplayName("syncOrganization")
    class SyncOrganizationTest {
        @Test
        @DisplayName("DEPARTMENT 同步 happy path 返回 HTTP 200")
        void departmentHappyPath() {
            when(organizationSyncService.syncDepartments("1", OfficePlatform.FEISHU, false))
                    .thenReturn(Mono.just(taskResult()));

            StepVerifier.create(handler.syncOrganization(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .body(Mono.just(syncRequest("DEPARTMENT")))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("MEMBER 同步 happy path 返回 HTTP 200")
        void memberHappyPath() {
            when(organizationSyncService.syncMembers("1", OfficePlatform.FEISHU, false))
                    .thenReturn(Mono.just(taskResult()));

            StepVerifier.create(handler.syncOrganization(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .body(Mono.just(syncRequest("MEMBER")))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("不支持的同步类型返回 HTTP 400")
        void unsupportedSyncType() {
            StepVerifier.create(handler.syncOrganization(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .body(Mono.just(syncRequest("UNKNOWN")))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时经 handleError 返回 HTTP 400")
        void serviceError() {
            when(organizationSyncService.syncDepartments("1", OfficePlatform.FEISHU, false))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.syncOrganization(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .body(Mono.just(syncRequest("DEPARTMENT")))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("非法平台名抛出异常并返回 HTTP 400")
        void unsupportedPlatform() {
            SyncOrganizationRequest request = syncRequest("DEPARTMENT");
            request.setPlatform("SLACK");

            StepVerifier.create(handler.syncOrganization(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .body(Mono.just(request))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getDepartmentMapping")
    class GetDepartmentMappingTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(organizationSyncService.getDepartmentMapping("1", OfficePlatform.FEISHU, "od-1"))
                    .thenReturn(Mono.just(mapping()));

            StepVerifier.create(handler.getDepartmentMapping(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .pathVariable("platform", "FEISHU")
                            .pathVariable("externalOrgId", "od-1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("映射不存在时返回 HTTP 200 空数据")
        void empty() {
            when(organizationSyncService.getDepartmentMapping("1", OfficePlatform.FEISHU, "od-1"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(handler.getDepartmentMapping(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .pathVariable("platform", "FEISHU")
                            .pathVariable("externalOrgId", "od-1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(organizationSyncService.getDepartmentMapping("1", OfficePlatform.FEISHU, "od-1"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getDepartmentMapping(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .pathVariable("platform", "FEISHU")
                            .pathVariable("externalOrgId", "od-1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getMemberMapping")
    class GetMemberMappingTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(organizationSyncService.getMemberMapping("1", OfficePlatform.FEISHU, "ou-1"))
                    .thenReturn(Mono.just(mapping()));

            StepVerifier.create(handler.getMemberMapping(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .pathVariable("platform", "FEISHU")
                            .pathVariable("externalUserId", "ou-1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("映射不存在时返回 HTTP 200 空数据")
        void empty() {
            when(organizationSyncService.getMemberMapping("1", OfficePlatform.FEISHU, "ou-1"))
                    .thenReturn(Mono.empty());

            StepVerifier.create(handler.getMemberMapping(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .pathVariable("platform", "FEISHU")
                            .pathVariable("externalUserId", "ou-1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(organizationSyncService.getMemberMapping("1", OfficePlatform.FEISHU, "ou-1"))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getMemberMapping(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .pathVariable("platform", "FEISHU")
                            .pathVariable("externalUserId", "ou-1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("listOrganizationMappings")
    class ListOrganizationMappingsTest {
        @Test
        @DisplayName("指定平台 happy path 返回 HTTP 200")
        void happyPathWithPlatform() {
            when(organizationSyncService.listOrganizationMappings("1", OfficePlatform.FEISHU))
                    .thenReturn(Mono.just(List.of(mapping())));

            StepVerifier.create(handler.listOrganizationMappings(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .queryParam("platform", "FEISHU")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("不传平台时查询全部平台返回 HTTP 200")
        void happyPathWithoutPlatform() {
            when(organizationSyncService.listOrganizationMappings("1", null)).thenReturn(Mono.just(List.of(mapping())));

            StepVerifier.create(handler.listOrganizationMappings(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(organizationSyncService.listOrganizationMappings("1", OfficePlatform.FEISHU))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.listOrganizationMappings(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .queryParam("platform", "FEISHU")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("listSyncTasks")
    class ListSyncTasksTest {
        @Test
        @DisplayName("指定平台与分页 happy path 返回 HTTP 200")
        void happyPathWithPlatform() {
            when(organizationSyncService.listSyncTasks("1", OfficePlatform.FEISHU, 1, 20))
                    .thenReturn(Mono.just(List.of(syncTask())));

            StepVerifier.create(handler.listSyncTasks(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .queryParam("platform", "FEISHU")
                            .queryParam("page", "1")
                            .queryParam("size", "20")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("缺省分页参数使用默认值，不传平台查询全部返回 HTTP 200")
        void happyPathDefaults() {
            when(organizationSyncService.listSyncTasks("1", null, 1, 20)).thenReturn(Mono.just(List.of(syncTask())));

            StepVerifier.create(handler.listSyncTasks(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(organizationSyncService.listSyncTasks("1", OfficePlatform.FEISHU, 1, 20))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.listSyncTasks(MockServerRequest.builder()
                            .header("X-Tenant-Id", "1")
                            .queryParam("platform", "FEISHU")
                            .queryParam("page", "1")
                            .queryParam("size", "20")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }
}
