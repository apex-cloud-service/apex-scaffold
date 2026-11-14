package com.apex.scaffold.integration.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.integration.connector.domain.Credential;
import com.apex.scaffold.integration.connector.infrastructure.persistence.mapper.CredentialMapper;
import com.apex.scaffold.integration.connector.infrastructure.persistence.po.CredentialPO;
import com.apex.scaffold.integration.connector.infrastructure.persistence.repository.CredentialRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.test.StepVerifier;

/**
 * 凭据仓储版本轮换测试（方案 M5 / 批次 E2）。
 *
 * <p>验证 {@code rotate}：{@code version_no} 在当前版本上递增、刷新
 * {@code rotated_at}、密文与 IV 按入参替换；更新 0 行报错。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@DisplayName("凭据仓储版本轮换测试")
class CredentialRepositoryImplTest {

    @Test
    @DisplayName("轮换：版本递增、rotatedAt 刷新、密文/IV 替换")
    void rotateBumpsVersionAndRefreshesRotatedAt() {
        CredentialMapper mapper = mock(CredentialMapper.class);
        when(mapper.updateById(any(CredentialPO.class))).thenReturn(1);
        CredentialPO persisted = new CredentialPO();
        persisted.setId(7L);
        persisted.setVersionNo(3);
        when(mapper.selectById(7L)).thenReturn(persisted);
        CredentialRepositoryImpl repository = new CredentialRepositoryImpl(mapper);

        Credential current = new Credential();
        current.setId(7L);
        current.setTenantId("t1");
        current.setCredentialKey("gh-token");
        current.setSecretCipher("new-cipher");
        current.setSecretIv("new-iv");
        current.setVersionNo(2);

        StepVerifier.create(repository.rotate(current))
                .assertNext(credential -> assertThat(credential.getVersionNo()).isEqualTo(3))
                .verifyComplete();

        ArgumentCaptor<CredentialPO> captor = ArgumentCaptor.forClass(CredentialPO.class);
        verify(mapper).updateById(captor.capture());
        CredentialPO updated = captor.getValue();
        assertThat(updated.getVersionNo()).isEqualTo(3);
        assertThat(updated.getRotatedAt()).isNotNull();
        assertThat(updated.getSecretCipher()).isEqualTo("new-cipher");
        assertThat(updated.getSecretIv()).isEqualTo("new-iv");
    }

    @Test
    @DisplayName("轮换 0 行更新（不存在或已删除）报业务错误")
    void rotateZeroRowsFails() {
        CredentialMapper mapper = mock(CredentialMapper.class);
        when(mapper.updateById(any(CredentialPO.class))).thenReturn(0);
        CredentialRepositoryImpl repository = new CredentialRepositoryImpl(mapper);

        Credential current = new Credential();
        current.setId(404L);
        StepVerifier.create(repository.rotate(current))
                .expectError(com.apex.scaffold.common.exception.BusinessException.class)
                .verify();
    }
}
