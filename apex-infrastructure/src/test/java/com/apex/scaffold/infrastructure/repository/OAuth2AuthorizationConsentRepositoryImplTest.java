package com.apex.scaffold.infrastructure.repository;

import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationConsentEntity;
import com.apex.scaffold.infrastructure.persistence.mapper.OAuth2AuthorizationConsentMapper;
import com.apex.scaffold.infrastructure.persistence.po.OAuth2AuthorizationConsentPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * OAuth2AuthorizationConsentRepositoryImpl单元测试
 *
 * <p>测试OAuth2授权同意仓储实现的核心功能
 *
 * @author Apex Team
 * @version 2.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2授权同意仓储测试")
class OAuth2AuthorizationConsentRepositoryImplTest {
    
    @Mock
    private OAuth2AuthorizationConsentMapper mapper;
    
    @InjectMocks
    private OAuth2AuthorizationConsentRepositoryImpl repository;
    
    @Test
    @DisplayName("根据客户端ID和用户ID查询 - 找到记录")
    void findByClientIdAndUserId_Found() {
        String clientId = "test-client";
        String userId = "user-123";
        
        OAuth2AuthorizationConsentPO po = createTestPO(clientId, userId);
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(po);
        
        StepVerifier.create(repository.findByClientIdAndUserId(clientId, userId))
                .assertNext(entity -> {
                    assertThat(entity.getClientId()).isEqualTo(clientId);
                    assertThat(entity.getUserId()).isEqualTo(userId);
                    assertThat(entity.getAuthorizedScopes())
                            .containsExactlyInAnyOrder("read", "write", "user:profile");
                })
                .verifyComplete();
        
        verify(mapper).selectOne(any(LambdaQueryWrapper.class));
    }
    
    @Test
    @DisplayName("根据客户端ID和用户ID查询 - 未找到记录")
    void findByClientIdAndUserId_NotFound() {
        String clientId = "test-client";
        String userId = "user-123";
        
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        
        StepVerifier.create(repository.findByClientIdAndUserId(clientId, userId))
                .verifyComplete();
        
        verify(mapper).selectOne(any(LambdaQueryWrapper.class));
    }
    
    @Test
    @DisplayName("保存授权同意 - 更新现有记录")
    void save_Update() {
        OAuth2AuthorizationConsentEntity entity = createTestEntity();
        OAuth2AuthorizationConsentPO existingPO = createTestPO(entity.getClientId(), entity.getUserId());
        
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(existingPO);
        when(mapper.updateById((OAuth2AuthorizationConsentPO) any()))
                .thenReturn(1);
        
        StepVerifier.create(repository.save(entity))
                .assertNext(saved -> {
                    assertThat(saved.getClientId()).isEqualTo(entity.getClientId());
                    assertThat(saved.getUserId()).isEqualTo(entity.getUserId());
                })
                .verifyComplete();
        
        verify(mapper).selectOne(any(LambdaQueryWrapper.class));
        verify(mapper).updateById((OAuth2AuthorizationConsentPO) any());
        verify(mapper, never()).insert((OAuth2AuthorizationConsentPO) any());
    }
    
    @Test
    @DisplayName("保存授权同意 - 新建记录")
    void save_Insert() {
        OAuth2AuthorizationConsentEntity entity = createTestEntity();
        
        when(mapper.selectOne(any(LambdaQueryWrapper.class)))
                .thenReturn(null);
        when(mapper.insert((OAuth2AuthorizationConsentPO) any()))
                .thenReturn(1);
        
        StepVerifier.create(repository.save(entity))
                .assertNext(saved -> {
                    assertThat(saved.getClientId()).isEqualTo(entity.getClientId());
                    assertThat(saved.getUserId()).isEqualTo(entity.getUserId());
                })
                .verifyComplete();
        
        verify(mapper).selectOne(any(LambdaQueryWrapper.class));
        verify(mapper).insert((OAuth2AuthorizationConsentPO) any());
        verify(mapper, never()).updateById((OAuth2AuthorizationConsentPO) any());
    }
    
    @Test
    @DisplayName("删除授权同意 - 成功")
    void deleteByClientIdAndUserId_Success() {
        String clientId = "test-client";
        String userId = "user-123";
        
        when(mapper.delete(any(LambdaQueryWrapper.class)))
                .thenReturn(1);
        
        StepVerifier.create(repository.deleteByClientIdAndUserId(clientId, userId))
                .verifyComplete();
        
        verify(mapper).delete(any(LambdaQueryWrapper.class));
    }
    
    private OAuth2AuthorizationConsentPO createTestPO(String clientId, String userId) {
        OAuth2AuthorizationConsentPO po = new OAuth2AuthorizationConsentPO();
        po.setId("consent-id-123");
        po.setRegisteredClientId(clientId);
        po.setPrincipalName(userId);
        po.setAuthorities("read,write,user:profile");
        po.setCreatedAt(Instant.now());
        po.setUpdatedAt(Instant.now());
        return po;
    }
    
    private OAuth2AuthorizationConsentEntity createTestEntity() {
        OAuth2AuthorizationConsentEntity entity = new OAuth2AuthorizationConsentEntity();
        entity.setClientId("test-client");
        entity.setUserId("user-123");
        Set<String> scopes = new HashSet<>();
        scopes.add("read");
        scopes.add("write");
        scopes.add("user:profile");
        entity.setAuthorizedScopes(scopes);
        entity.setGrantedAt(Instant.now());
        entity.setLastModifiedAt(Instant.now());
        return entity;
    }
}
