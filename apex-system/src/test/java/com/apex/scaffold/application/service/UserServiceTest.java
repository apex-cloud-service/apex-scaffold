package com.apex.scaffold.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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

    @Mock
    private TenantGuard tenantGuard;

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

        lenient()
                .when(cacheConsistencyService.evictWithDelayedDoubleDelete(anyString(), anyString()))
                .thenReturn(Mono.just(true));
        lenient().when(tenantGuard.assertUserQuota(1)).thenReturn(Mono.empty());
        lenient().when(tenantGuard.assertActive()).thenReturn(Mono.empty());
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
     * 租户用户配额超限时拒绝创建，且不写入仓储。
     */
    @Test
    @DisplayName("create - 用户配额超限时拒绝且不调用仓储")
    void create_RejectsWhenUserQuotaExceeded() {
        when(tenantGuard.assertUserQuota(1))
                .thenReturn(Mono.error(new BusinessException(
                        com.apex.scaffold.common.result.ResultCode.TENANT_QUOTA_EXCEEDED, "用户数超出配额")));

        userService
                .create(new UserEntity())
                .as(StepVerifier::create)
                .expectErrorSatisfies(
                        error -> org.assertj.core.api.Assertions.assertThat(((BusinessException) error).getCode())
                                .isEqualTo(com.apex.scaffold.common.result.ResultCode.TENANT_QUOTA_EXCEEDED.getCode()))
                .verify();

        verifyNoInteractions(userRepository);
    }

    /**
     * 租户不可写时拒绝签发注册票据的前置校验，且不查询用户仓储。
     */
    @Test
    @DisplayName("assertPhoneRegistrationAllowed - 租户不可写时拒绝")
    void assertPhoneRegistrationAllowed_RejectsWhenTenantNotWritable() {
        when(tenantGuard.assertActive())
                .thenReturn(Mono.error(new BusinessException(
                        com.apex.scaffold.common.result.ResultCode.TENANT_ILLEGAL_TRANSITION, "租户状态不允许创建业务数据")));

        userService
                .assertPhoneRegistrationAllowed("13800001234")
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> org.assertj.core.api.Assertions.assertThat(
                                ((BusinessException) error).getCode())
                        .isEqualTo(com.apex.scaffold.common.result.ResultCode.TENANT_ILLEGAL_TRANSITION.getCode()))
                .verify();

        verifyNoInteractions(userRepository);
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

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "token", "login", "id", "openId"})
    void repositoryFailuresDoNotLeakCredentials(String operation) {
        RuntimeException failure = new RuntimeException(
                "password=private-password access_token=private-access refresh_token=private-refresh",
                new IllegalStateException("client_secret=private-client-secret"));
        lenient().when(userRepository.save(any(UserEntity.class))).thenReturn(Mono.error(failure));
        lenient().when(userRepository.findById(1L)).thenReturn(Mono.just(testUser));
        lenient()
                .when(userAssembler.mergeUpdate(any(UserEntity.class), any(UserEntity.class)))
                .thenReturn(testUser);
        Logger logger = (Logger) LoggerFactory.getLogger(UserService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Mono<?> result =
                    switch (operation) {
                        case "create" -> userService.create(testUser);
                        case "update" -> userService.update(testUser);
                        case "token" -> userService.updateTokenInfo(1L, "private-access", "private-refresh", null);
                        case "login" -> userService.updateLoginInfo(1L, "127.0.0.1");
                        case "id" -> {
                            when(userRepository.findById(1L)).thenReturn(Mono.error(failure));
                            yield userService.getById(1L);
                        }
                        case "openId" -> {
                            when(userRepository.findByOpenId(testUser.getOpenId()))
                                    .thenReturn(Mono.error(failure));
                            yield userService.getByOpenId(testUser.getOpenId());
                        }
                        default -> throw new IllegalArgumentException(operation);
                    };
            StepVerifier.create(result)
                    .expectErrorSatisfies(error ->
                            org.assertj.core.api.Assertions.assertThat(error).isSameAs(failure))
                    .verify();
            org.assertj.core.api.Assertions.assertThat(appender.list)
                    .filteredOn(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR)
                    .isNotEmpty()
                    .allSatisfy(event -> {
                        org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage())
                                .doesNotContain(
                                        "private-password",
                                        "private-access",
                                        "private-refresh",
                                        "private-client-secret");
                        org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy())
                                .isNull();
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
