package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.model.entity.UserSocialEntity;
import com.apex.scaffold.domain.repository.UserSocialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 用户社交身份绑定应用服务单元测试。
 *
 * <p>覆盖幂等绑定的四个核心分支：新建绑定、同用户 UnionID 同步、
 * 跨用户冲突跳过、入参缺失静默跳过。</p>
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
class UserSocialServiceTest {

    private static final Long USER_ID = 1001L;
    private static final Long OTHER_USER_ID = 2002L;
    private static final String TENANT_ID = "default";
    private static final String OPEN_ID = "openid-mini-001";
    private static final String UNION_ID = "union-001";

    @Mock
    private UserSocialRepository userSocialRepository;

    private UserSocialService service;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        service = new UserSocialService(userSocialRepository);
    }

    /**
     * 验证 {@code upsertBindingCreatesNewBindingWhenAbsent} 场景。
     */
    @Test
    void upsertBindingCreatesNewBindingWhenAbsent() {
        when(userSocialRepository.findByChannelAndOpenId(SocialChannel.WECHAT_MINI, OPEN_ID))
                .thenReturn(Mono.empty());
        when(userSocialRepository.save(any(UserSocialEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.upsertBinding(USER_ID, TENANT_ID, SocialChannel.WECHAT_MINI, OPEN_ID, UNION_ID))
                .verifyComplete();

        ArgumentCaptor<UserSocialEntity> captor = ArgumentCaptor.forClass(UserSocialEntity.class);
        verify(userSocialRepository).save(captor.capture());
        UserSocialEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getChannel()).isEqualTo(SocialChannel.WECHAT_MINI);
        assertThat(saved.getOpenId()).isEqualTo(OPEN_ID);
        assertThat(saved.getUnionId()).isEqualTo(UNION_ID);
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.isBound()).isTrue();
        assertThat(saved.getBoundAt()).isNotNull();
    }

    /**
     * 验证 {@code upsertBindingSyncsUnionIdForSameUser} 场景。
     */
    @Test
    void upsertBindingSyncsUnionIdForSameUser() {
        UserSocialEntity existing = UserSocialEntity.bind(USER_ID, TENANT_ID, SocialChannel.WECHAT_APP, OPEN_ID, null);
        when(userSocialRepository.findByChannelAndOpenId(SocialChannel.WECHAT_APP, OPEN_ID))
                .thenReturn(Mono.just(existing));
        when(userSocialRepository.save(any(UserSocialEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.upsertBinding(USER_ID, TENANT_ID, SocialChannel.WECHAT_APP, OPEN_ID, UNION_ID))
                .verifyComplete();

        assertThat(existing.getUnionId()).isEqualTo(UNION_ID);
        verify(userSocialRepository).save(existing);
    }

    /**
     * 验证 {@code upsertBindingSkipsWhenBoundToOtherUser} 场景。
     */
    @Test
    void upsertBindingSkipsWhenBoundToOtherUser() {
        UserSocialEntity occupied =
                UserSocialEntity.bind(OTHER_USER_ID, TENANT_ID, SocialChannel.WECHAT_MP, OPEN_ID, UNION_ID);
        when(userSocialRepository.findByChannelAndOpenId(SocialChannel.WECHAT_MP, OPEN_ID))
                .thenReturn(Mono.just(occupied));

        StepVerifier.create(service.upsertBinding(USER_ID, TENANT_ID, SocialChannel.WECHAT_MP, OPEN_ID, UNION_ID))
                .verifyComplete();

        verify(userSocialRepository, never()).save(any(UserSocialEntity.class));
    }

    /**
     * 验证 {@code upsertBindingSkipsOnMissingInput} 场景。
     */
    @Test
    void upsertBindingSkipsOnMissingInput() {
        StepVerifier.create(service.upsertBinding(USER_ID, TENANT_ID, SocialChannel.WECHAT_APP, " ", UNION_ID))
                .verifyComplete();
        StepVerifier.create(service.upsertBinding(null, TENANT_ID, SocialChannel.WECHAT_APP, OPEN_ID, UNION_ID))
                .verifyComplete();
        StepVerifier.create(service.upsertBinding(USER_ID, TENANT_ID, null, OPEN_ID, UNION_ID))
                .verifyComplete();

        verify(userSocialRepository, never()).findByChannelAndOpenId(any(), any());
        verify(userSocialRepository, never()).save(any(UserSocialEntity.class));
    }

    /**
     * 验证 {@code findByChannelAndOpenIdDelegatesToRepository} 场景。
     */
    @Test
    void findByChannelAndOpenIdDelegatesToRepository() {
        UserSocialEntity existing = UserSocialEntity.bind(USER_ID, TENANT_ID, SocialChannel.WECHAT_APP, OPEN_ID, null);
        when(userSocialRepository.findByChannelAndOpenId(SocialChannel.WECHAT_APP, OPEN_ID))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(service.findByChannelAndOpenId(SocialChannel.WECHAT_APP, OPEN_ID))
                .expectNext(existing)
                .verifyComplete();
    }
}
