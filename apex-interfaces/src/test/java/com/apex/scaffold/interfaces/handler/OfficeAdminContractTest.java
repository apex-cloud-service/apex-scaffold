package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.ServerRequest;

class OfficeAdminContractTest {
    @Test
    void managementEndpointsHavePermissionAuditAndIdempotency() throws Exception {
        Class<?> handler = Class.forName("com.apex.scaffold.interfaces.handler.OfficeAdminHandler");
        for (String name : new String[] {
            "listApps", "getApp", "createApp", "updateApp", "deleteApp", "listBindings", "reviewBinding", "unbind"
        }) {
            java.lang.reflect.Method method = handler.getMethod(name, ServerRequest.class);
            assertThat(method.getAnnotation(RequirePermission.class)).isNotNull();
            if (!name.startsWith("list") && !name.startsWith("get")) {
                assertThat(method.getAnnotation(IdempotentApi.class)).isNotNull();
                OperationLog audit = method.getAnnotation(OperationLog.class);
                assertThat(audit).isNotNull();
                assertThat(audit.saveRequest()).isFalse();
                assertThat(audit.saveResponse()).isFalse();
            }
        }
    }

    @Test
    void appResponseDoesNotExposeSecretReferences() throws Exception {
        Class<?> response = Class.forName("com.apex.scaffold.interfaces.dto.office.OfficeAppResponse");
        assertThat(response.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("secretReference", "signingSecretReference", "clientSecret");
    }

    @Test
    void appRequestRejectsMissingFields() throws Exception {
        Class<?> type = Class.forName("com.apex.scaffold.interfaces.dto.office.OfficeAppRequest");
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(type.getConstructor().newInstance()))
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("appId", "secretReference", "redirectUri", "enabled");
        }
    }

    @Test
    void allAdminRoutesHaveOpenApiMetadataAndPermissionSeeds() throws Exception {
        Class<?> router = Class.forName("com.apex.scaffold.interfaces.router.OfficeAdminRouter");
        Class<?> handler = Class.forName("com.apex.scaffold.interfaces.handler.OfficeAdminHandler");
        var routes = router.getMethod("officeAdminRoutes", handler)
                .getAnnotation(org.springdoc.core.annotations.RouterOperations.class)
                .value();
        assertThat(routes).hasSize(8);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (var route : routes) {
            var method = handler.getMethod(route.beanMethod(), ServerRequest.class);
            var operation = method.getAnnotation(io.swagger.v3.oas.annotations.Operation.class);
            assertThat(operation).isNotNull();
            assertThat(ids.add(operation.operationId())).isTrue();
            assertThat(operation.responses()).isNotEmpty();
            String permission = method.getAnnotation(RequirePermission.class).value();
            for (String database : new String[] {"mysql", "postgresql"}) {
                try (var stream = getClass()
                        .getResourceAsStream("/db/migration/" + database + "/V30__add_office_admin_permissions.sql")) {
                    assertThat(stream).isNotNull();
                    assertThat(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                            .contains(permission);
                }
            }
        }
    }
}
