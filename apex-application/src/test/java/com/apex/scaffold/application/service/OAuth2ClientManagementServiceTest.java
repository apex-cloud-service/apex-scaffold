package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.assembler.OAuth2ClientAssembler;
import com.apex.scaffold.application.dto.OAuth2ClientRequest;
import com.apex.scaffold.application.dto.OAuth2ClientResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2客户端管理服务测试")
class OAuth2ClientManagementServiceTest {

    @Mock
    private OAuth2RegisteredClientRepository clientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OAuth2ClientAssembler oAuth2ClientAssembler;

    @InjectMocks
    private OAuth2ClientManagementService clientManagementService;

    private OAuth2ClientRequest testRequest;
    private OAuth2RegisteredClientEntity testClient;
    private OAuth2ClientResponse testResponse;

    @BeforeEach
    void setUp() {
        testRequest = createTestClientRequest();
        testClient = createTestClientEntity();
        testResponse = createTestClientResponse();

        lenient().when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$encrypted-secret");
        lenient()
                .when(oAuth2ClientAssembler.toResponse(any(OAuth2RegisteredClientEntity.class)))
                .thenReturn(testResponse);
    }

    @Test
    @DisplayName("创建客户端 - 成功")
    void createClient_Success() {
        when(clientRepository.findByClientId(testRequest.getClientId())).thenReturn(Mono.empty());
        when(clientRepository.save(any(OAuth2RegisteredClientEntity.class))).thenReturn(Mono.just(testClient));

        Mono<OAuth2ClientResponse> result = clientManagementService.createClient(testRequest);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getClientId()).isEqualTo(testRequest.getClientId());
                    assertThat(response.getClientName()).isEqualTo(testRequest.getClientName());
                })
                .verifyComplete();

        verify(clientRepository).findByClientId(testRequest.getClientId());
        verify(clientRepository).save(any(OAuth2RegisteredClientEntity.class));
        verify(passwordEncoder).encode(testRequest.getClientSecret());
        verify(oAuth2ClientAssembler).toResponse(any(OAuth2RegisteredClientEntity.class));
    }

    @Test
    @DisplayName("创建客户端 - 客户端ID已存在")
    void createClient_ClientIdExists() {
        when(clientRepository.findByClientId(testRequest.getClientId())).thenReturn(Mono.just(testClient));

        Mono<OAuth2ClientResponse> result = clientManagementService.createClient(testRequest);

        StepVerifier.create(result).expectError(BusinessException.class).verify();

        verify(clientRepository).findByClientId(testRequest.getClientId());
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("更新客户端 - 成功")
    void updateClient_Success() {
        String clientId = "test-client";
        OAuth2ClientRequest updateRequest = createTestClientRequest();
        updateRequest.setClientName("Updated Client Name");

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.just(testClient));
        when(clientRepository.save(any(OAuth2RegisteredClientEntity.class))).thenReturn(Mono.just(testClient));

        Mono<OAuth2ClientResponse> result = clientManagementService.updateClient(clientId, updateRequest);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getClientId()).isEqualTo(clientId);
                })
                .verifyComplete();

        verify(clientRepository).findByClientId(clientId);
        verify(clientRepository).save(any(OAuth2RegisteredClientEntity.class));
    }

    @Test
    @DisplayName("更新客户端 - 客户端不存在")
    void updateClient_ClientNotFound() {
        String clientId = "non-existent-client";
        OAuth2ClientRequest updateRequest = createTestClientRequest();

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.empty());

        Mono<OAuth2ClientResponse> result = clientManagementService.updateClient(clientId, updateRequest);

        StepVerifier.create(result).expectError(BusinessException.class).verify();

        verify(clientRepository).findByClientId(clientId);
        verify(clientRepository, never()).save(any());
    }

    @Test
    @DisplayName("删除客户端 - 成功")
    void deleteClient_Success() {
        String clientId = "test-client";

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.just(testClient));
        when(clientRepository.deleteById(testClient.getId())).thenReturn(Mono.empty());

        Mono<Void> result = clientManagementService.deleteClient(clientId);

        StepVerifier.create(result).verifyComplete();

        verify(clientRepository).findByClientId(clientId);
        verify(clientRepository).deleteById(testClient.getId());
    }

    @Test
    @DisplayName("删除客户端 - 客户端不存在")
    void deleteClient_ClientNotFound() {
        String clientId = "non-existent-client";

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.empty());

        Mono<Void> result = clientManagementService.deleteClient(clientId);

        StepVerifier.create(result).expectError(BusinessException.class).verify();

        verify(clientRepository).findByClientId(clientId);
        verify(clientRepository, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("查询客户端 - 成功")
    void getClient_Success() {
        String clientId = "test-client";

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.just(testClient));

        Mono<OAuth2ClientResponse> result = clientManagementService.getClient(clientId);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getClientId()).isEqualTo(clientId);
                    assertThat(response.getClientName()).isEqualTo(testClient.getClientName());
                })
                .verifyComplete();

        verify(clientRepository).findByClientId(clientId);
    }

    @Test
    @DisplayName("查询客户端 - 客户端不存在")
    void getClient_ClientNotFound() {
        String clientId = "non-existent-client";

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.empty());

        Mono<OAuth2ClientResponse> result = clientManagementService.getClient(clientId);

        StepVerifier.create(result).expectError(BusinessException.class).verify();
    }

    @Test
    @DisplayName("客户端列表 - 成功")
    void listClients_Success() {
        int page = 1;
        int size = 10;

        when(clientRepository.findAll(page, size)).thenReturn(Flux.just(testClient));

        Flux<OAuth2ClientResponse> result = clientManagementService.listClients(page, size);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getClientId()).isEqualTo(testClient.getClientId());
                    assertThat(response.getClientName()).isEqualTo(testClient.getClientName());
                })
                .verifyComplete();

        verify(clientRepository).findAll(page, size);
    }

    @Test
    @DisplayName("重置客户端密钥 - 成功")
    void resetClientSecret_Success() {
        String clientId = "test-client";

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.just(testClient));
        when(clientRepository.save(any(OAuth2RegisteredClientEntity.class))).thenReturn(Mono.just(testClient));

        Mono<String> result = clientManagementService.resetClientSecret(clientId);

        StepVerifier.create(result)
                .assertNext(secret -> {
                    assertThat(secret).isNotNull();
                    assertThat(secret.length()).isGreaterThanOrEqualTo(32);
                })
                .verifyComplete();

        verify(clientRepository).findByClientId(clientId);
        verify(clientRepository).save(any(OAuth2RegisteredClientEntity.class));
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    @DisplayName("重置客户端密钥 - 客户端不存在")
    void resetClientSecret_ClientNotFound() {
        String clientId = "non-existent-client";

        when(clientRepository.findByClientId(clientId)).thenReturn(Mono.empty());

        Mono<String> result = clientManagementService.resetClientSecret(clientId);

        StepVerifier.create(result).expectError(BusinessException.class).verify();

        verify(clientRepository).findByClientId(clientId);
        verify(clientRepository, never()).save(any());
    }

    private OAuth2ClientRequest createTestClientRequest() {
        OAuth2ClientRequest request = new OAuth2ClientRequest();
        request.setClientId("test-client");
        request.setClientSecret("test-secret-123");
        request.setClientName("Test Client");
        request.setClientAuthenticationMethods(Set.of("client_secret_basic"));
        request.setAuthorizationGrantTypes(Set.of("authorization_code", "refresh_token"));
        request.setRedirectUris(Set.of("http://localhost:3000/callback"));
        request.setScopes(Set.of("read", "write"));
        request.setRequireProofKey(true);
        request.setRequireAuthorizationConsent(false);
        request.setAccessTokenTimeToLive(7200L);
        request.setRefreshTokenTimeToLive(604800L);
        return request;
    }

    private OAuth2RegisteredClientEntity createTestClientEntity() {
        OAuth2RegisteredClientEntity entity = new OAuth2RegisteredClientEntity();
        entity.setId("client-id-123");
        entity.setClientId("test-client");
        entity.setClientSecret("$2a$10$encrypted-secret");
        entity.setClientName("Test Client");
        entity.setClientAuthenticationMethods(Set.of("client_secret_basic"));
        entity.setAuthorizationGrantTypes(Set.of("authorization_code", "refresh_token"));
        entity.setRedirectUris(Set.of("http://localhost:3000/callback"));
        entity.setScopes(Set.of("read", "write"));
        entity.setClientSettings("{\"requireProofKey\":true,\"requireAuthorizationConsent\":false}");
        entity.setTokenSettings("{\"accessTokenTimeToLive\":7200,\"refreshTokenTimeToLive\":604800}");
        return entity;
    }

    private OAuth2ClientResponse createTestClientResponse() {
        OAuth2ClientResponse response = new OAuth2ClientResponse();
        response.setId("client-id-123");
        response.setClientId("test-client");
        response.setClientName("Test Client");
        response.setClientAuthenticationMethods(Set.of("client_secret_basic"));
        response.setAuthorizationGrantTypes(Set.of("authorization_code", "refresh_token"));
        response.setRedirectUris(Set.of("http://localhost:3000/callback"));
        response.setScopes(Set.of("read", "write"));
        response.setIsPublicClient(false);
        return response;
    }
}
