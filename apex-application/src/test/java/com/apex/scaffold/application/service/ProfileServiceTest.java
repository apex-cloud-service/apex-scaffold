package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.assembler.ProfileAssembler;
import com.apex.scaffold.application.dto.MeVO;
import com.apex.scaffold.application.dto.ProfileUpdateRequest;
import com.apex.scaffold.application.factory.UserDefaultsFactory;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.EducationLevel;
import com.apex.scaffold.domain.model.entity.CreditAccountEntity;
import com.apex.scaffold.domain.model.entity.SubscriptionEntity;
import com.apex.scaffold.domain.model.entity.UserComplianceEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.model.entity.UserProfileEntity;
import com.apex.scaffold.domain.repository.CreditAccountRepository;
import com.apex.scaffold.domain.repository.SubscriptionRepository;
import com.apex.scaffold.domain.repository.UserComplianceRepository;
import com.apex.scaffold.domain.repository.UserProfileRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 用户资料服务单元测试类
 *
 * <p>测试 ProfileService 的核心业务逻辑，包括用户资料查询和更新功能
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private UserService userService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserComplianceRepository userComplianceRepository;

    @Mock
    private ProfileAssembler profileAssembler;

    @Mock
    private UserDefaultsFactory userDefaultsFactory;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CreditAccountRepository creditAccountRepository;

    @Mock
    private CacheConsistencyPort cacheConsistencyService;

    @InjectMocks
    private ProfileService profileService;

    private UserEntity user;
    private UserProfileEntity profile;
    private UserComplianceEntity compliance;
    private MeVO meVO;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setOpenId("test_openid");
        user.setNickName("TestUser");
        user.setAvatarUrl("https://cdn.example.com/avatar.png");
        user.setGender(1);

        profile = new UserProfileEntity();
        profile.setUserId(1L);
        profile.setDatingGoal("serious");
        profile.setBio("Hello world");
        profile.setMbti("INTJ");
        profile.setHeightCm(175);
        profile.setSchool("Test University");
        profile.setJob("Engineer");

        compliance = UserComplianceEntity.create(1L);

        meVO = new MeVO();
        meVO.setUserId("1");
        meVO.setNickName("TestUser");
        meVO.setGender(1);

        lenient()
                .when(userDefaultsFactory.createDefaultProfile(anyLong()))
                .thenAnswer(invocation -> UserProfileEntity.create(invocation.getArgument(0)));
        lenient()
                .when(userDefaultsFactory.createDefaultCompliance(anyLong()))
                .thenAnswer(invocation -> UserComplianceEntity.create(invocation.getArgument(0)));
        lenient().when(subscriptionRepository.findActiveByUserId(anyLong())).thenReturn(Mono.empty());
        lenient().when(creditAccountRepository.findByUserId(anyLong())).thenReturn(Mono.empty());
        lenient().when(cacheConsistencyService.evict(anyString(), anyString())).thenReturn(Mono.just(true));
    }

    /**
     * 获取{@code me_Success}。
     */
    @Test
    @DisplayName("getMe - 成功获取用户资料")
    void getMe_Success() {
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.just(profile));
        when(userComplianceRepository.findByUserId(1L)).thenReturn(Mono.just(compliance));
        when(profileAssembler.toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class)))
                .thenReturn(meVO);

        profileService
                .getMe("test_openid")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getUserId()).isEqualTo("1");
                    assertThat(result.getNickName()).isEqualTo("TestUser");
                    assertThat(result.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
                    assertThat(result.getApexVip()).isFalse();
                    assertThat(result.getVipLevel()).isEqualTo("NONE");
                    assertThat(result.getVerificationStatus()).isEqualTo("INACTIVE");
                    assertThat(result.getVerificationCompletedCount()).isZero();
                    assertThat(result.getVerificationRequiredCount()).isEqualTo(3);
                    assertThat(result.getReceivedLikeCount()).isZero();
                    assertThat(result.getCreditBalance()).isZero();
                    assertThat(result.getAestheticResonanceCount()).isZero();
                    assertThat(result.getExposureCreditBalance()).isZero();
                })
                .verifyComplete();

        verify(profileAssembler)
                .toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class));
    }

    /**
     * 获取{@code me_WithSubscriptionAndCredit_Success}。
     */
    @Test
    @DisplayName("getMe - 展示会员与算力余额")
    void getMe_WithSubscriptionAndCredit_Success() {
        SubscriptionEntity subscription = SubscriptionEntity.createSvipSubscription(1L, 30);
        CreditAccountEntity creditAccount = CreditAccountEntity.create(1L);
        creditAccount.setBalance(1280L);
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.just(profile));
        when(userComplianceRepository.findByUserId(1L)).thenReturn(Mono.just(compliance));
        when(subscriptionRepository.findActiveByUserId(1L)).thenReturn(Mono.just(subscription));
        when(creditAccountRepository.findByUserId(1L)).thenReturn(Mono.just(creditAccount));
        when(profileAssembler.toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class)))
                .thenReturn(meVO);

        profileService
                .getMe("test_openid")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getApexVip()).isTrue();
                    assertThat(result.getVipLevel()).isEqualTo("SVIP");
                    assertThat(result.getVerificationStatus()).isEqualTo("INACTIVE");
                    assertThat(result.getCreditBalance()).isEqualTo(1280L);
                })
                .verifyComplete();
    }

    /**
     * 获取{@code me_WithoutProfileAndCompliance_Success}。
     */
    @Test
    @DisplayName("getMe - 无画像和合规记录时使用默认值")
    void getMe_WithoutProfileAndCompliance_Success() {
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.empty());
        when(userComplianceRepository.findByUserId(1L)).thenReturn(Mono.empty());
        when(profileAssembler.toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class)))
                .thenReturn(meVO);

        profileService
                .getMe("test_openid")
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result.getNickName()).isEqualTo("TestUser"))
                .verifyComplete();

        verify(profileAssembler)
                .toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class));
    }

    /**
     * 获取{@code me_UserNotFound}。
     */
    @Test
    @DisplayName("getMe - 用户不存在时抛出异常")
    void getMe_UserNotFound() {
        when(userService.getByOpenId("unknown")).thenReturn(Mono.empty());

        profileService.getMe("unknown").as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
        });
    }

    /**
     * 更新{@code me_Success}。
     */
    @Test
    @DisplayName("updateMe - 成功更新用户资料")
    void updateMe_Success() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickName("NewName");
        request.setAvatarUrl("https://cdn.example.com/new-avatar.png");
        request.setGender(1);
        request.setBirthday(LocalDate.of(1998, 8, 6));
        request.setCity(" 上海 ");
        request.setDatingGoal("casual");
        request.setBio("New bio");
        request.setMbti("ENFP");
        request.setHeightCm(180);
        request.setSchool("New School");
        request.setJob("Designer");

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.just(profile));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(userProfileRepository.save(profile)).thenReturn(Mono.just(profile));
        when(userComplianceRepository.findByUserId(1L)).thenReturn(Mono.just(compliance));
        when(profileAssembler.toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class)))
                .thenReturn(meVO);

        profileService
                .updateMe("test_openid", request)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result.getUserId()).isEqualTo("1"))
                .verifyComplete();

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAvatarUrl()).isEqualTo("https://cdn.example.com/new-avatar.png");
        assertThat(userCaptor.getValue().getGender()).isEqualTo(1);
        assertThat(userCaptor.getValue().getBirthday()).isEqualTo(LocalDate.of(1998, 8, 6));
        assertThat(userCaptor.getValue().getCity()).isEqualTo("上海");
        verify(cacheConsistencyService).evict("user:id", "1");
        verify(cacheConsistencyService).evict("user:openId", "test_openid");
    }

    /**
     * 验证性别已设置时修改性别被拒绝且不触碰画像更新。
     */
    @Test
    @DisplayName("updateMe - 已设置性别时不允许修改")
    void updateMe_GenderChangeRejected() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setGender(2);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));

        profileService.updateMe("test_openid", request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) ex).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
            assertThat(ex).hasMessageContaining("性别不可修改");
        });

        verify(userRepository, never()).save(any());
        verify(userProfileRepository, never()).save(any());
    }

    /**
     * 验证性别未设置时允许首次设置并写入用户画像。
     */
    @Test
    @DisplayName("updateMe - 性别未设置时允许首次设置")
    void updateMe_InitialGenderAllowed() {
        user.setGender(null);
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setGender(2);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.just(profile));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(userProfileRepository.save(profile)).thenReturn(Mono.just(profile));
        when(userComplianceRepository.findByUserId(1L)).thenReturn(Mono.just(compliance));
        when(profileAssembler.toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class)))
                .thenReturn(meVO);

        profileService
                .updateMe("test_openid", request)
                .as(StepVerifier::create)
                .expectNext(meVO)
                .verifyComplete();

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getGender()).isEqualTo(2);
    }

    /**
     * 验证 {@code updateMe_EducationLevel} 场景下的预期行为。
     */
    @Test
    @DisplayName("updateMe - 学历字段写入用户画像并返回")
    void updateMe_EducationLevel() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setEducationLevel(EducationLevel.MASTER);

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.just(profile));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(userProfileRepository.save(profile)).thenReturn(Mono.just(profile));
        when(userComplianceRepository.findByUserId(1L)).thenReturn(Mono.just(compliance));
        when(profileAssembler.toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class)))
                .thenReturn(meVO);

        profileService
                .updateMe("test_openid", request)
                .as(StepVerifier::create)
                .expectNext(meVO)
                .verifyComplete();

        ArgumentCaptor<UserProfileEntity> profileCaptor = ArgumentCaptor.forClass(UserProfileEntity.class);
        verify(userProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getEducationLevel()).isEqualTo(EducationLevel.MASTER);
    }

    /**
     * 更新{@code me_WithoutNickName_Success}。
     */
    @Test
    @DisplayName("updateMe - 不修改昵称时保持原名")
    void updateMe_WithoutNickName_Success() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setDatingGoal("casual");

        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.just(profile));
        when(userRepository.save(user)).thenReturn(Mono.just(user));
        when(userProfileRepository.save(profile)).thenReturn(Mono.just(profile));
        when(userComplianceRepository.findByUserId(1L)).thenReturn(Mono.just(compliance));
        when(profileAssembler.toMeVO(
                        any(UserEntity.class),
                        any(UserProfileEntity.class),
                        any(UserComplianceEntity.class),
                        any(Map.class),
                        any(Map.class)))
                .thenReturn(meVO);

        profileService
                .updateMe("test_openid", request)
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result.getUserId()).isEqualTo("1"))
                .verifyComplete();

        verify(userRepository).save(user);
        verify(userProfileRepository).save(profile);
    }

    /**
     * 更新{@code me_UserNotFound}。
     */
    @Test
    @DisplayName("updateMe - 用户不存在时抛出异常")
    void updateMe_UserNotFound() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setNickName("NewName");

        when(userService.getByOpenId("unknown")).thenReturn(Mono.empty());

        profileService.updateMe("unknown", request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
        });
    }

    /**
     * 执行 {@code submitCustomInterestTag_Success} 操作。
     */
    @Test
    @DisplayName("submitCustomInterestTag - 成功提交自定义标签")
    void submitCustomInterestTag_Success() {
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userProfileRepository.findByUserId(1L)).thenReturn(Mono.just(profile));
        when(userProfileRepository.save(profile)).thenReturn(Mono.just(profile));

        profileService
                .submitCustomInterestTag("test_openid", "运动健身")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("status", "PENDING");
                    assertThat(result).containsEntry("tag", "运动健身");
                })
                .verifyComplete();

        assertThat(profile.getInterests()).contains("运动健身");
        verify(userProfileRepository).save(profile);
    }

    /**
     * 执行 {@code submitCustomInterestTag_NullTag_ThrowsError} 操作。
     */
    @Test
    @DisplayName("submitCustomInterestTag - 标签为空时抛出参数异常")
    void submitCustomInterestTag_NullTag_ThrowsError() {
        profileService
                .submitCustomInterestTag("test_openid", null)
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                });
    }

    /**
     * 执行 {@code submitCustomInterestTag_BlankTag_ThrowsError} 操作。
     */
    @Test
    @DisplayName("submitCustomInterestTag - 标签为空白时抛出参数异常")
    void submitCustomInterestTag_BlankTag_ThrowsError() {
        profileService
                .submitCustomInterestTag("test_openid", "   ")
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                });
    }

    /**
     * 执行 {@code submitCustomInterestTag_UserNotFound} 操作。
     */
    @Test
    @DisplayName("submitCustomInterestTag - 用户不存在时抛出异常")
    void submitCustomInterestTag_UserNotFound() {
        when(userService.getByOpenId("unknown")).thenReturn(Mono.empty());

        profileService
                .submitCustomInterestTag("unknown", "运动")
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
                });
    }
}
