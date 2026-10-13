package com.apex.scaffold.officeservice.interfaces.off.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.office.off.application.dto.DepartmentDTO;
import com.apex.scaffold.office.off.application.dto.EmployeeDTO;
import com.apex.scaffold.office.off.application.dto.SyncHistoryDTO;
import com.apex.scaffold.office.off.application.service.OrgSyncService;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("OrgSyncHandler 单元测试")
class OrgSyncHandlerTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();
    private final RequestHandlerUtil requestHandlerUtil = new RequestHandlerUtil(validator);

    private OrgSyncService orgSyncService;
    private OrgSyncHandler handler;

    @BeforeEach
    void setUp() {
        orgSyncService = mock(OrgSyncService.class);
        handler = new OrgSyncHandler(orgSyncService, requestHandlerUtil);
    }

    private DepartmentDTO department() {
        return DepartmentDTO.builder().id(1L).deptCode("D1").deptName("研发部").build();
    }

    private EmployeeDTO employee() {
        return EmployeeDTO.builder()
                .id(1L)
                .employeeCode("E1")
                .employeeName("张三")
                .build();
    }

    private SyncHistoryDTO syncHistory() {
        return SyncHistoryDTO.builder().id(1L).syncType("FULL").build();
    }

    private PageResponse<DepartmentDTO> departmentPage() {
        return PageResponse.of(java.util.List.of(department()), 1L, 1, 10);
    }

    private PageResponse<EmployeeDTO> employeePage() {
        return PageResponse.of(java.util.List.of(employee()), 1L, 1, 10);
    }

    private PageResponse<SyncHistoryDTO> syncHistoryPage() {
        return PageResponse.of(java.util.List.of(syncHistory()), 1L, 1, 10);
    }

    @Nested
    @DisplayName("getDepartmentList")
    class GetDepartmentListTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(orgSyncService.getDepartmentPage(anyInt(), anyInt(), any())).thenReturn(Mono.just(departmentPage()));

            StepVerifier.create(handler.getDepartmentList(MockServerRequest.builder()
                            .queryParam("pageNum", "1")
                            .queryParam("pageSize", "10")
                            .queryParam("status", "ACTIVE")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("默认分页且无 status 返回 HTTP 200")
        void defaultPage() {
            when(orgSyncService.getDepartmentPage(anyInt(), anyInt(), isNull()))
                    .thenReturn(Mono.just(departmentPage()));

            StepVerifier.create(handler.getDepartmentList(
                            MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(orgSyncService.getDepartmentPage(anyInt(), anyInt(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(handler.getDepartmentList(
                            MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getDepartmentDetail")
    class GetDepartmentDetailTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(orgSyncService.getDepartmentById(9L)).thenReturn(Mono.just(department()));

            StepVerifier.create(handler.getDepartmentDetail(
                            MockServerRequest.builder().pathVariable("id", "9").build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("部门不存在返回 HTTP 400")
        void notFound() {
            when(orgSyncService.getDepartmentById(9L)).thenReturn(Mono.empty());

            StepVerifier.create(handler.getDepartmentDetail(
                            MockServerRequest.builder().pathVariable("id", "9").build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("部门ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.getDepartmentDetail(MockServerRequest.builder()
                            .pathVariable("id", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("createDepartment")
    class CreateDepartmentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            DepartmentDTO body = department();
            when(orgSyncService.createDepartment(any(DepartmentDTO.class))).thenReturn(Mono.just(department()));

            StepVerifier.create(
                            handler.createDepartment(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            DepartmentDTO body = department();
            when(orgSyncService.createDepartment(any(DepartmentDTO.class)))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(
                            handler.createDepartment(MockServerRequest.builder().body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("updateDepartment")
    class UpdateDepartmentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            DepartmentDTO body = department();
            when(orgSyncService.updateDepartment(eq(9L), any(DepartmentDTO.class)))
                    .thenReturn(Mono.just(department()));

            StepVerifier.create(handler.updateDepartment(
                            MockServerRequest.builder().pathVariable("id", "9").body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("部门不存在返回 HTTP 400")
        void notFound() {
            DepartmentDTO body = department();
            when(orgSyncService.updateDepartment(eq(9L), any(DepartmentDTO.class)))
                    .thenReturn(Mono.empty());

            StepVerifier.create(handler.updateDepartment(
                            MockServerRequest.builder().pathVariable("id", "9").body(Mono.just(body))))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("部门ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.updateDepartment(MockServerRequest.builder()
                            .pathVariable("id", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("deleteDepartment")
    class DeleteDepartmentTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(orgSyncService.deleteDepartment(9L)).thenReturn(Mono.empty());

            StepVerifier.create(handler.deleteDepartment(
                            MockServerRequest.builder().pathVariable("id", "9").build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("部门ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.deleteDepartment(MockServerRequest.builder()
                            .pathVariable("id", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getEmployeeList")
    class GetEmployeeListTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(orgSyncService.getEmployeePage(anyInt(), anyInt(), any(), any()))
                    .thenReturn(Mono.just(employeePage()));

            StepVerifier.create(handler.getEmployeeList(MockServerRequest.builder()
                            .queryParam("pageNum", "1")
                            .queryParam("pageSize", "10")
                            .queryParam("deptId", "3")
                            .queryParam("status", "ACTIVE")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("默认分页且无过滤返回 HTTP 200")
        void defaultPage() {
            when(orgSyncService.getEmployeePage(anyInt(), anyInt(), isNull(), isNull()))
                    .thenReturn(Mono.just(employeePage()));

            StepVerifier.create(
                            handler.getEmployeeList(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(orgSyncService.getEmployeePage(anyInt(), anyInt(), any(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(
                            handler.getEmployeeList(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getEmployeeDetail")
    class GetEmployeeDetailTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(orgSyncService.getEmployeeById(9L)).thenReturn(Mono.just(employee()));

            StepVerifier.create(handler.getEmployeeDetail(
                            MockServerRequest.builder().pathVariable("id", "9").build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("员工不存在返回 HTTP 400")
        void notFound() {
            when(orgSyncService.getEmployeeById(9L)).thenReturn(Mono.empty());

            StepVerifier.create(handler.getEmployeeDetail(
                            MockServerRequest.builder().pathVariable("id", "9").build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }

        @Test
        @DisplayName("员工ID非法返回 HTTP 400")
        void invalidId() {
            StepVerifier.create(handler.getEmployeeDetail(MockServerRequest.builder()
                            .pathVariable("id", "abc")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("getSyncHistory")
    class GetSyncHistoryTest {
        @Test
        @DisplayName("happy path 返回 HTTP 200")
        void happyPath() {
            when(orgSyncService.getSyncHistoryPage(anyInt(), anyInt(), any(), any()))
                    .thenReturn(Mono.just(syncHistoryPage()));

            StepVerifier.create(handler.getSyncHistory(MockServerRequest.builder()
                            .queryParam("pageNum", "1")
                            .queryParam("pageSize", "10")
                            .queryParam("syncSource", "FEISHU")
                            .queryParam("syncStatus", "SUCCESS")
                            .build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(200))
                    .verifyComplete();
        }

        @Test
        @DisplayName("服务异常时返回 HTTP 400")
        void serviceError() {
            when(orgSyncService.getSyncHistoryPage(anyInt(), anyInt(), any(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("boom")));

            StepVerifier.create(
                            handler.getSyncHistory(MockServerRequest.builder().build()))
                    .assertNext(r -> assertThat(r.statusCode().value()).isEqualTo(400))
                    .verifyComplete();
        }
    }
}
