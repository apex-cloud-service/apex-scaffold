package com.apex.scaffold.authcenter.interfaces.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnifiedAuthorizationResponseTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void constructorDefensivelyCopiesAuthorizationCollections() {
        var permissions = new ArrayList<>(List.of("user:read"));
        var roles = new ArrayList<>(List.of(new UnifiedAuthorizationResponse.Role("ADMIN", "ALL")));

        var response = new UnifiedAuthorizationResponse("center", 99L, permissions, roles);
        permissions.add("user:write");
        roles.clear();

        assertThat(response.permissions()).containsExactly("user:read");
        assertThat(response.roles()).containsExactly(new UnifiedAuthorizationResponse.Role("ADMIN", "ALL"));
        assertThat(response.permissions()).isUnmodifiable();
        assertThat(response.roles()).isUnmodifiable();
    }

    @Test
    void jsonUsesStableAuthorizationContract() throws Exception {
        var response = new UnifiedAuthorizationResponse(
                "center", 99L, List.of("user:read"), List.of(new UnifiedAuthorizationResponse.Role("ADMIN", "ALL")));

        assertThat(objectMapper.writeValueAsString(response))
                .isEqualTo(
                        "{\"tenantId\":\"center\",\"userId\":99,\"permissions\":[\"user:read\"],\"roles\":[{\"code\":\"ADMIN\",\"dataScope\":\"ALL\"}]}");
    }

    @Test
    void schemaMarksAuthorizationFieldsAsRequired() {
        ResolvedSchema schema = ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(UnifiedAuthorizationResponse.class));

        assertThat(schema.referencedSchemas.get("UnifiedAuthorizationResponse").getRequired())
                .containsExactlyInAnyOrder("tenantId", "userId", "permissions", "roles");
        assertThat(schema.referencedSchemas.get("Role").getRequired()).containsExactly("code");
    }
}
