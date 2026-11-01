package com.apex.scaffold.officeservice.interfaces.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.officeservice.interfaces.handler.OfficeAdminHandler;
import com.apex.scaffold.officeservice.interfaces.handler.OfficeHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeApprovalHandler;
import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeMessageHandler;
import com.apex.scaffold.officeservice.interfaces.off.handler.MessageHandler;
import com.apex.scaffold.officeservice.interfaces.off.handler.MessageTemplateHandler;
import com.apex.scaffold.officeservice.interfaces.off.handler.OrgSyncHandler;
import com.apex.scaffold.officeservice.interfaces.off.router.MessageRouter;
import com.apex.scaffold.officeservice.interfaces.off.router.OrgSyncRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeApprovalRouter;
import com.apex.scaffold.officeservice.interfaces.router.office.OfficeMessageRouter;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = OfficeOpenApiContractTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.bootstrap.enabled=false",
            "springdoc.api-docs.enabled=true"
        })
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OfficeOpenApiContractTest {
    private final org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext context;

    OfficeOpenApiContractTest(
            org.springframework.boot.web.reactive.context.ReactiveWebServerApplicationContext context) {
        this.context = context;
    }

    @Test
    void generatedDocumentRetainsWrappedResponsesAndHandlerMetadata() {
        JsonNode document = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + context.getWebServer().getPort())
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .build()
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(document).isNotNull();
        assertOperation(document, "/api/office/documents/my", "get", "getMyDocuments", "OfficeDocumentListResult");
        assertOperation(
                document,
                "/api/office/documents/{documentId}/move",
                "put",
                "moveDocument",
                "OfficeDocumentBooleanResult");
        assertOperation(
                document, "/api/office/calendar/events", "post", "createCalendarEvent", "OfficeCalendarEventResult");
        assertOperation(
                document,
                "/api/office/calendar/events/{eventId}",
                "get",
                "getCalendarEventDetail",
                "OfficeCalendarEventResult");
        assertOperation(
                document,
                "/api/office/calendar/events/{eventId}",
                "put",
                "updateCalendarEvent",
                "OfficeCalendarBooleanResult");
        assertOperation(
                document,
                "/api/office/calendar/events/{eventId}",
                "delete",
                "deleteCalendarEvent",
                "OfficeCalendarBooleanResult");
        assertOperation(
                document,
                "/api/office/calendar/events/range",
                "get",
                "getCalendarEventsByTimeRange",
                "OfficeCalendarEventListResult");
        assertOperation(
                document,
                "/api/office/calendar/events/my",
                "get",
                "getMyCalendarEvents",
                "OfficeCalendarEventListResult");
        assertOperation(
                document,
                "/api/office/calendar/events/upcoming",
                "get",
                "getUpcomingEvents",
                "OfficeCalendarEventListResult");
        assertOperation(
                document,
                "/api/v1/office/organization/sync",
                "post",
                "syncOrganization",
                "OfficeOrganizationSyncResult");
        assertOperation(
                document,
                "/api/v1/office/organization/mappings",
                "get",
                "listOrganizationMappings",
                "OfficeOrganizationMappingListResult");
        assertOperation(
                document,
                "/api/v1/office/organization/sync/tasks",
                "get",
                "listSyncTasks",
                "OfficeOrganizationTaskListResult");
        assertOperation(
                document,
                "/api/v1/office/organization/mappings/department/{platform}/{externalOrgId}",
                "get",
                "getDepartmentMapping",
                "OfficeOrganizationMappingResult");
        assertOperation(
                document,
                "/api/v1/office/organization/mappings/member/{platform}/{externalUserId}",
                "get",
                "getMemberMapping",
                "OfficeOrganizationMappingResult");
        assertThat(document.at("/components/schemas/OfficeCalendarEventListResult/properties/data/items/$ref")
                        .asText())
                .endsWith("/CalendarEventResponse");
        assertThat(document.at("/components/schemas/OfficeCalendarBooleanResult/properties/data/type")
                        .asText())
                .isEqualTo("boolean");
        assertThat(document.at("/components/schemas/OfficeOrganizationTaskListResult/properties/data/items/$ref")
                        .asText())
                .endsWith("/OfficeSyncTask");
        assertThat(document.path("paths")
                        .path("/api/office/calendar/events/range")
                        .path("get")
                        .path("parameters")
                        .toString())
                .doesNotContain("organizerId");
        java.util.List<String> operationIds = new java.util.ArrayList<>();
        document.path("paths")
                .forEach(path -> path.forEach(operation -> {
                    if (operation.has("operationId")) {
                        operationIds.add(operation.path("operationId").asText());
                    }
                }));
        assertThat(operationIds).doesNotHaveDuplicates();
        assertOperation(document, "/api/office/messages", "post", "sendOfficeMessage", "OfficeSendMessageResult");
        assertOperation(
                document,
                "/api/office/messages/{messageId}",
                "get",
                "getOfficeMessageStatus",
                "OfficeMessageStatusResult");
        assertOperation(
                document,
                "/api/office/messages/{messageId}/resend",
                "post",
                "resendOfficeMessage",
                "OfficeMessageBooleanResult");
        assertOperation(document, "/admin-api/v1/org/departments", "get", "getDepartmentList", "DepartmentPageResult");
        assertOperation(document, "/admin-api/v1/org/employees/{id}", "get", "getEmployeeDetail", "EmployeeResult");
        assertOperation(document, "/api/office/approvals", "post", "initiateApproval", "OfficeApprovalResult");
        assertOperation(
                document, "/api/office/approvals/pending", "get", "getPendingApprovals", "OfficeApprovalListResult");
        assertOperation(
                document,
                "/api/office/approvals/{processInstanceId}",
                "delete",
                "cancelApproval",
                "OfficeApprovalBooleanResult");
        assertOperation(document, "/api/v1/messages", "post", "sendMessage", "InternalMessageResult");
        assertOperation(document, "/api/v1/messages/inbox", "get", "getInbox", "InternalMessageListResult");
        assertOperation(
                document, "/api/v1/messages/unread/count", "get", "getUnreadCount", "InternalMessageCountResult");
        assertOperation(
                document, "/admin-api/v1/message-templates", "get", "getEnabledTemplates", "OfficeTemplateListResult");
        assertOperation(
                document,
                "/admin-api/v1/message-templates/{templateId}",
                "put",
                "updateTemplate",
                "OfficeTemplateResult");
        assertThat(document.path("paths")
                        .path("/admin-api/v1/message-templates")
                        .path("get")
                        .path("parameters")
                        .toString())
                .doesNotContain("templateType");
        assertThat(document.at("/components/schemas/OfficeApprovalListResult/properties/data/items/$ref")
                        .asText())
                .endsWith("/ApprovalProcessResponse");
        assertThat(document.at("/components/schemas/InternalMessageCountResult/properties/data/type")
                        .asText())
                .isEqualTo("integer");
        assertThat(document.at("/components/schemas/OfficeSendMessageResult/properties/data/$ref")
                        .asText())
                .endsWith("/SendMessageResponse");
        assertThat(document.path("paths")
                        .path("/api/office/messages/{messageId}/resend")
                        .path("post")
                        .path("responses")
                        .has("404"))
                .isFalse();
    }

    @Test
    void generatedOfficeAdminAndLoginContractsDescribeBodiesDataAndPaginationDefaults() {
        JsonNode document = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + context.getWebServer().getPort())
                .responseTimeout(java.time.Duration.ofSeconds(30))
                .build()
                .get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(document).isNotNull();
        String apps = "/admin-api/v1/office/apps";
        String app = apps + "/{platform}";
        String bindings = "/admin-api/v1/office/bindings";
        assertOperation(document, apps, "get", "listOfficeApps", "OfficeAppListResult");
        assertOperation(document, app, "get", "getOfficeApp", "OfficeAppResult");
        assertOperation(document, app, "post", "createOfficeApp", "OfficeAppResult");
        assertOperation(document, app, "put", "updateOfficeApp", "OfficeAppResult");
        assertOperation(document, app, "delete", "deleteOfficeApp", "OfficeAdminBooleanResult");
        assertOperation(document, bindings, "get", "listOfficeBindings", "OfficeBindingListResult");
        assertOperation(document, bindings + "/page", "get", "listOfficeBindingsPage", "OfficeBindingPageResult");
        assertOperation(document, bindings + "/{id}/review", "post", "reviewOfficeBinding", "OfficeAdminBooleanResult");
        assertOperation(
                document, bindings + "/{id}/unbind", "delete", "unbindOfficeIdentity", "OfficeAdminBooleanResult");
        assertOperation(
                document, "/api/v1/office/status", "get", "listOfficePlatformStatus", "OfficePlatformStatusResult");
        assertOperation(document, "/api/v1/office/login", "get", "startOfficeLogin", "OfficeAuthorizationUrlResult");
        assertOperation(document, "/api/v1/office/callback", "get", "officeLoginCallback", "OfficeLoginResult");

        JsonNode schemas = document.path("components").path("schemas");
        JsonNode appData = resolveSchema(document, schemas.at("/OfficeAppResult/properties/data"));
        assertThat(appData.path("properties").has("appId")).isTrue();
        assertThat(appData.at("/properties/enabled/type").asText()).isEqualTo("boolean");
        JsonNode appList = schemas.at("/OfficeAppListResult/properties/data");
        assertThat(appList.path("type").asText()).isEqualTo("array");
        assertThat(resolveSchema(document, appList.path("items"))).isEqualTo(appData);
        JsonNode bindingList = schemas.at("/OfficeBindingListResult/properties/data");
        assertThat(bindingList.path("type").asText()).isEqualTo("array");
        JsonNode bindingData = resolveSchema(document, bindingList.path("items"));
        assertThat(bindingData.path("properties").has("externalUserId")).isTrue();
        assertThat(bindingData.at("/properties/status/enum").toString()).contains("PENDING", "APPROVED");
        JsonNode page = resolveSchema(document, schemas.at("/OfficeBindingPageResult/properties/data"));
        assertThat(resolveSchema(document, page.at("/properties/list/items"))).isEqualTo(bindingData);
        assertThat(page.at("/properties/total/type").asText()).isEqualTo("integer");
        assertThat(page.at("/properties/pageNum/type").asText()).isEqualTo("integer");
        assertThat(page.at("/properties/pageSize/type").asText()).isEqualTo("integer");
        assertThat(page.at("/properties/hasNext/type").asText()).isEqualTo("boolean");
        assertThat(schemas.at("/OfficeAdminBooleanResult/properties/data/type").asText())
                .isEqualTo("boolean");
        assertThat(schemas.at("/OfficePlatformStatusResult/properties/data/type")
                        .asText())
                .isEqualTo("array");
        assertThat(schemas.at("/OfficePlatformStatusResult/properties/data/items/type")
                        .asText())
                .isEqualTo("string");
        assertThat(schemas.at("/OfficeAuthorizationUrlResult/properties/data/type")
                        .asText())
                .isEqualTo("string");
        JsonNode login = resolveSchema(document, schemas.at("/OfficeLoginResult/properties/data"));
        assertThat(login.at("/properties/accessToken/type").asText()).isEqualTo("string");
        assertThat(login.at("/properties/refreshToken/type").asText()).isEqualTo("string");
        assertThat(login.at("/properties/expiresIn/type").asText()).isEqualTo("integer");

        for (String method : java.util.List.of("post", "put")) {
            JsonNode body = assertRequestBody(document, app, method, "OfficeAppRequest");
            assertThat(body.path("properties").has("secretReference")).isTrue();
            assertThat(body.at("/properties/enabled/type").asText()).isEqualTo("boolean");
            assertThat(body.path("required").toString()).contains("appId", "secretReference", "redirectUri", "enabled");
        }
        JsonNode review = assertRequestBody(document, bindings + "/{id}/review", "post", "OfficeBindingReviewRequest");
        assertThat(review.at("/properties/approved/type").asText()).isEqualTo("boolean");
        assertThat(review.at("/properties/version/type").asText()).isEqualTo("integer");
        assertThat(review.path("required").toString()).contains("approved", "version");
        for (String path : java.util.List.of(bindings, bindings + "/page")) {
            assertPaginationDefault(document, path, "page", 1);
            assertPaginationDefault(document, path, "size", 20);
        }
    }

    private JsonNode resolveSchema(JsonNode document, JsonNode schema) {
        return schema.has("$ref") ? document.at(schema.path("$ref").asText().substring(1)) : schema;
    }

    private JsonNode assertRequestBody(JsonNode document, String path, String method, String schema) {
        JsonNode body = document.path("paths").path(path).path(method).path("requestBody");
        assertThat(body.path("required").asBoolean()).isTrue();
        JsonNode bodySchema = body.at("/content/application~1json/schema");
        assertThat(bodySchema.path("$ref").asText()).isEqualTo("#/components/schemas/" + schema);
        return resolveSchema(document, bodySchema);
    }

    private void assertPaginationDefault(JsonNode document, String path, String name, int defaultValue) {
        JsonNode parameters = document.path("paths").path(path).path("get").path("parameters");
        java.util.List<JsonNode> matches = new java.util.ArrayList<>();
        parameters.forEach(parameter -> {
            if (name.equals(parameter.path("name").asText())) {
                matches.add(parameter);
            }
        });
        assertThat(matches).hasSize(1);
        JsonNode parameter = matches.get(0);
        assertThat(parameter.path("in").asText()).isEqualTo("query");
        assertThat(parameter.path("required").asBoolean()).isFalse();
        assertThat(parameter.at("/schema/type").asText())
                .withFailMessage("Expected integer parameter %s for %s, parameter: %s", name, path, parameter)
                .isEqualTo("integer");
        assertThat(parameter.at("/schema/default").asInt()).isEqualTo(defaultValue);
    }

    private void assertOperation(JsonNode document, String path, String method, String id, String schema) {
        JsonNode operation = document.path("paths").path(path).path(method);
        assertThat(operation.path("operationId").asText()).isEqualTo(id);
        assertThat(operation
                        .at("/responses/200/content/application~1json/schema/$ref")
                        .asText())
                .withFailMessage(
                        "Expected JSON response schema %s for %s %s, operation: %s", schema, method, path, operation)
                .isEqualTo("#/components/schemas/" + schema);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
            exclude = {
                DataSourceAutoConfiguration.class,
                ReactiveSecurityAutoConfiguration.class,
                org.springframework.boot.actuate.autoconfigure.security.reactive
                        .ReactiveManagementWebSecurityAutoConfiguration.class,
                // 契约切片测试只装配 Router（@Import 显式列举），
                // 需排除组件入口自动装配，避免引入 Mapper/Handler 依赖（方案 C7）
                com.apex.scaffold.office.autoconfigure.OfficeComponentAutoConfiguration.class
            })
    @Import({
        OfficeAdminRouter.class,
        OfficeRouter.class,
        OrgSyncRouter.class,
        OfficeMessageRouter.class,
        OfficeApprovalRouter.class,
        MessageRouter.class,
        com.apex.scaffold.officeservice.interfaces.router.office.OfficeDocumentRouter.class,
        com.apex.scaffold.officeservice.interfaces.router.office.OfficeCalendarRouter.class,
        com.apex.scaffold.officeservice.interfaces.router.office.OfficeOrganizationRouter.class
    })
    static class Application {
        @Bean
        OfficeAdminHandler officeAdminHandler() {
            return mock(OfficeAdminHandler.class);
        }

        @Bean
        OfficeHandler officeHandler() {
            return mock(OfficeHandler.class);
        }

        @Bean
        com.apex.scaffold.officeservice.interfaces.handler.office.OfficeDocumentHandler officeDocumentHandler() {
            return mock(com.apex.scaffold.officeservice.interfaces.handler.office.OfficeDocumentHandler.class);
        }

        @Bean
        com.apex.scaffold.officeservice.interfaces.handler.office.OfficeCalendarHandler officeCalendarHandler() {
            return mock(com.apex.scaffold.officeservice.interfaces.handler.office.OfficeCalendarHandler.class);
        }

        @Bean
        com.apex.scaffold.officeservice.interfaces.handler.office.OfficeOrganizationHandler
                officeOrganizationHandler() {
            return mock(com.apex.scaffold.officeservice.interfaces.handler.office.OfficeOrganizationHandler.class);
        }

        @Bean
        OrgSyncHandler orgSyncHandler() {
            return mock(OrgSyncHandler.class);
        }

        @Bean
        OfficeMessageHandler officeMessageHandler() {
            return mock(OfficeMessageHandler.class);
        }

        @Bean
        OfficeApprovalHandler officeApprovalHandler() {
            return mock(OfficeApprovalHandler.class);
        }

        @Bean
        MessageHandler messageHandler() {
            return mock(MessageHandler.class);
        }

        @Bean
        MessageTemplateHandler messageTemplateHandler() {
            return mock(MessageTemplateHandler.class);
        }
    }
}
