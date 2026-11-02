package com.apex.scaffold.application.openplatform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.OAuth2ClientRequest;
import com.apex.scaffold.application.dto.OAuth2ClientResponse;
import com.apex.scaffold.application.dto.OpenAppRequest;
import com.apex.scaffold.application.dto.OpenAppResponse;
import com.apex.scaffold.application.service.OAuth2ClientManagementService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link OpenAppAdminService} 服务层单元测试（D2）.
 *
 * <p>复用真实 {@link OpenPlatformScopeRegistry}（内置目录）与 {@link ObjectMapper}，
 * 仅模拟 {@link OAuth2ClientManagementService} 与 {@link OAuth2RegisteredClientRepository}，
 * 校验开放应用注册的 scope 越权拒绝、密钥处理、元数据合并（{@code openPlatform.*} 平铺键）
 * 与既有口径（状态保持/分页钳制/NOT_FOUND）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@DisplayName("OpenAppAdminService 开放应用管理服务测试")
class OpenAppAdminServiceTest {

    private static final String CLIENT_ID = "partner-data-platform";

    private OAuth2ClientManagementService managementService;
    private OAuth2RegisteredClientRepository clientRepository;
    private OpenAppAdminService service;

    @BeforeEach
    void setUp() {
        managementService = mock(OAuth2ClientManagementService.class);
        clientRepository = mock(OAuth2RegisteredClientRepository.class);
        service = new OpenAppAdminService(
                managementService, clientRepository, new OpenPlatformScopeRegistry(), new ObjectMapper());
    }

    private OpenAppRequest.OpenAppRequestBuilder request() {
        return OpenAppRequest.builder()
                .clientId(CLIENT_ID)
                .appName("合作方数据平台")
                .scopes(Set.of("open:file:read", "open:workflow:read"))
                .redirectUris(Set.of("https://partner.example.com/oauth/callback"))
                .description("对接文件与工作流开放接口")
                .contact("partner@example.com")
                .rateLimitTier("standard");
    }

    /** 构造带空设置 JSON 的客户端实体（mergeMetadata 的落库对象）。 */
    private OAuth2RegisteredClientEntity entity(String settingsJson) {
        OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
        entity.setId("id-1");
        entity.setClientId(CLIENT_ID);
        entity.setClientName("合作方数据平台");
        entity.setClientSettings(settingsJson);
        entity.setScopes(Set.of("open:file:read", "open:workflow:read"));
        entity.setRedirectUris(Set.of("https://partner.example.com/oauth/callback"));
        return entity;
    }

    @Test
    @DisplayName("创建应用应拒绝未登记 scope 并列出非法清单（scope 越权反例）")
    void shouldRejectUnknownScopes() {
        OpenAppRequest request = request()
                .scopes(Set.of("open:file:read", "open:file:delete", "bogus:scope"))
                .build();

        assertThatThrownBy(() -> service.createApp(request).block())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("open:file:delete")
                .hasMessageContaining("bogus:scope");

        verifyNoInteractions(managementService, clientRepository);
    }

    @Test
    @DisplayName("创建应用缺少 clientId 或 appName 应返回参数错误")
    void shouldRequireClientIdAndAppNameOnCreate() {
        assertThatThrownBy(() ->
                        service.createApp(request().clientId(null).build()).block())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clientId");
        assertThatThrownBy(
                        () -> service.createApp(request().appName(" ").build()).block())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appName");

        verifyNoInteractions(managementService, clientRepository);
    }

    @Test
    @DisplayName("创建应用状态非 ENABLED/DISABLED 应返回参数错误")
    void shouldRejectInvalidStatus() {
        assertThatThrownBy(() ->
                        service.createApp(request().status("PAUSED").build()).block())
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getCode())
                .isEqualTo(ResultCode.PARAM_ERROR.getCode());

