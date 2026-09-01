package com.apex.scaffold.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.domain.repository.CreditAccountRepository;
import java.time.LocalDate;
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
 * {@link CreditService} 的单元测试类 v2.0
 *
 * <p>测试积分算力服务的核心业务逻辑，包括积分增加（含上限封顶）、积分消费、
 * 每日登录奖励、投票奖励、收藏奖励和邀请奖励发放。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private CreditAccountRepository creditAccountRepository;

    @InjectMocks
    private CreditService creditService;

    private CreditAccountEntity account;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        account = new CreditAccountEntity();
        account.setUserId(1L);
        account.setBalance(500L);
        account.setTodayLoginBonus(false);
    }

    // ==================== addCredit ====================
    @Test
    @DisplayName("addCredit - 余额 500 增加 100 后余额为 600")
    void addCredit_Normal() {
        account.setBalance(500L);
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() + amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .addCredit(1L, 100L, "TEST")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(600L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("addCredit - 余额 1950 增加 100 后封顶 2000")
    void addCredit_CappedAtMax() {
        account.setBalance(1950L);
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() + amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .addCredit(1L, 100L, "TEST")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(2000L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("addCredit - 保存失败时返回 false")
    void addCredit_SaveFails() {
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        creditService
                .addCredit(1L, 100L, "TEST")
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    // ==================== consumeCredit ====================
    @Test
    @DisplayName("consumeCredit - 余额 500 消费 100 后余额为 400")
    void consumeCredit_SufficientBalance() {
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.decrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() - amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .consumeCredit(1L, 100L)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(400L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("consumeCredit - 余额不足时返回 false")
    void consumeCredit_InsufficientBalance() {
        account.setBalance(50L);
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.just(account));

        creditService
                .consumeCredit(1L, 100L)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("consumeCredit - 账户不存在时返回 false")
    void consumeCredit_AccountNotFound() {
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.empty());

        creditService
                .consumeCredit(1L, 100L)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    // ==================== grantDailyLoginBonus ====================
    @Test
    @DisplayName("grantDailyLoginBonus - 今日已领取返回 false")
    void grantDailyLoginBonus_AlreadyClaimed() {
        account.setTodayLoginBonus(true);
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));

        creditService
                .grantDailyLoginBonus(1L)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("grantDailyLoginBonus - 首次领取成功（+3）")
    void grantDailyLoginBonus_Success() {
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.updateLoginBonusWithVersion(anyLong(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    Long newBalance = invocation.getArgument(2);
                    account.setBalance(newBalance);
                    account.setTodayLoginBonus(true);
                    return Mono.just(true);
                });

        creditService
                .grantDailyLoginBonus(1L)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(503L);
                    assertThat(account.getTodayLoginBonus()).isTrue();
                })
                .verifyComplete();
    }

    // ==================== grantVoteReward ====================
    @Test
    @DisplayName("grantVoteReward - CHOICE_2 奖励 1 分")
    void grantVoteReward_ChoiceTwo() {
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() + amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .grantVoteReward(1L, "CHOICE_2", false)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(501L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("grantVoteReward - FOUR_PICK_TWO 有正面照奖励 3 分")
    void grantVoteReward_FourPickTwoWithFrontal() {
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() + amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .grantVoteReward(1L, "FOUR_PICK_TWO", true)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(503L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("grantVoteReward - FOUR_PICK_TWO 无正面照奖励 2 分")
    void grantVoteReward_FourPickTwoWithoutFrontal() {
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() + amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .grantVoteReward(1L, "FOUR_PICK_TWO", false)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(502L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("grantVoteReward - CHOICE_2_SELF_ANCHOR 奖励 1 分")
    void grantVoteReward_ChoiceTwoSelfAnchor() {
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() + amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .grantVoteReward(1L, "CHOICE_2_SELF_ANCHOR", false)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(501L);
                })
                .verifyComplete();
    }

    // ==================== grantFavoriteReward ====================
    @Test
    @DisplayName("grantFavoriteReward - 正常增加 5 分")
    void grantFavoriteReward_NormalAdd5() {
        account.setDailyFavoriteTotal(0L);
        account.setDailyFavoriteDate(LocalDate.now());
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.updateFavoriteRewardWithVersion(anyLong(), any(), anyLong(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    Long newDailyTotal = invocation.getArgument(2);
                    java.time.LocalDate date = invocation.getArgument(3);
                    Long newBalance = invocation.getArgument(4);
                    account.setDailyFavoriteTotal(newDailyTotal);
                    account.setDailyFavoriteDate(date);
                    account.setBalance(newBalance);
                    return Mono.just(true);
                });

        creditService
                .grantFavoriteReward(1L)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(505L);
                    assertThat(account.getDailyFavoriteTotal()).isEqualTo(5L);
                    assertThat(account.getDailyFavoriteDate()).isEqualTo(LocalDate.now());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("grantFavoriteReward - 当日收藏已达上限（200）返回 false")
    void grantFavoriteReward_DailyLimitReached() {
        account.setDailyFavoriteTotal(200L);
        account.setDailyFavoriteDate(LocalDate.now());
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.just(account));

        creditService
                .grantFavoriteReward(1L)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result).isFalse())
                .verifyComplete();
    }

    @Test
    @DisplayName("grantFavoriteReward - 跨天后收藏计数重置")
    void grantFavoriteReward_DateReset() {
        account.setDailyFavoriteTotal(150L);
        account.setDailyFavoriteDate(LocalDate.now().minusDays(1));
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.updateFavoriteRewardWithVersion(anyLong(), any(), anyLong(), any(), anyLong()))
                .thenAnswer(invocation -> {
                    Long newDailyTotal = invocation.getArgument(2);
                    java.time.LocalDate date = invocation.getArgument(3);
                    Long newBalance = invocation.getArgument(4);
                    account.setDailyFavoriteTotal(newDailyTotal);
                    account.setDailyFavoriteDate(date);
                    account.setBalance(newBalance);
                    return Mono.just(true);
                });

        creditService
                .grantFavoriteReward(1L)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(505L);
                    assertThat(account.getDailyFavoriteTotal()).isEqualTo(5L);
                    assertThat(account.getDailyFavoriteDate()).isEqualTo(LocalDate.now());
                })
                .verifyComplete();
    }

    // ==================== grantInviteReward ====================
    @Test
    @DisplayName("grantInviteReward - 邀请奖励 100 分")
    void grantInviteReward_Success() {
        when(creditAccountRepository.createIfNotExists(1L)).thenReturn(Mono.just(account));
        when(creditAccountRepository.incrementBalance(anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Long amount = invocation.getArgument(1);
                    account.setBalance(account.getBalance() + amount);
                    account.setVersion(account.getVersion() + 1);
                    return Mono.just(true);
                });

        creditService
                .grantInviteReward(1L)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).isTrue();
                    assertThat(account.getBalance()).isEqualTo(600L);
                })
                .verifyComplete();
    }
}
