package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.domain.model.entity.CreditTxEntity;
import com.apex.scaffold.domain.model.entity.SubscriptionEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.CreditAccountRepository;
import com.apex.scaffold.domain.repository.CreditTxRepository;
import com.apex.scaffold.domain.repository.SubscriptionRepository;
import java.time.LocalDateTime;
import java.util.List;
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
 * 会员订阅服务单元测试类
 *
 * <p>测试 SubscriptionService 的核心业务逻辑，包括会员状态查询和算力流水查询功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CreditAccountRepository creditAccountRepository;

    @Mock
    private CreditTxRepository creditTxRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private UserEntity user;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setOpenId("test_openid");
        user.setNickName("TestUser");
    }

    /**
     * 获取{@code me_Success}。
     */
    @Test
    @DisplayName("getMe - 成功获取会员状态和算力余额")
    void getMe_Success() {
        SubscriptionEntity sub = new SubscriptionEntity();
        sub.setLevel("PREMIUM");
        sub.setEndAt(LocalDateTime.of(2025, 12, 31, 23, 59));

        CreditAccountEntity acc = new CreditAccountEntity();
        acc.setBalance(1000L);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(subscriptionRepository.findActiveByUserId(1L)).thenReturn(Mono.just(sub));
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.just(acc));

        subscriptionService
                .getMe("test_openid")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("level", "PREMIUM");
                    assertThat(result).containsEntry("computeCredits", 1000L);
                    assertThat(result).containsEntry("speedMultiplier", 1.0);
                    assertThat(result).containsKey("expiresAt");
                })
                .verifyComplete();
    }

    /**
     * 获取{@code me_DefaultValues_Success}。
     */
    @Test
    @DisplayName("getMe - 无订阅和账户时返回默认值")
    void getMe_DefaultValues_Success() {
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(subscriptionRepository.findActiveByUserId(1L)).thenReturn(Mono.empty());
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.empty());

        subscriptionService
                .getMe("test_openid")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("level", "NORMAL");
                    assertThat(result).containsEntry("computeCredits", 0L);
                    assertThat(result).containsEntry("speedMultiplier", 1.0);
                })
                .verifyComplete();
    }

    /**
     * 获取{@code me_UserNotFound}。
     */
    @Test
    @DisplayName("getMe - 用户不存在时抛出异常")
    void getMe_UserNotFound() {
        when(userService.getByOpenId("unknown")).thenReturn(Mono.empty());

        subscriptionService.getMe("unknown").as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
        });
    }

    /**
     * 查询{@code creditTx_Success}列表。
     */
    @Test
    @DisplayName("listCreditTx - 成功获取算力流水列表")
    void listCreditTx_Success() {
        CreditTxEntity tx = new CreditTxEntity();
        tx.setId(1L);
        tx.setUserId(1L);
        tx.setAmount(100L);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(creditTxRepository.listByUserId(1L, 0, 10)).thenReturn(Mono.just(List.of(tx)));
        when(creditTxRepository.countByUserId(1L)).thenReturn(Mono.just(1L));

        subscriptionService
                .listCreditTx("test_openid", 1, 10)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("total", 1L);
                    assertThat(result).containsEntry("pageNum", 1);
                    assertThat(result).containsEntry("pageSize", 10);
                    assertThat(result).containsEntry("pages", 1);
                    assertThat(result).containsKey("list");
                })
                .verifyComplete();
    }

    /**
     * 查询{@code creditTx_Empty}列表。
     */
    @Test
    @DisplayName("listCreditTx - 流水列表为空")
    void listCreditTx_Empty() {
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(creditTxRepository.listByUserId(1L, 0, 10)).thenReturn(Mono.just(List.of()));
        when(creditTxRepository.countByUserId(1L)).thenReturn(Mono.just(0L));

        subscriptionService
                .listCreditTx("test_openid", 1, 10)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("total", 0L);
                    assertThat(result).containsEntry("pages", 0);
                })
                .verifyComplete();
    }

    /**
     * 查询{@code creditTx_PageTwo_Success}列表。
     */
    @Test
    @DisplayName("listCreditTx - 第二页查询")
    void listCreditTx_PageTwo_Success() {
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(creditTxRepository.listByUserId(1L, 10, 10)).thenReturn(Mono.just(List.of()));
        when(creditTxRepository.countByUserId(1L)).thenReturn(Mono.just(0L));

        subscriptionService
                .listCreditTx("test_openid", 2, 10)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("pageNum", 2);
                    assertThat(result).containsEntry("total", 0L);
                })
                .verifyComplete();
    }

    /**
     * 查询{@code creditTx_UserNotFound}列表。
     */
    @Test
    @DisplayName("listCreditTx - 用户不存在时抛出异常")
    void listCreditTx_UserNotFound() {
        when(userService.getByOpenId("unknown")).thenReturn(Mono.empty());

        subscriptionService
                .listCreditTx("unknown", 1, 10)
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
                });
    }
}
