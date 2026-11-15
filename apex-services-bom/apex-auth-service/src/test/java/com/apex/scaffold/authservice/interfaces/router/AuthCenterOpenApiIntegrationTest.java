package com.apex.scaffold.authservice.interfaces.router;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.authservice.AuthCenterBasicTestConfiguration;
import com.apex.scaffold.authservice.EmbeddedRedisBootTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "security.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "apex.security.data-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "apex.security.config-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "security.config.encryption.key=01234567890123456789012345678901"
        })
@Import(AuthCenterBasicTestConfiguration.class)
@AutoConfigureWebTestClient
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RequiredArgsConstructor
class AuthCenterOpenApiIntegrationTest extends EmbeddedRedisBootTestSupport {
    private static final String CONTRACTS =
            """
            post|/api/mfa/totp/enable|enableTotp||TotpSetupResult|
            post|/api/mfa/totp/activate|activateTotp||TotpActivationResult|code:query:true:string::
            post|/api/mfa/sms/enable|enableSms||ManagedMfaConfigResult|phoneNumber:query:true:string::
            post|/api/mfa/email/enable|enableEmail||ManagedMfaConfigResult|email:query:true:string::
            delete|/api/mfa/{type}|disableManagedMfa||EmptyResult|type:path:true:string::
            post|/api/mfa/verify|verifyManagedMfaCode||ManagedMfaBooleanResult|code:query:true:string::,type:query:true:string::
            post|/api/mfa/send-code|sendMfaCode||EmptyResult|type:query:true:string::
            get|/api/mfa/configs|getUserMfaConfigs||MfaConfig[]|
            get|/api/mfa/status|checkMfaStatus||ManagedMfaBooleanResult|
            post|/api/mfa/backup-codes/regenerate|regenerateBackupCodes||ManagedBackupCodesResult|
            post|/api/mfa/backup-codes/verify|verifyManagedBackupCode||ManagedMfaBooleanResult|code:query:true:string::
            post|/api/mfa/unlock|unlockMfa||EmptyResult|userId:query:true:string::,type:query:true:string::
            post|/api/ip-access-control/whitelist|addToWhitelist||IpRuleResult|ipAddress:query:true:string::,description:query:false:string::
            post|/api/ip-access-control/blacklist|addToBlacklist||IpRuleResult|ipAddress:query:true:string::,description:query:false:string::,durationMinutes:query:false:integer:int32:
            delete|/api/ip-access-control/whitelist|removeFromWhitelist||EmptyResult|ipAddress:query:true:string::
            delete|/api/ip-access-control/blacklist|removeFromBlacklist||EmptyResult|ipAddress:query:true:string::
            get|/api/ip-access-control/whitelist|getWhitelist||IpAccessControl[]|
            get|/api/ip-access-control/blacklist|getBlacklist||IpAccessControl[]|
            put|/api/ip-access-control/{ruleId}/enable|enableRule||EmptyResult|ruleId:path:true:integer:int64:
            put|/api/ip-access-control/{ruleId}/disable|disableRule||EmptyResult|ruleId:path:true:integer:int64:
            delete|/api/ip-access-control/{ruleId}|deleteRule||EmptyResult|ruleId:path:true:integer:int64:
            post|/api/ip-access-control/cleanup|cleanupExpiredRules||CountResult|
            post|/api/ip-access-control/check|checkAccess||IpAccessResult|ipAddress:query:true:string::
            get|/auth/v1/authorization/users/{userId}|queryUnifiedAuthorization||UnifiedAuthorizationResult|userId:path:true:integer:int64:
            post|/auth/v1/login|login|LoginRequest|LoginResponse|
            post|/auth/v1/logout/{userId}|logout|||userId:path:true:integer:int64:
            post|/auth/v1/mfa/verify|verifyMfaChallenge|MfaVerifyRequest|LoginResponse|
            post|/auth/v1/refresh|refreshToken|RefreshTokenRequest|TokenPair|
            post|/auth/v2/password-recovery/requests|initiatePasswordRecovery|PasswordRecoveryInitiateRequest|PasswordRecoveryInitiateResult|
            post|/auth/v2/password-recovery/verifications|verifyPasswordRecovery|PasswordRecoveryVerifyRequest|PasswordRecoveryVerifyResult|
            post|/auth/v2/password-recovery/confirmations|confirmPasswordRecovery|PasswordRecoveryConfirmRequest|PasswordRecoveryConfirmResult|
            post|/auth/v1/roles|createRole|RoleDTO|RoleResult|
            get|/auth/v1/roles|listRoles||RoleListResult|
            post|/auth/v1/roles/assign-permissions|assignPermissions|AssignPermissionsRequest|EmptyResult|
            put|/auth/v1/roles/{id}|updateRole|RoleDTO|RoleResult|id:path:true:integer:int64:
            delete|/auth/v1/roles/{id}|deleteRole||EmptyResult|id:path:true:integer:int64:
            get|/auth/v1/roles/{id}|getRoleById||RoleResult|id:path:true:integer:int64:
            get|/auth/v1/roles/{id}/permissions|getRolePermissions||PermissionIdsResult|id:path:true:integer:int64:
            post|/auth/v1/permissions|createPermission|PermissionDTO|PermissionResult|
            get|/auth/v1/permissions|listPermissions||PermissionListResult|
            get|/auth/v1/permissions/resource/{resource}|getPermissionsByResource||PermissionListResult|resource:path:true:string::
            put|/auth/v1/permissions/{id}|updatePermission|PermissionDTO|PermissionResult|id:path:true:integer:int64:
            delete|/auth/v1/permissions/{id}|deletePermission||EmptyResult|id:path:true:integer:int64:
            get|/auth/v1/permissions/{id}|getPermissionById||PermissionResult|id:path:true:integer:int64:
            get|/auth/v1/sessions/user/{userId}|getUserSessions||SessionListResult|userId:path:true:integer:int64:,tenantId:query:false:string::default
            delete|/auth/v1/sessions/user/{userId}|terminateUserSessions||EmptyResult|userId:path:true:integer:int64:,tenantId:query:false:string::default
            get|/auth/v1/sessions/online|getOnlineUsers||OnlineSessionsResult|
            get|/auth/v1/sessions/statistics|getOnlineStatistics||OnlineStatisticsResult|
            delete|/auth/v1/sessions/{sessionId}|terminateSession||EmptyResult|sessionId:path:true:string::
            get|/auth/v1/sessions/{sessionId}|getSessionById||SessionResult|sessionId:path:true:integer:int64:
            post|/api/v1/mfa/setup|setupMfa|MfaSetupRequest|MfaSetupResult|
            post|/api/v1/mfa/verify|verifyAndEnableMfa|MfaVerifyRequest|MfaEnabledResult|
            post|/api/v1/mfa/verify-code|verifyMfaCode|MfaVerifyRequest|MfaVerifiedResult|
            post|/api/v1/mfa/verify-backup|verifyBackupCode|MfaVerifyRequest|MfaVerifiedResult|
            post|/api/v1/mfa/disable|disableMfa|MfaVerifyRequest|MfaDisabledResult|
            get|/api/v1/mfa/status|getMfaStatus||MfaStatusResult|userId:query:true:integer:int64:,tenantId:query:true:string::
            post|/api/v1/auth/configs|createAuthConfig|CreateAuthConfigRequest|AuthConfigResult|
            put|/api/v1/auth/configs/{id}|updateAuthConfig|UpdateAuthConfigRequest|AuthConfigResult|id:path:true:integer:int64:,tenantId:query:true:string::
            delete|/api/v1/auth/configs/{id}|deleteAuthConfig||EmptyResult|id:path:true:integer:int64:,tenantId:query:true:string::
            get|/api/v1/auth/configs/effective|getEffectiveAuthConfig||AuthConfigResult|configType:query:true:string::,configKey:query:true:string::,tenantId:query:true:string::
            get|/api/v1/auth/configs/type/{type}|getAuthConfigsByType||AuthConfigListResult|type:path:true:string::,tenantId:query:true:string::
            get|/api/v1/auth/configs|listAuthConfigs||AuthConfigListResult|tenantId:query:true:string::
            get|/api/v1/auth/configs/{id}|getAuthConfig||AuthConfigResult|id:path:true:integer:int64:,tenantId:query:true:string::
            get|/api/v1/auth/audit-logs/failed|listFailedAuditLogs||AuditLogListResult|tenantId:query:true:string::
            get|/api/v1/auth/audit-logs/high-risk|listHighRiskAuditLogs||AuditLogListResult|tenantId:query:true:string::
            get|/api/v1/auth/audit-logs/user/{userId}|listUserAuditLogs||AuditLogListResult|userId:path:true:integer:int64:,tenantId:query:true:string::
            get|/api/v1/auth/audit-logs/event-type/{eventType}|listAuditLogsByEventType||AuditLogListResult|eventType:path:true:string::,tenantId:query:true:string::
            get|/api/v1/auth/audit-logs/ip/{ipAddress}|listAuditLogsByIpAddress||AuditLogListResult|ipAddress:path:true:string::,tenantId:query:true:string::
            get|/api/v1/auth/audit-logs/user/{userId}/login-failures|countUserLoginFailures||CountResult|userId:path:true:integer:int64:,tenantId:query:true:string::,startTime:query:true:string::
            post|/api/v1/auth/audit-logs/time-range|listAuditLogsByTimeRange|AuditLogQueryRequest|AuditLogListResult|
            post|/api/v1/auth/audit-logs/count|countAuditLogs|AuditLogQueryRequest|CountResult|
            delete|/api/v1/auth/audit-logs/cleanup|cleanupExpiredAuditLogs||CountResult|tenantId:query:true:string::,beforeTime:query:true:string::
            get|/api/v1/auth/audit-logs/{id}|getAuditLog||AuditLogResult|id:path:true:integer:int64:
            """;
    private final WebTestClient client;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String password = UUID.randomUUID().toString();
    private JsonNode document;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID().toString() + UUID.randomUUID());
        registry.add(
                "spring.datasource.url",
                () -> "jdbc:h2:mem:authdocs;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add(
                "spring.r2dbc.url",
                () -> "r2dbc:h2:mem:///authdocs;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
    }

    @BeforeAll
    void fetchDocumentThroughRealSecurityChain() {
        jdbc.update(
                "INSERT INTO auth_user(id,tenant_id,username,password_hash,status,failed_login_attempts) VALUES(91001,'default','openapi-user',?,1,0)",
                encoder.encode(password));
        JsonNode login = login();
        document = client.mutate()
                .responseTimeout(Duration.ofSeconds(30))
                .build()
                .get()
                .uri("/v3/api-docs")
                .headers(headers ->
                        headers.setBearerAuth(login.path("accessToken").asText()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(document).isNotNull();
    }

    @Test
    void allSixtyNineOperationsMatchExplicitContracts() {
        Set<String> actual = new HashSet<>();
        Set<String> operationIds = new HashSet<>();
        document.path("paths")
                .fields()
                .forEachRemaining(path -> path.getValue().fields().forEachRemaining(method -> {
                    if (Set.of("get", "post", "put", "delete", "patch").contains(method.getKey())) {
                        if (!path.getKey().startsWith("/oauth2/")
                                && !path.getKey().startsWith("/.well-known/")
                                && !path.getKey().startsWith("/internal/")) {
                            actual.add(method.getKey() + " " + path.getKey());
                        }
                        assertThat(operationIds.add(
                                        method.getValue().path("operationId").asText()))
                                .isTrue();
                    }
                }));
        Set<String> expected = new HashSet<>();
        CONTRACTS.lines().filter(line -> !line.isBlank()).forEach(line -> {
            String[] contract = line.strip().split("[|]", -1);
            expected.add(contract[0] + " " + contract[1]);
            assertOperationContract(contract);
        });
        assertThat(expected).hasSize(73);
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        assertLocalReferencesResolve(document);
    }

    @Test
    void unifiedAuthorizationDocumentsPathSecurityAndSchema() {
        JsonNode path = document.path("paths").path("/auth/v1/authorization/users/{userId}");
        assertThat(path.fieldNames()).toIterable().containsExactly("get");
        JsonNode operation = path.path("get");
        assertParameters(operation, "userId:path:true:integer:int64:");
        assertThat(operation.path("parameters").path(0).at("/schema/minimum").asInt())
                .isEqualTo(1);
        assertThat(operation.path("security")).hasSize(1);
        JsonNode requirement = operation.path("security").path(0);
        assertThat(requirement.fieldNames()).toIterable().containsExactly("bearerAuth");
        assertThat(requirement.path("bearerAuth").isArray()).isTrue();
        assertThat(requirement.path("bearerAuth")).isEmpty();
        assertThat(operation.path("responses").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("200", "400", "401", "403");
        for (String status : new String[] {"401"}) {
            assertThat(operation.path("responses").path(status).has("content")).isFalse();
        }
        for (String status : new String[] {"400", "403"}) {
            assertThat(operation
                            .path("responses")
                            .path(status)
                            .at("/content/application~1json/schema/$ref")
                            .asText())
                    .isEqualTo("#/components/schemas/Result");
        }
        assertEnvelope("UnifiedAuthorizationResult");
        assertThat(property(schema("UnifiedAuthorizationResult"), "data")
                        .path("$ref")
                        .asText())
                .isEqualTo("#/components/schemas/UnifiedAuthorizationResponse");
        JsonNode snapshot = schema("UnifiedAuthorizationResponse");
        assertThat(snapshot.path("properties").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("tenantId", "userId", "permissions", "roles");
        assertThat(property(snapshot, "tenantId").path("type").asText()).isEqualTo("string");
        assertThat(property(snapshot, "userId").path("type").asText()).isEqualTo("integer");
        assertThat(property(snapshot, "userId").path("format").asText()).isEqualTo("int64");
        assertThat(property(snapshot, "permissions").path("type").asText()).isEqualTo("array");
        assertThat(property(snapshot, "permissions").at("/items/type").asText()).isEqualTo("string");
        assertThat(property(snapshot, "roles").path("type").asText()).isEqualTo("array");
        String roleRef = property(snapshot, "roles").at("/items/$ref").asText();
        assertThat(roleRef).startsWith("#/components/schemas/");
        JsonNode role = document.at(roleRef.substring(1));
        assertThat(role.path("properties").fieldNames()).toIterable().containsExactlyInAnyOrder("code", "dataScope");
        for (String field : new String[] {"code", "dataScope"}) {
            assertThat(property(role, field).path("type").asText()).isEqualTo("string");
        }
    }

    @Test
    void oauthProtocolOperationsAreDocumented() {
        Map.of(
                        "/oauth2/authorize",
                        "get",
                        "/oauth2/token",
                        "post",
                        "/oauth2/revoke",
                        "post",
                        "/oauth2/introspect",
                        "post")
                .forEach((path, method) -> {
                    JsonNode operation = document.path("paths").path(path).path(method);
                    assertThat(operation.path("operationId").asText()).isNotBlank();
                    assertThat(operation.path("summary").asText()).isNotBlank();
                });
        JsonNode authorize = document.path("paths").path("/oauth2/authorize").path("get");
        assertAuthorizeParameters(authorize);
        assertThat(authorize.path("responses").has("200")).isFalse();
        JsonNode redirect = authorize.path("responses").path("302");
        assertThat(redirect.has("content")).isFalse();
        assertThat(redirect.at("/headers/Location/schema/format").asText()).isEqualTo("uri");
        for (String endpoint : new String[] {"token", "revoke", "introspect"}) {
            JsonNode operation =
                    document.path("paths").path("/oauth2/" + endpoint).path("post");
            assertThat(operation
                            .at("/requestBody/content/application~1x-www-form-urlencoded/schema/$ref")
                            .asText())
                    .startsWith("#/components/schemas/");
            assertThat(operation
                            .at("/responses/400/content/application~1json/schema/$ref")
                            .asText())
                    .isEqualTo("#/components/schemas/OAuthError");
        }
        assertThat(schema("TokenForm").path("properties").has("grant_type")).isTrue();
        assertThat(schema("TokenForm").path("required").toString()).contains("grant_type");
        assertThat(schema("TokenResponse").path("properties").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("access_token", "token_type", "expires_in", "refresh_token", "scope");
        assertThat(schema("IntrospectionResponse").path("properties").has("active"))
                .isTrue();
        assertThat(schema("OAuthError").path("properties").fieldNames())
                .toIterable()
                .containsExactly("error");
    }

    @Test
    void internalContractOperationsAreDocumented() {
        Map.of("/internal/v1/users/{accountId}/permissions", "get", "/internal/v1/tokens/validate", "post")
                .forEach((path, method) -> {
                    JsonNode operation = document.path("paths").path(path).path(method);
                    assertThat(operation.path("operationId").asText()).isNotBlank();
                    assertThat(operation.path("summary").asText()).isNotBlank();
                    assertThat(operation.path("description").asText()).isNotBlank();
                    assertThat(operation.path("tags").toString()).contains("内部契约");
                });
        // 内部契约路由虽在服务端 OpenAPI 文档中可见，但不属于对外公共契约（不计入 69 个外部操作）
        assertThat(document.path("paths").has("/internal/v1/users/{accountId}/permissions"))
                .isTrue();
        assertThat(document.path("paths").has("/internal/v1/tokens/validate")).isTrue();
    }

    @Test
    void wrappedResponsesRetainTheirConcreteDataTypes() {
        Map.of(
                        "RoleResult", "RoleDTO",
                        "PermissionResult", "PermissionDTO",
                        "SessionResult", "SessionDTO",
                        "AuthConfigResult", "AuthConfigDTO",
                        "AuditLogResult", "AuditLogDTO",
                        "MfaSetupResult", "MfaSetupResponse",
                        "OnlineStatisticsResult", "OnlineStatistics",
                        "MfaStatusResult", "MfaStatus")
                .forEach((wrapper, dataType) -> {
                    assertEnvelope(wrapper);
                    assertThat(property(schema(wrapper), "data").path("$ref").asText())
                            .endsWith("/" + dataType);
                });
        Map.of(
                        "RoleListResult", "RoleDTO",
                        "PermissionListResult", "PermissionDTO",
                        "SessionListResult", "SessionDTO",
                        "AuthConfigListResult", "AuthConfigDTO",
                        "AuditLogListResult", "AuditLogDTO",
                        "OnlineSessionsResult", "SessionEntity")
                .forEach((wrapper, itemType) -> {
                    assertEnvelope(wrapper);
                    JsonNode data = property(schema(wrapper), "data");
                    assertThat(data.path("type").asText()).isEqualTo("array");
                    assertThat(data.at("/items/$ref").asText()).endsWith("/" + itemType);
                });
        assertThat(property(schema("PermissionIdsResult"), "data")
                        .at("/items/type")
                        .asText())
                .isEqualTo("integer");
        assertThat(property(schema("PermissionIdsResult"), "data")
                        .at("/items/format")
                        .asText())
                .isEqualTo("int64");
        assertThat(property(schema("CountResult"), "data").path("format").asText())
                .isEqualTo("int64");
        Map.of("MfaEnabledResult", "MfaEnabled", "MfaVerifiedResult", "MfaVerified", "MfaDisabledResult", "MfaDisabled")
                .forEach((wrapper, dataType) -> {
                    assertEnvelope(wrapper);
                    assertThat(property(schema(wrapper), "data").path("$ref").asText())
                            .endsWith("/" + dataType);
                });
        assertThat(property(schema("MfaEnabled"), "enabled").path("type").asText())
                .isEqualTo("boolean");
        assertThat(property(schema("MfaVerified"), "verified").path("type").asText())
                .isEqualTo("boolean");
        assertThat(property(schema("MfaDisabled"), "disabled").path("type").asText())
                .isEqualTo("boolean");
        assertThat(property(schema("MfaStatus"), "mfaType").path("type").asText())
                .isEqualTo("string");
        assertThat(property(schema("OnlineStatistics"), "totalOnlineUsers")
                        .path("format")
                        .asText())
                .isEqualTo("int64");
        assertThat(property(schema("OnlineStatistics"), "timestamp")
                        .path("format")
                        .asText())
                .isEqualTo("date-time");
    }

    @Test
    void authenticationSchemasDescribeCurrentSecurityAndUnwrappedResponses() {
        assertThat(document.at("/components/securitySchemes/bearerAuth/type").asText())
                .isEqualTo("http");
        assertThat(document.at("/components/securitySchemes/bearerAuth/scheme").asText())
                .isEqualTo("bearer");
        assertThat(schema("LoginRequest").path("required").toString()).contains("username", "password");
        assertThat(property(schema("LoginRequest"), "password")
                        .path("writeOnly")
                        .asBoolean())
                .isTrue();
        assertThat(property(schema("LoginRequest"), "tenantId")
                        .path("description")
                        .asText())
                .contains("可信登录租户", "不能");
        assertThat(schema("RefreshTokenRequest").path("required").toString()).contains("refreshToken");
        assertThat(schema("LoginResponse").path("properties").has("code")).isFalse();
        assertThat(schema("TokenPair").path("properties").has("data")).isFalse();
        // 6 个字段：accessToken / accessTokenId / refreshToken / refreshTokenJti / expiresIn / tokenType
        // accessTokenId、refreshTokenJti 为撤销传播（A3）所需的令牌标识，随统一认证收口加入
        assertThat(schema("TokenPair").path("properties").size()).isEqualTo(6);
        assertThat(property(schema("TokenPair"), "expiresIn").path("format").asText())
                .isEqualTo("int64");
        for (String endpoint : new String[] {"login", "refresh"}) {
            JsonNode operation =
                    document.path("paths").path("/auth/v1/" + endpoint).path("post");
            for (String status : new String[] {"401", "500"}) {
                assertThat(operation
                                .path("responses")
                                .path(status)
                                .at("/content/text~1plain/schema/type")
                                .asText())
                        .isEqualTo("string");
            }
        }
        JsonNode logout =
                document.path("paths").path("/auth/v1/logout/{userId}").path("post");
        assertThat(logout.path("description").asText()).contains("认证主体", "全部令牌", "忽略外部");
        assertThat(logout.path("responses").path("403").has("content")).isFalse();
        assertThat(logout.has("requestBody")).isFalse();
    }

    @Test
    void actualAuthenticationResponsesAgreeWithDocumentation() {
        JsonNode login = login();
        assertThat(login.has("code")).isFalse();
        assertThat(login.path("userId").asLong()).isEqualTo(91001L);
        JsonNode refreshed = client.post()
                .uri("/auth/v1/refresh")
                .bodyValue(Map.of("refreshToken", login.path("refreshToken").asText()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
        assertThat(refreshed).isNotNull();
        // 与 TokenPair schema 字段数一致（含撤销传播所需的 accessTokenId / refreshTokenJti）
        assertThat(refreshed.size()).isEqualTo(6);
        assertThat(refreshed.path("refreshToken").asText())
                .isNotEqualTo(login.path("refreshToken").asText());
        client.post()
                .uri("/auth/v1/refresh")
                .bodyValue(Map.of("refreshToken", login.path("refreshToken").asText()))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        // 重放检测已撤销该用户全部令牌，登出授权边界需使用重新登录后的新访问令牌。
        String access = login().path("accessToken").asText();
        client.post()
                .uri("/auth/v1/logout/91002")
                .headers(headers -> headers.setBearerAuth(access))
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody()
                .isEmpty();
        client.post()
                .uri("/auth/v1/logout/91001?tenantId=other")
                .header("X-Tenant-Id", "other")
                .headers(headers -> headers.setBearerAuth(access))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .isEmpty();
    }

    @Test
    void documentationEndpointKeepsAuthenticationRequirement() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isUnauthorized();
        JsonNode login = login();
        client.get()
                .uri("/v3/api-docs")
                .headers(headers ->
                        headers.setBearerAuth(login.path("refreshToken").asText()))
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    /**
     * 校验实际授权端点文档的七项查询参数及 PKCE 约束，避免参数遗漏或重复。
     *
     * @param authorize 授权端点的 OpenAPI 操作
     */
    private void assertAuthorizeParameters(JsonNode authorize) {
        assertParameters(
                authorize,
                "client_id:query:true:string::,response_type:query:true:string::,redirect_uri:query:true:string:uri:,"
                        + "scope:query:false:string::,state:query:false:string::,"
                        + "code_challenge:query:true:string::,code_challenge_method:query:true:string::");
        Set<String> names = new HashSet<>();
        for (JsonNode parameter : authorize.path("parameters")) {
            assertThat(names.add(parameter.path("name").asText())).isTrue();
            if ("code_challenge".equals(parameter.path("name").asText())) {
                assertThat(parameter.at("/schema/pattern").asText()).isEqualTo("[A-Za-z0-9_-]{43}");
            } else if ("code_challenge_method".equals(parameter.path("name").asText())) {
                assertThat(parameter.at("/schema/enum")).hasSize(1);
                assertThat(parameter.at("/schema/enum/0").asText()).isEqualTo("S256");
            }
        }
    }

    private void assertOperationContract(String[] contract) {
        JsonNode operation = document.path("paths").path(contract[1]).path(contract[0]);
        assertThat(operation.path("operationId").asText()).as(contract[1]).isEqualTo(contract[2]);
        assertThat(operation.path("summary").asText()).isNotBlank();
        assertThat(operation.path("description").asText()).isNotBlank();
        assertParameters(operation, contract[5]);
        assertBody(operation, contract[3]);
        JsonNode success = operation.path("responses").path("200");
        if (contract[4].isEmpty()) {
            assertThat(success.has("content")).isFalse();
        } else if ("object".equals(contract[4])) {
            // 匿名对象载荷（如开放应用摘要列表）：schema 为 additionalProperties=true 的内联对象，
            // 无需为无稳定类型的分页/摘要结构强造 DTO
            assertThat(success.at("/content/application~1json/schema/additionalProperties")
                            .asBoolean())
                    .as("200 response of " + contract[1] + " => " + success)
                    .isTrue();
        } else if (contract[4].endsWith("[]")) {
            JsonNode array = success.at("/content/application~1json/schema");
            assertThat(array.path("type").asText()).isEqualTo("array");
            assertThat(array.at("/items/$ref").asText())
                    .isEqualTo("#/components/schemas/" + contract[4].replace("[]", ""));
        } else {
            assertThat(success.at("/content/application~1json/schema/$ref").asText())
                    .isEqualTo("#/components/schemas/" + contract[4]);
        }
        if (Set.of(
                        "login",
                        "verifyMfaChallenge",
                        "refreshToken",
                        "initiatePasswordRecovery",
                        "verifyPasswordRecovery",
                        "confirmPasswordRecovery")
                .contains(contract[2])) {
            assertThat(operation.path("security").isMissingNode()
                            || operation.path("security").isEmpty())
                    .isTrue();
        } else if (contract[1].startsWith("/api/mfa/") || contract[1].startsWith("/api/ip-access-control/")) {
            for (JsonNode parameter : operation.path("parameters")) {
                assertThat(parameter.path("name").asText()).isNotEqualTo("tenantId");
            }
            assertThat(operation.path("responses").has("500")).isTrue();
        } else {
            assertThat(operation.path("security").get(0).has("bearerAuth")).isTrue();
            assertThat(operation.path("responses").path("401").has("content")).isFalse();
        }
    }

    private JsonNode login() {
        return client.post()
                .uri("/auth/v1/login")
                .bodyValue(Map.of("username", "openapi-user", "password", password))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(JsonNode.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertParameters(JsonNode operation, String expected) {
        String[] parameters = expected.isEmpty() ? new String[0] : expected.split(",");
        assertThat(operation.path("parameters").size()).isEqualTo(parameters.length);
        for (String entry : parameters) {
            String[] fields = entry.split(":", -1);
            JsonNode parameter = null;
            for (JsonNode candidate : operation.path("parameters")) {
                if (candidate.path("name").asText().equals(fields[0])) {
                    parameter = candidate;
                    break;
                }
            }
            assertThat(parameter).as(fields[0]).isNotNull();
            assertThat(parameter.path("in").asText()).isEqualTo(fields[1]);
            assertThat(parameter.path("required").asBoolean()).isEqualTo(Boolean.parseBoolean(fields[2]));
            assertThat(parameter.at("/schema/type").asText()).isEqualTo(fields[3]);
            assertThat(parameter.at("/schema/format").asText()).isEqualTo(fields[4]);
            assertThat(parameter.at("/schema/default").asText()).isEqualTo(fields[5]);
            assertThat(parameter.path("description").asText()).isNotBlank();
        }
    }

    private void assertBody(JsonNode operation, String body) {
        if (body.isEmpty()) {
            assertThat(operation.has("requestBody")).isFalse();
        } else {
            assertThat(operation.at("/requestBody/required").asBoolean()).isTrue();
            assertThat(operation
                            .at("/requestBody/content/application~1json/schema/$ref")
                            .asText())
                    .isEqualTo("#/components/schemas/" + body);
        }
    }

    private void assertEnvelope(String name) {
        for (String field : new String[] {"code", "msg", "traceId", "data"}) {
            assertThat(property(schema(name), field).isMissingNode())
                    .as(name + "." + field)
                    .isFalse();
        }
    }

    private JsonNode schema(String name) {
        return document.path("components").path("schemas").path(name);
    }

    private JsonNode property(JsonNode schema, String name) {
        if (schema.has("$ref")) {
            return property(document.at(schema.path("$ref").asText().substring(1)), name);
        }
        if (schema.path("properties").has(name)) {
            return schema.path("properties").path(name);
        }
        for (JsonNode component : schema.path("allOf")) {
            JsonNode property = property(component, name);
            if (!property.isMissingNode()) {
                return property;
            }
        }
        return schema.path("properties").path(name);
    }

    private void assertLocalReferencesResolve(JsonNode node) {
        if (node.has("$ref") && node.path("$ref").asText().startsWith("#/")) {
            assertThat(document.at(node.path("$ref").asText().substring(1)).isMissingNode())
                    .as(node.toString())
                    .isFalse();
        }
        node.elements().forEachRemaining(this::assertLocalReferencesResolve);
    }
}