        verifyNoInteractions(managementService, clientRepository);
    }

    @Test
    @DisplayName("创建成功应注册 client_credentials 客户端并合并开放元数据")
    void shouldCreateClientAndMergeMetadata() {
        when(managementService.createClient(any(OAuth2ClientRequest.class)))
                .thenReturn(Mono.just(mock(OAuth2ClientResponse.class)));
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.just(entity("{}")));
        when(clientRepository.save(any(OAuth2RegisteredClientEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, OAuth2RegisteredClientEntity.class)));

        OpenAppResponse response = service.createApp(request().build()).block();

        ArgumentCaptor<OAuth2ClientRequest> captor = ArgumentCaptor.forClass(OAuth2ClientRequest.class);
        verify(managementService).createClient(captor.capture());
        OAuth2ClientRequest base = captor.getValue();
        assertThat(base.getClientId()).isEqualTo(CLIENT_ID);
        assertThat(base.getClientName()).isEqualTo("合作方数据平台");
        assertThat(base.getClientAuthenticationMethods()).containsExactly("client_secret_basic");
        assertThat(base.getAuthorizationGrantTypes()).containsExactly("client_credentials");
        assertThat(base.getScopes()).containsExactlyInAnyOrder("open:file:read", "open:workflow:read");
        assertThat(base.getClientSecret()).isNotBlank();

        ArgumentCaptor<OAuth2RegisteredClientEntity> saver =
                ArgumentCaptor.forClass(OAuth2RegisteredClientEntity.class);
        verify(clientRepository).save(saver.capture());
        String settings = saver.getValue().getClientSettings();
        assertThat(settings)
                .contains("\"openPlatform.appName\":\"合作方数据平台\"")
                .contains("\"openPlatform.status\":\"ENABLED\"")
                .contains("\"openPlatform.createdAt\"");

        assertThat(response).isNotNull();
        assertThat(response.getClientSecret()).isEqualTo(base.getClientSecret());
        assertThat(response.getStatus()).isEqualTo("ENABLED");
        assertThat(response.getScopes()).containsExactlyInAnyOrder("open:file:read", "open:workflow:read");
    }

    @Test
    @DisplayName("创建应用未提供密钥时应自动生成，提供时应原样透传且状态小写归一")
    void shouldResolveSecretAndNormalizeStatus() {
        when(managementService.createClient(any(OAuth2ClientRequest.class)))
                .thenReturn(Mono.just(mock(OAuth2ClientResponse.class)));
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.just(entity("{}")));
        when(clientRepository.save(any(OAuth2RegisteredClientEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, OAuth2RegisteredClientEntity.class)));

        OpenAppResponse response = service.createApp(
                        request().clientSecret("my-secret").status("disabled").build())
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getClientSecret()).isEqualTo("my-secret");
        assertThat(response.getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("更新应用时客户端不存在应返回 NOT_FOUND")
    void shouldReturnNotFoundOnUpdateMissingClient() {
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.empty());
        // updateClient 在 Mono 装配期即被调用，需提供占位响应避免 NPE；空上游时不会被订阅
        when(managementService.updateClient(eq(CLIENT_ID), any(OAuth2ClientRequest.class)))
                .thenReturn(Mono.just(mock(OAuth2ClientResponse.class)));

        StepVerifier.create(service.updateApp(CLIENT_ID, request().build()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
                })
                .verify();
    }

    @Test
    @DisplayName("更新应用缺省状态时应保留既有状态并按需改写元数据")
    void shouldPreserveExistingStatusOnUpdate() {
        OAuth2RegisteredClientEntity existing =
                entity("{\"openPlatform.appName\":\"旧名\",\"openPlatform.status\":\"DISABLED\","
                        + "\"openPlatform.createdAt\":\"2026-01-01T00:00:00\"}");
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.just(existing));
        when(managementService.updateClient(eq(CLIENT_ID), any(OAuth2ClientRequest.class)))
                .thenReturn(Mono.just(mock(OAuth2ClientResponse.class)));
        when(clientRepository.save(any(OAuth2RegisteredClientEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0, OAuth2RegisteredClientEntity.class)));

        OpenAppRequest update =
                request().appName("新名").scopes(null).redirectUris(null).build();
        OpenAppResponse response = service.updateApp(CLIENT_ID, update).block();

        ArgumentCaptor<OAuth2RegisteredClientEntity> saver =
                ArgumentCaptor.forClass(OAuth2RegisteredClientEntity.class);
        verify(clientRepository).save(saver.capture());
        String settings = saver.getValue().getClientSettings();
        assertThat(settings)
                .contains("\"openPlatform.appName\":\"新名\"")
                .contains("\"openPlatform.status\":\"DISABLED\"")
                .contains("\"openPlatform.createdAt\":\"2026-01-01T00:00:00\"");

        verify(managementService).updateClient(eq(CLIENT_ID), any(OAuth2ClientRequest.class));
        assertThat(response).isNotNull();
        assertThat(response.getAppName()).isEqualTo("新名");
        assertThat(response.getStatus()).isEqualTo("DISABLED");
        assertThat(response.getClientSecret()).isNull();
    }

    @Test
    @DisplayName("详情查询应解析开放元数据且不返回密钥；未开通元数据的客户端回退客户端名")
    void shouldParseMetadataAndFallback() {
        OAuth2RegisteredClientEntity open =
                entity("{\"openPlatform.appName\":\"合作方数据平台\",\"openPlatform.description\":\"对接开放接口\","
                        + "\"openPlatform.contact\":\"partner@example.com\",\"openPlatform.rateLimitTier\":\"standard\","
                        + "\"openPlatform.status\":\"ENABLED\",\"openPlatform.createdAt\":\"2026-01-01T00:00:00\"}");
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.just(open));

        OpenAppResponse response = service.getApp(CLIENT_ID).block();

        assertThat(response).isNotNull();
        assertThat(response.getAppName()).isEqualTo("合作方数据平台");
        assertThat(response.getDescription()).isEqualTo("对接开放接口");
        assertThat(response.getContact()).isEqualTo("partner@example.com");
        assertThat(response.getRateLimitTier()).isEqualTo("standard");
        assertThat(response.getStatus()).isEqualTo("ENABLED");
        assertThat(response.getCreatedAt()).isEqualTo("2026-01-01T00:00:00");
        assertThat(response.getClientSecret()).isNull();
    }

    @Test
    @DisplayName("详情查询客户端不存在应返回 NOT_FOUND")
    void shouldReturnNotFoundOnGetMissingClient() {
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.getApp(CLIENT_ID).block())
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("分页查询应钳制到 page>=0、1<=size<=100 并回退客户端名")
    void shouldClampPaginationAndFallbackName() {
        OAuth2RegisteredClientEntity plain = entity("{}");
        plain.setClientName("普通客户端");
        when(clientRepository.findAll(anyInt(), anyInt())).thenReturn(Flux.just(plain));

        List<OpenAppResponse> responses =
                service.listApps(-5, 999).collectList().block();

        verify(clientRepository).findAll(0, 100);
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getAppName()).isEqualTo("普通客户端");
        assertThat(responses.get(0).getStatus()).isNull();
    }

    @Test
    @DisplayName("重置密钥应委托既有客户端管理服务并在响应返回一次性明文")
    void shouldDelegateSecretReset() {
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.just(entity("{}")));
        when(managementService.resetClientSecret(CLIENT_ID)).thenReturn(Mono.just("new-secret"));

        OpenAppResponse response = service.resetSecret(CLIENT_ID).block();

        verify(managementService).resetClientSecret(CLIENT_ID);
        assertThat(response).isNotNull();
        assertThat(response.getClientSecret()).isEqualTo("new-secret");
    }

    @Test
    @DisplayName("重置密钥时客户端不存在应返回 NOT_FOUND")
    void shouldReturnNotFoundOnResetMissingClient() {
        when(clientRepository.findByClientId(CLIENT_ID)).thenReturn(Mono.empty());
        // resetClientSecret 在 Mono 装配期即被调用，需提供占位响应避免 NPE；空上游时不会被订阅
        when(managementService.resetClientSecret(CLIENT_ID)).thenReturn(Mono.just("placeholder"));

        assertThatThrownBy(() -> service.resetSecret(CLIENT_ID).block())
                .isInstanceOf(BusinessException.class)
                .extracting(throwable -> ((BusinessException) throwable).getCode())
                .isEqualTo(ResultCode.NOT_FOUND.getCode());
    }
}
