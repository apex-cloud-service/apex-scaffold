package com.apex.scaffold.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 用户服务单元测试类
 *
 * <p>测试 UserService 的核心业务逻辑
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.apex.scaffold.application.assembler.UserAssembler userAssembler;

    @Mock
    private CacheConsistencyPort cacheConsistencyService;

    @InjectMocks
    private UserService userService;

    private UserEntity testUser;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        testUser = new UserEntity();
        testUser.setId(1L);
        testUser.setOpenId("test_openid");
        testUser.setNickName("Test User");
        testUser.setPhone("13800138000");

        lenient().when(cacheConsistencyService.evict(anyString(), anyString())).thenReturn(Mono.just(true));
    }

    @Test
    @DisplayName("getByOpenId - 成功场景")
    void getByOpenId_Success() {
        when(userRepository.findByOpenId("test_openid")).thenReturn(Mono.just(testUser));

        userService
                .getByOpenId("test_openid")
                .as(StepVerifier::create)
                .expectNextMatches(u -> "Test User".equals(u.getNickName()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getByOpenId - 用户不存在")
    void getByOpenId_NotFound() {
        when(userRepository.findByOpenId("unknown")).thenReturn(Mono.empty());

        userService.getByOpenId("unknown").as(StepVerifier::create).verifyComplete();
    }

    @Test
    @DisplayName("getByPhone - 成功场景")
    void getByPhone_Success() {
        when(userRepository.findByPhone("13800138000")).thenReturn(Mono.just(testUser));

        userService
                .getByPhone("13800138000")
                .as(StepVerifier::create)
                .expectNextMatches(u -> "13800138000".equals(u.getPhone()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getByPhone - 用户不存在")
    void getByPhone_NotFound() {
        when(userRepository.findByPhone("13900000000")).thenReturn(Mono.empty());

        userService.getByPhone("13900000000").as(StepVerifier::create).verifyComplete();
    }

    @Test
    @DisplayName("getById - 成功场景")
    void getById_Success() {
        when(userRepository.findById(1L)).thenReturn(Mono.just(testUser));

        userService
                .getById(1L)
                .as(StepVerifier::create)
                .expectNextMatches(u -> u.getId().equals(1L) && "Test User".equals(u.getNickName()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getById - 用户不存在")
    void getById_NotFound() {
        when(userRepository.findById(999L)).thenReturn(Mono.empty());

        userService.getById(999L).as(StepVerifier::create).verifyComplete();
    }

    @Test
    @DisplayName("create - 成功场景")
    void create_Success() {
        when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(testUser));

        userService
                .create(new UserEntity())
                .as(StepVerifier::create)
                .expectNextMatches(u -> u.getId().equals(1L))
                .verifyComplete();
    }

    /**
     * 更新{@code _Success}。
     */
    @Test
    @DisplayName("update - 成功场景")
    void update_Success() {
        when(userRepository.findById(1L)).thenReturn(Mono.just(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(testUser));
        when(userAssembler.mergeUpdate(any(UserEntity.class), any(UserEntity.class)))
                .thenReturn(testUser);

        userService
                .update(testUser)
                .as(StepVerifier::create)
                .expectNextMatches(u -> u.getId().equals(1L))
                .verifyComplete();
    }

    /**
     * 更新{@code loginInfo_Success}。
     */
    @Test
    @DisplayName("updateLoginInfo - 成功场景")
    void updateLoginInfo_Success() {
        when(userRepository.findById(1L)).thenReturn(Mono.just(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(testUser));

        userService
                .updateLoginInfo(1L, "127.0.0.1")
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 更新{@code loginInfo_UserNotFound}。
     */
    @Test
    @DisplayName("updateLoginInfo - 用户不存在")
    void updateLoginInfo_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Mono.empty());

        userService
                .updateLoginInfo(999L, "127.0.0.1")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 更新{@code tokenInfo_Success}。
     */
    @Test
    @DisplayName("updateTokenInfo - 成功场景")
    void updateTokenInfo_Success() {
        when(userRepository.findById(1L)).thenReturn(Mono.just(testUser));
        when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.just(testUser));

        userService
                .updateTokenInfo(1L, "access", "refresh", null)
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 更新{@code tokenInfo_UserNotFound}。
     */
    @Test
    @DisplayName("updateTokenInfo - 用户不存在")
    void updateTokenInfo_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Mono.empty());

        userService
                .updateTokenInfo(999L, "access", "refresh", null)
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 查询{@code page_Success}。
     */
    @Test
    @DisplayName("findPage - 成功场景")
    void findPage_Success() {
        PageResponse<UserEntity> pageResp = PageResponse.of(java.util.List.of(testUser), 1L, 1, 10);
        when(userRepository.findPage(1, 10, "create_time", false)).thenReturn(Mono.just(pageResp));

        userService
                .findPage(1, 10, "create_time", false)
                .as(StepVerifier::create)
                .expectNextMatches(r -> r.getTotal() == 1L && r.getPageNum() == 1)
                .verifyComplete();

        verify(userRepository).findPage(1, 10, "create_time", false);
    }

    @Test
    @DisplayName("findPage - 页码或页大小非法时应返回参数错误")
    void findPage_InvalidPaginationBounds() {
        userService
                .findPage(0, 10, "id", true)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> {
                    org.assertj.core.api.Assertions.assertThat(error).isInstanceOf(BusinessException.class);
                    org.assertj.core.api.Assertions.assertThat(((BusinessException) error).getCode())
                            .isEqualTo(400);
                })
                .verify();

        userService
                .findPage(1, 101, "id", true)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("findPage - 非法排序字段应返回参数错误且不访问仓储")
    void findPage_InvalidOrderField() {
        userService
                .findPage(1, 10, "password", true)
                .as(StepVerifier::create)
                .expectError(BusinessException.class)
                .verify();

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("findPage - 未指定排序时使用默认排序")
    void findPage_DefaultOrder() {
        PageResponse<UserEntity> pageResp = PageResponse.of(java.util.List.of(testUser), 1L, 1, 10);
        when(userRepository.findPage(1, 10, "create_time", false)).thenReturn(Mono.just(pageResp));

        userService
                .findPage(1, 10, null, true)
                .as(StepVerifier::create)
                .expectNext(pageResp)
                .verifyComplete();

        verify(userRepository).findPage(1, 10, "create_time", false);
    }

    @Test
    @DisplayName("findPage - 排序字段应去除空白并归一化")
    void findPage_NormalizesOrderField() {
        PageResponse<UserEntity> pageResp = PageResponse.of(java.util.List.of(testUser), 1L, 1, 10);
        when(userRepository.findPage(1, 10, "nick_name", true)).thenReturn(Mono.just(pageResp));

        userService
                .findPage(1, 10, "  NICK_NAME  ", true)
                .as(StepVerifier::create)
                .expectNext(pageResp)
                .verifyComplete();

        verify(userRepository).findPage(1, 10, "nick_name", true);
    }
}
