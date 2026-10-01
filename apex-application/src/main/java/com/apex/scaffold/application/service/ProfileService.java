package com.apex.scaffold.application.service;

import static com.apex.scaffold.common.scheduler.BlockingIoSchedulers.*;

import com.apex.scaffold.application.assembler.ProfileAssembler;
import com.apex.scaffold.application.dto.MeVO;
import com.apex.scaffold.application.dto.ProfileUpdateRequest;
import com.apex.scaffold.application.factory.UserDefaultsFactory;
import com.apex.scaffold.common.constant.SystemConstants;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 用户资料应用服务
 *
 * <p>
 * 提供用户资料查询和更新的应用服务。
 * 采用CQRS模式分离读操作和写操作，优化性能和扩展性。
 * </p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>查询用户资料（GetMeQuery）</li>
 *   <li>更新用户资料（UpdateProfileCommand）</li>
 *   <li>提交自定义标签（SubmitCustomTagCommand）</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>CQRS模式：分离读模型和写模型</li>
 *   <li>Assembler模式：实体到VO的转换</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.1
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final int VERIFICATION_REQUIRED_SAMPLE_COUNT = 3;
    private static final String VIP_LEVEL_NONE = "NONE";
    private static final String VERIFICATION_STATUS_INACTIVE = "INACTIVE";
    private static final String VERIFICATION_STATUS_COMPLETED = "COMPLETED";

    /**
     * 用户服务
     *
     * <p>
     * 依赖用户基础服务进行用户查询和更新。
     * </p>
     */
    private final UserService userService;

    /**
     * 用户资料仓储
     *
     * <p>
     * 用于用户资料实体的持久化操作。
     * </p>
     */
    private final UserProfileRepository userProfileRepository;

    /** 用户仓储，用于用户基础信息的持久化。 */
    private final UserRepository userRepository;

    private final CacheConsistencyPort cacheConsistencyService;

    /**
     * 用户合规配置仓储
     *
     * <p>
     * 用于用户合规配置实体的持久化操作。
     * </p>
     */
    private final UserComplianceRepository userComplianceRepository;

    /**
     * 资料组装器
     *
     * <p>
     * 负责将领域实体转换为视图对象。
     * </p>
     */
    private final ProfileAssembler profileAssembler;

    /**
     * 用户默认实体工厂
     *
     * <p>
     * 用于创建默认合规配置和头像实体。
     * </p>
     */
    private final UserDefaultsFactory userDefaultsFactory;

    /** 当前有效会员订阅仓储。 */
    private final SubscriptionRepository subscriptionRepository;

    /** 用户算力账户仓储。 */
    private final CreditAccountRepository creditAccountRepository;

    /**
     * 查询：获取当前用户资料
     *
     * <p>
     * 查询指定用户的完整资料，包括用户基础信息、用户画像和合规配置。
     * 使用响应式编程模型，支持高并发场景。
     * </p>
     *
     * <h3>数据来源</h3>
     * <ul>
     *   <li>UserEntity：用户基础信息</li>
     *   <li>UserProfileEntity：用户画像信息</li>
     *   <li>UserComplianceEntity：合规配置信息</li>
     * </ul>
     *
     * @param openId 微信OpenID
     * @return 用户资料视图对象
     * @throws BusinessException 用户不存在时抛出业务异常
     */
    public Mono<MeVO> getMe(String openId) {
        log.debug("查询用户资料，openId: {}", openId);

        Mono<MeVO> base = userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(user -> Mono.zip(
                                userProfileRepository
                                        .findByUserId(user.getId())
                                        .defaultIfEmpty(userDefaultsFactory.createDefaultProfile(user.getId())),
                                userComplianceRepository
                                        .findByUserId(user.getId())
                                        .defaultIfEmpty(userDefaultsFactory.createDefaultCompliance(user.getId())),
                                subscriptionRepository
                                        .findActiveByUserId(user.getId())
                                        .filter(SubscriptionEntity::isActive)
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty()),
                                creditAccountRepository
                                        .findByUserId(user.getId())
                                        .map(ProfileService::creditBalance)
                                        .defaultIfEmpty(0L))
                        .map(tuple -> buildMeVO(
                                user,
                                tuple.getT1(),
                                tuple.getT2(),
                                tuple.getT3().orElse(null),
                                0L,
                                0L,
                                tuple.getT4(),
                                0L,
                                0L)));
        return base;
    }

    /**
     * 命令：更新用户资料
     *
     * <p>
     * 更新指定用户的资料信息，包括昵称和用户画像。
     * 使用事务保证数据一致性。
     * </p>
     *
     * <h3>更新字段</h3>
     * <ul>
     *   <li>UserEntity.nickName, avatarUrl, gender, birthday, city</li>
     *   <li>UserProfileEntity.datingGoal, bio, mbti, heightCm, school, job</li>
     *   <li>UserProfileEntity.educationLevel</li>
     * </ul>
     *
     * <p>通过同步 JDBC 事务端口原子更新用户和画像，避免响应式订阅阶段脱离
     * 普通 {@code @Transactional} 事务边界。</p>
     *
     * @param openId 微信OpenID
     * @param request 更新请求对象
     * @return 更新后的用户资料视图对象
     * @throws BusinessException 用户不存在时抛出业务异常
     */
    public Mono<MeVO> updateMe(String openId, ProfileUpdateRequest request) {
        log.debug("更新用户资料，openId: {}", openId);

        return userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(user -> performUpdate(user, request, openId));
    }

    /**
     * 执行资料更新操作
     *
     * <p>
     * 内部方法，执行用户资料和画像的更新。
     * </p>
     *
     * <h3>线程模型</h3>
     * <p>资料同步写库通过 {@code BlockingIoSchedulers#jdbc()} 专用线程池承载，
     * 与全局 boundedElastic 隔离，避免慢 SQL 拖垮其他阻塞 I/O。</p>
     *
     * @param user 用户实体
     * @param request 更新请求
     * @param openId OpenID用于重新查询
     * @return 更新后的视图对象
     */
    private Mono<MeVO> performUpdate(UserEntity user, ProfileUpdateRequest request, String openId) {
        updateBasicInfo(user, request);
        user.setUpdateTime(LocalDateTime.now());

        return userProfileRepository
                .findByUserId(user.getId())
                .defaultIfEmpty(userDefaultsFactory.createDefaultProfile(user.getId()))
                .flatMap(profile -> {
                    updateProfileFields(profile, request);
                    return Mono.when(userRepository.save(user), userProfileRepository.save(profile))
                            .subscribeOn(jdbc());
                })
                .then(Mono.when(
                        cacheConsistencyService.evict("user:id", String.valueOf(user.getId())),
                        cacheConsistencyService.evict("user:openId", openId)))
                .then(getMe(openId));
    }

    /**
     * 更新用户基础信息
     *
     * @param user 用户实体
     * @param request 更新请求
     */
    private void updateBasicInfo(UserEntity user, ProfileUpdateRequest request) {
        if (request.getNickName() != null && !request.getNickName().isBlank()) {
            user.setNickName(request.getNickName());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getGender() != null) {
            if (user.getGender() != null && !request.getGender().equals(user.getGender())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "性别不可修改");
            }
            user.setGender(request.getGender());
        }
        if (request.getBirthday() != null) {
            user.setBirthday(request.getBirthday());
        }
        if (request.getCity() != null) {
            user.setCity(request.getCity().trim());
        }
    }

    /**
     * 更新用户画像字段
     *
     * @param profile 用户画像实体
     * @param request 更新请求
     */
    private void updateProfileFields(UserProfileEntity profile, ProfileUpdateRequest request) {
        if (request.getDatingGoal() != null) {
            profile.setDatingGoal(request.getDatingGoal());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }
        if (request.getMbti() != null) {
            profile.setMbti(request.getMbti());
        }
        if (request.getHeightCm() != null) {
            profile.setHeightCm(request.getHeightCm());
        }
        if (request.getSchool() != null) {
            profile.setSchool(request.getSchool());
        }
        if (request.getEducationLevel() != null) {
            profile.setEducationLevel(request.getEducationLevel());
        }
        if (request.getJob() != null) {
            profile.setJob(request.getJob());
        }
        profile.setUpdateTime(LocalDateTime.now());
    }

    /**
     * 命令：提交自定义兴趣标签
     *
     * <p>
     * 用户提交自定义的兴趣标签，标签将进入审核流程。
     * </p>
     *
     * <h3>业务流程</h3>
     * <ul>
     *   <li>验证标签非空</li>
     *   <li>创建审核记录</li>
     *   <li>返回提交状态</li>
     * </ul>
     *
     * <p>P0-12修复(FE-2): 补充持久化逻辑，将标签追加到 UserProfileEntity.interests 字段中持久化保存。
     * 标签以JSON数组格式存储，追加前会去除重复。</p>
     *
     * @param openId 当前用户OpenID
     * @param tag 自定义兴趣标签
     * @return 提交结果，包含状态和标签信息
     * @throws BusinessException 标签为空时抛出参数异常
     */
    public Mono<Map<String, Object>> submitCustomInterestTag(String openId, String tag) {
        log.debug("提交自定义兴趣标签，openId: {}, tag: {}", openId, tag);

        if (tag == null || tag.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "tag不能为空"));
        }

        // P0-12修复(FE-2): 将标签持久化到用户画像的interests字段
        return userService
                .getByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(user -> userProfileRepository
                        .findByUserId(user.getId())
                        .defaultIfEmpty(userDefaultsFactory.createDefaultProfile(user.getId()))
                        .flatMap(profile -> {
                            String updatedInterests = appendInterestTag(profile.getInterests(), tag);
                            profile.setInterests(updatedInterests);
                            profile.setUpdateTime(LocalDateTime.now());
                            return userProfileRepository.save(profile);
                        }))
                .thenReturn(createTagSubmissionResult(tag));
    }

    /**
     * 构建用户资料视图对象
     *
     * <p>
     * 将用户基础信息、画像和合规配置组装为视图对象。
     * </p>
     *
     * @param user 用户实体
     * @param profile 用户画像实体
     * @param compliance 用户合规配置实体
     * @return 用户资料视图对象
     */
    private MeVO buildMeVO(
            UserEntity user,
            UserProfileEntity profile,
            UserComplianceEntity compliance,
            SubscriptionEntity activeSubscription,
            Long publishedPhotoCount,
            Long receivedLikeCount,
            Long creditBalance,
            Long aestheticResonanceCount,
            Long exposureCreditBalance) {
        Map<String, Object> tags = createEmptyTags();
        Map<String, Object> sameFrequency =
                buildSameFrequencyInfo(user, profile, receivedLikeCount != null ? receivedLikeCount : 0L);
        MeVO meVO = profileAssembler.toMeVO(user, profile, compliance, sameFrequency, tags);
        populateHomepageDisplayFields(
                meVO,
                user,
                activeSubscription,
                publishedPhotoCount,
                receivedLikeCount,
                creditBalance,
                aestheticResonanceCount,
                exposureCreditBalance);
        return meVO;
    }

    /**
     * 补充个人主页展示字段。
     *
     * <p>认证进度只统计已发布照片，确保草稿或发布失败的样本不会被提前计入认证。</p>
     */
    private void populateHomepageDisplayFields(
            MeVO meVO,
            UserEntity user,
            SubscriptionEntity activeSubscription,
            Long publishedPhotoCount,
            Long receivedLikeCount,
            Long creditBalance,
            Long aestheticResonanceCount,
            Long exposureCreditBalance) {
        boolean apexVip = activeSubscription != null && activeSubscription.isActive();
        int completedCount = (int) Math.min(
                Math.max(publishedPhotoCount == null ? 0L : publishedPhotoCount, 0L),
                VERIFICATION_REQUIRED_SAMPLE_COUNT);

        meVO.setAvatarUrl(user.getAvatarUrl());
        meVO.setApexVip(apexVip);
        meVO.setVipLevel(apexVip ? activeSubscription.getLevel() : VIP_LEVEL_NONE);
        meVO.setVerificationCompletedCount(completedCount);
        meVO.setVerificationRequiredCount(VERIFICATION_REQUIRED_SAMPLE_COUNT);
        meVO.setVerificationStatus(
                completedCount >= VERIFICATION_REQUIRED_SAMPLE_COUNT
                        ? VERIFICATION_STATUS_COMPLETED
                        : VERIFICATION_STATUS_INACTIVE);
        meVO.setReceivedLikeCount(Math.max(receivedLikeCount == null ? 0L : receivedLikeCount, 0L));
        meVO.setCreditBalance(Math.max(creditBalance == null ? 0L : creditBalance, 0L));
        meVO.setAestheticResonanceCount(Math.max(aestheticResonanceCount == null ? 0L : aestheticResonanceCount, 0L));
        meVO.setExposureCreditBalance(Math.max(exposureCreditBalance == null ? 0L : exposureCreditBalance, 0L));
    }

    /**
     * 从算力账户实体中安全提取余额，balance 为 null 时返回 0。
     *
     * @param account 用户算力账户实体
     * @return 算力余额，保证非 null
     */
    private static Long creditBalance(CreditAccountEntity account) {
        return account.getBalance() == null ? 0L : account.getBalance();
    }

    /**
     * 创建空标签集合
     *
     * @return 包含空数组的标签Map
     */
    private Map<String, Object> createEmptyTags() {
        Map<String, Object> tags = new HashMap<>(3);
        tags.put("personality", new String[0]);
        tags.put("interests", new String[0]);
        tags.put("appearance", new String[0]);
        return tags;
    }

    /**
     * 构建同频状态信息
     *
     * <p>P1-FE-4修复: 根据用户实际收到的偏爱信号数量和资料完善度计算同频解锁状态。
     * 用户需要满足资料完善（昵称+交友目标已填写）且收到偏爱信号数 &gt;= {@value SystemConstants#SAME_FREQUENCY_UNLOCK_MIN_SIGNALS}
     * 才可解锁同频功能。</p>
     *
     * @param user 用户实体
     * @param profile 用户画像实体
     * @param receivedPreferCount 收到的偏爱信号数量
     * @return 同频状态Map
     */
    private Map<String, Object> buildSameFrequencyInfo(
            UserEntity user, UserProfileEntity profile, long receivedPreferCount) {
        boolean profileComplete = isProfileComplete(profile, user);
        boolean unlocked = profileComplete && receivedPreferCount >= SystemConstants.SAME_FREQUENCY_UNLOCK_MIN_SIGNALS;
        String unlockReason;
        if (!profileComplete) {
            unlockReason = "profile_incomplete";
        } else if (receivedPreferCount < SystemConstants.SAME_FREQUENCY_UNLOCK_MIN_SIGNALS) {
            unlockReason = "insufficient_prefer_signals";
        } else {
            unlockReason = "unlocked";
        }

        Map<String, Object> sameFrequency = new HashMap<>(4);
        sameFrequency.put("unlocked", unlocked);
        sameFrequency.put("unlockReason", unlockReason);
        sameFrequency.put("profileRequiredFieldsCompleted", profileComplete);
        sameFrequency.put("receivedPreferCount", receivedPreferCount);

        return sameFrequency;
    }

    /**
     * 检查用户资料是否完整
     *
     * @param user 用户实体
     * @param profile 用户画像实体
     * @return true-完整，false-不完整
     */
    private boolean isProfileComplete(UserProfileEntity profile, UserEntity user) {
        return profile.getDatingGoal() != null
                && !profile.getDatingGoal().isBlank()
                && user.getNickName() != null
                && !user.getNickName().isBlank();
    }

    /**
     * 将新标签追加到已有的兴趣标签JSON字符串中。
     *
     * <p>P0-12修复(FE-2): 辅助方法，将新的兴趣标签追加到JSON数组中。如果标签已存在则跳过重复添加。</p>
     *
     * @param existingInterestsJson 已有的兴趣标签JSON（可为null）
     * @param newTag 新标签
     * @return 更新后的JSON数组字符串
     */
    @SuppressWarnings("unchecked")
    private String appendInterestTag(String existingInterestsJson, String newTag) {
        List<String> tags;
        if (existingInterestsJson != null && !existingInterestsJson.isBlank()) {
            try {
                tags = new ArrayList<>((List<String>) JsonUtil.fromJson(existingInterestsJson, List.class));
            } catch (Exception e) {
                log.warn("解析已有兴趣标签失败，将重新初始化，existingInterestsJson: {}", existingInterestsJson, e);
                tags = new ArrayList<>(16);
            }
        } else {
            tags = new ArrayList<>(16);
        }
        if (!tags.contains(newTag)) {
            tags.add(newTag);
        }
        return JsonUtil.toJson(tags);
    }

    /**
     * 创建标签提交结果
     *
     * @param tag 标签内容
     * @return 结果Map
     */
    private Map<String, Object> createTagSubmissionResult(String tag) {
        return Map.of("status", "PENDING", "tag", tag);
    }
}
