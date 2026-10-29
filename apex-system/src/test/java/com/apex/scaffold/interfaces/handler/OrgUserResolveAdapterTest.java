package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.OrganizationDirectoryApplicationService;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.interfaces.dto.internal.OrgUserResolveRequest;
import com.apex.scaffold.interfaces.dto.internal.OrgUserResolveResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * 组织目录内部接口适配器单元测试。
 *
 * <p>覆盖内部组织解析接口的匹配优先级（已绑定主键 → 手机号 → 邮箱 → 登录名）、无可用字段
 * 不发查询、以及同一系统用户被多个外部标识命中时的去重行为。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.6.0
 */
@ExtendWith(MockitoExtension.class)
class OrgUserResolveAdapterTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private OrganizationDirectoryApplicationService directoryService;

    private OrgUserResolveAdapter adapter() {
        return new OrgUserResolveAdapter(directoryService);
    }

    @Test
    @DisplayName("resolve - 手机号优先命中并回传真实用户 ID")
    void resolve_MatchesByMobileFirst() {
        UserEntity user = user(11L, "13800000000", "alice@example.com", "alice");
        when(userRepository.findByPhonesOrEmailsOrUsernames(anyList(), anyList(), anyList()))
                .thenReturn(Flux.just(user));

        OrgUserResolveRequest request = new OrgUserResolveRequest(List.of(
                new OrgUserResolveRequest.OrgUserRef("ext-1", "13800000000", "other@example.com", "other", null)));

        StepVerifier.create(adapter().resolve(request))
                .assertNext(response -> {
                    assertThat(response.resolved()).hasSize(1);
                    OrgUserResolveResponse.ResolvedUser resolved =
                            response.resolved().get(0);
                    assertThat(resolved.externalUserId()).isEqualTo("ext-1");
                    assertThat(resolved.userId()).isEqualTo(11L);
                    assertThat(resolved.matchedBy()).isEqualTo("MOBILE");
                    assertThat(resolved.deptId()).isEqualTo(3L);
                })
                .verifyComplete();

        verify(userRepository, never()).findByIds(anyList());
    }

    @Test
    @DisplayName("resolve - 手机号未命中时回退邮箱，再回退登录名")
    void resolve_FallsBackToEmailThenUsername() {
        UserEntity emailUser = user(21L, null, "bob@example.com", "bob");
        UserEntity nameUser = user(31L, null, null, "carol");
        when(userRepository.findByPhonesOrEmailsOrUsernames(anyList(), anyList(), anyList()))
                .thenReturn(Flux.just(emailUser, nameUser));

        OrgUserResolveRequest request = new OrgUserResolveRequest(List.of(
                new OrgUserResolveRequest.OrgUserRef("ext-bob", null, "bob@example.com", null, null),
                new OrgUserResolveRequest.OrgUserRef("ext-carol", null, null, "carol", null),
                new OrgUserResolveRequest.OrgUserRef("ext-missing", "13900000000", null, null, null)));

        StepVerifier.create(adapter().resolve(request))
                .assertNext(response -> {
                    assertThat(response.resolved()).hasSize(2);
                    assertThat(response.resolved())
                            .extracting(OrgUserResolveResponse.ResolvedUser::matchedBy)
                            .containsExactly("EMAIL", "USERNAME");
                    assertThat(response.resolved())
                            .extracting(OrgUserResolveResponse.ResolvedUser::userId)
                            .containsExactly(21L, 31L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("resolve - 已绑定系统用户 ID 优先于手机号匹配")
    void resolve_PrefersBoundUserId() {
        UserEntity bound = user(51L, "13600000000", "erin@example.com", "erin");
        UserEntity phoneHit = user(52L, "13500000000", null, null);
        when(userRepository.findByIds(List.of(51L))).thenReturn(Flux.just(bound));
        when(userRepository.findByPhonesOrEmailsOrUsernames(anyList(), anyList(), anyList()))
                .thenReturn(Flux.just(phoneHit, bound));

        OrgUserResolveRequest request = new OrgUserResolveRequest(
                List.of(new OrgUserResolveRequest.OrgUserRef("ext-erin", "13500000000", null, null, 51L)));

        StepVerifier.create(adapter().resolve(request))
                .assertNext(response -> {
                    assertThat(response.resolved()).hasSize(1);
                    assertThat(response.resolved().get(0).userId()).isEqualTo(51L);
                    assertThat(response.resolved().get(0).matchedBy()).isEqualTo("USER_ID");
                    assertThat(response.resolved().get(0).deptId()).isEqualTo(3L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("resolve - 同一系统用户被多个外部标识命中时只回传一条")
    void resolve_DeduplicatesByUserId() {
        UserEntity user = user(41L, "13700000000", "dave@example.com", "dave");
        when(userRepository.findByPhonesOrEmailsOrUsernames(anyList(), anyList(), anyList()))
                .thenReturn(Flux.just(user));

        OrgUserResolveRequest request = new OrgUserResolveRequest(List.of(
                new OrgUserResolveRequest.OrgUserRef("ext-d1", "13700000000", null, null, null),
                new OrgUserResolveRequest.OrgUserRef("ext-d2", null, "dave@example.com", null, null)));

        StepVerifier.create(adapter().resolve(request))
                .assertNext(response -> assertThat(response.resolved()).hasSize(1))
                .verifyComplete();
    }

    @Test
    @DisplayName("resolve - 全部条目缺少可匹配字段时不查询仓储")
    void resolve_NoMatchableFieldSkipsRepository() {
        OrgUserResolveRequest request = new OrgUserResolveRequest(
                List.of(new OrgUserResolveRequest.OrgUserRef("ext-empty", " ", null, "", null)));

        StepVerifier.create(adapter().resolve(request))
                .assertNext(response -> assertThat(response.resolved()).isEmpty())
                .verifyComplete();

        verify(userRepository, never()).findByPhonesOrEmailsOrUsernames(any(), any(), any());
        verify(userRepository, never()).findByIds(any());
    }

    private static UserEntity user(Long id, String phone, String email, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setOpenId("open-" + id);
        user.setPhone(phone);
        user.setEmail(email);
        user.setUsername(username);
        user.setDeptId(3L);
        return user;
    }
}
