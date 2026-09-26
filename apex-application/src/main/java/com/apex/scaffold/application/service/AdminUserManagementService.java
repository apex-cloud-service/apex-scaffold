package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.AdminUserAssembler;
import com.apex.scaffold.application.dto.admin.AdminRoleResponse;
import com.apex.scaffold.application.dto.admin.AdminUserCreateRequest;
import com.apex.scaffold.application.dto.admin.AdminUserDetailResponse;
import com.apex.scaffold.application.dto.admin.AdminUserProfileResponse;
import com.apex.scaffold.application.dto.admin.AdminUserSummaryResponse;
import com.apex.scaffold.application.dto.admin.AdminUserUpdateRequest;
import com.apex.scaffold.application.factory.UserDefaultsFactory;
import com.apex.scaffold.common.annotation.DataScope;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.RoleRepository;
import com.apex.scaffold.domain.repository.UserComplianceRepository;
import com.apex.scaffold.domain.repository.UserProfileRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.repository.UserRoleRepository;
import com.apex.scaffold.domain.service.AccountDeletionPort;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 超级管理员用户查询和后台角色配置。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Service
public class AdminUserManagementService {

    /** 无活跃心跳/登录时判定为不活跃的天数阈值。 */
    private static final long INACTIVE_DAYS_THRESHOLD = 30L;

    private static final Set<String> MANAGED_ROLE_CODES = Set.of("SUPER_ADMIN", "REVIEW_MANAGER", "AUDITOR");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final TokenService tokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ProfileService profileService;
    private final UserProfileRepository userProfileRepository;
    private final UserComplianceRepository userComplianceRepository;
    private final UserDefaultsFactory userDefaultsFactory;
    private final CacheConsistencyPort cacheConsistencyPort;
    private final AccountDeletionPort accountDeletionPort;
    private final AdminUserAssembler adminUserAssembler;

    /**
     * 创建超级管理员用户管理服务。
     *
     * @param userRepository 用户仓储
     * @param roleRepository 角色仓储
     * @param userRoleRepository 用户角色关系仓储
     * @param tokenService Token 服务
     * @param tokenBlacklistService Token 黑名单服务
     * @param profileService 用户画像服务
     * @param userProfileRepository 用户画像仓储
     * @param userComplianceRepository 用户合规记录仓储
     * @param userDefaultsFactory 用户默认值工厂
     * @param cacheConsistencyPort 缓存一致性端口
     * @param accountDeletionPort 账号注销端口
     * @param adminUserAssembler 管理后台用户对象转换器
     */
    public AdminUserManagementService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            UserRoleRepository userRoleRepository,
            TokenService tokenService,
            TokenBlacklistService tokenBlacklistService,
            ProfileService profileService,
            UserProfileRepository userProfileRepository,
            UserComplianceRepository userComplianceRepository,
            UserDefaultsFactory userDefaultsFactory,
            CacheConsistencyPort cacheConsistencyPort,
            AccountDeletionPort accountDeletionPort,
            AdminUserAssembler adminUserAssembler) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.tokenService = tokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.profileService = profileService;
        this.userProfileRepository = userProfileRepository;
        this.userComplianceRepository = userComplianceRepository;
        this.userDefaultsFactory = userDefaultsFactory;
        this.cacheConsistencyPort = cacheConsistencyPort;
        this.accountDeletionPort = accountDeletionPort;
        this.adminUserAssembler = adminUserAssembler;
    }

    /**
     * 超级管理员分页搜索用户并附带角色摘要。
     *
     * <p><b>数据权限</b>：标注 {@link DataScope} 后，由数据权限拦截器在
     * {@code sys_user} 查询上自动注入当前操作者的部门过滤条件
     * （{@code dept_id = 当前用户所属部门}），超级管理员/未分配部门用户
     * 未装配数据权限范围时放行全量查询。</p>
     *
     * @param operatorOpenId 操作管理员 openId，需超级管理员权限
     * @param keyword 搜索关键字，可为空
     * @param status 状态过滤，可为空
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页条数，限制在 1 到 100
     * @return 用户摘要分页结果
     */
    @DataScope
    public Mono<PageResponse<AdminUserSummaryResponse>> search(
            String operatorOpenId, String keyword, Integer status, int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "分页参数不合法"));
        }
        return assertSuperAdmin(operatorOpenId)
                .then(userRepository.searchForAdministration(keyword, status, pageNum, pageSize))
                .flatMap(page -> Flux.fromIterable(page.getList())
                        .concatMap(user ->
                                roleRepository.findByUserId(user.getId()).map(roles -> summary(user, roles)))
                        .collectList()
                        .map(items -> PageResponse.of(items, page.getTotal(), pageNum, pageSize)));
    }

    /**
     * 查询用户后台详情（超级管理员权限）。
     *
     * @param operatorOpenId 操作管理员 openId
     * @param userId 目标用户 ID
     * @return 用户详情
     */
    public Mono<AdminUserDetailResponse> detail(String operatorOpenId, Long userId) {
        return assertSuperAdmin(operatorOpenId).then(detailUnchecked(userId));
    }

    /**
     * 组装用户后台详情，包含角色、画像与合规摘要。
     *
     * @param userId 目标用户 ID
     * @return 用户详情
     */
    private Mono<AdminUserDetailResponse> detailUnchecked(Long userId) {
        return requiredUser(userId).flatMap(user -> Mono.zip(
                        roleRepository.findByUserId(userId),
                        profileService.getMe(user.getOpenId()),
                        userProfileRepository
                                .findByUserId(userId)
                                .defaultIfEmpty(userDefaultsFactory.createDefaultProfile(userId)))
                .map(data -> new AdminUserDetailResponse(
                        user.getId(),
                        user.getNickName(),
                        user.getAvatarUrl(),
                        user.getGender(),
                        user.getBirthday(),
                        user.getPhone(),
                        user.getCountry(),
                        user.getProvince(),
                        user.getCity(),
                        user.getStatus(),
                        user.getOnboardingStatus(),
                        user.getLanguage(),
                        activityStatus(user),
                        user.getLastLoginTime(),
                        user.getLastHeartbeatTime(),
                        user.getCreateTime(),
                        user.getUpdateTime(),
                        data.getT2(),
                        toProfile(data.getT3()),
                        toRoles(data.getT1()))));
    }

    /** 创建可通过既有手机号验证码流程登录的普通用户，并初始化画像与合规记录。 */
    public Mono<AdminUserDetailResponse> create(String operatorOpenId, AdminUserCreateRequest request) {
        String phone = request.phone().trim();
        return assertSuperAdmin(operatorOpenId)
                .then(userRepository.findByPhone(phone))
                .flatMap(existing ->
                        Mono.<UserEntity>error(new BusinessException(ResultCode.USER_ALREADY_EXISTS, "手机号已存在")))
                .switchIfEmpty(Mono.defer(() -> {
                    UserEntity user = UserEntity.create(
                            "PHONE_" + java.util.UUID.randomUUID().toString().replace("-", ""), null);
                    user.setPhone(phone);
                    user.setNickName(request.nickname().trim());
                    user.setGender(request.gender());
                    user.setBirthday(request.birthday());
                    user.setCountry(trimToNull(request.country()));
                    user.setProvince(trimToNull(request.province()));
                    user.setCity(trimToNull(request.city()));
                    user.setLanguage("zh_CN");
                    user.setOnboardingStatus("ONBOARDING_PENDING");
                    return userRepository.save(user);
                }))
                .flatMap(user -> {
                    var profile = userDefaultsFactory.createDefaultProfile(user.getId());
                    profile.setDatingGoal(trimToNull(request.datingGoal()));
                    profile.setMbti(trimToNull(request.mbti()));
                    profile.setHeightCm(request.heightCm());
                    profile.setSchool(trimToNull(request.school()));
                    profile.setEducationLevel(request.educationLevel());
                    profile.setJob(trimToNull(request.job()));
                    profile.setBio(trimToNull(request.bio()));
                    return Mono.when(
                                    userProfileRepository.save(profile),
                                    userComplianceRepository.save(
                                            userDefaultsFactory.createDefaultCompliance(user.getId())))
                            .thenReturn(user);
                })
                .flatMap(user -> detailUnchecked(user.getId()));
    }

    /** 编辑用户可展示资料；认证令牌、OpenID 与安全审计字段不允许由后台表单修改。 */
    public Mono<AdminUserDetailResponse> update(String operatorOpenId, Long userId, AdminUserUpdateRequest request) {
        String phone = request.phone().trim();
        Mono<UserEntity> saved = assertSuperAdmin(operatorOpenId)
                .then(requiredUser(userId))
                .flatMap(user -> userRepository
                        .findByPhone(phone)
                        .filter(existing -> !existing.getId().equals(userId))
                        .flatMap(existing -> Mono.<UserEntity>error(
                                new BusinessException(ResultCode.USER_ALREADY_EXISTS, "手机号已被其他用户使用")))
                        .switchIfEmpty(Mono.defer(() -> {
                            user.setPhone(phone);
                            user.setNickName(request.nickname().trim());
                            user.setGender(request.gender());
                            user.setBirthday(request.birthday());
                            user.setCountry(trimToNull(request.country()));
                            user.setProvince(trimToNull(request.province()));
                            user.setCity(trimToNull(request.city()));
                            return userRepository.save(user);
                        })));
        return saved.flatMap(user -> userProfileRepository
                        .findByUserId(userId)
                        .defaultIfEmpty(userDefaultsFactory.createDefaultProfile(userId))
                        .flatMap(profile -> {
                            profile.setDatingGoal(trimToNull(request.datingGoal()));
                            profile.setMbti(trimToNull(request.mbti()));
                            profile.setHeightCm(request.heightCm());
                            profile.setSchool(trimToNull(request.school()));
                            profile.setEducationLevel(request.educationLevel());
                            profile.setJob(trimToNull(request.job()));
                            profile.setBio(trimToNull(request.bio()));
                            return userProfileRepository.save(profile);
                        }))
                .then(Mono.when(
                        cacheConsistencyPort.evict("user:id", String.valueOf(userId)),
                        requiredUser(userId)
                                .flatMap(user -> cacheConsistencyPort.evict("user:openId", user.getOpenId()))))
                .then(detailUnchecked(userId));
    }

    /**
     * 查询超级管理员可配置的角色列表。
     *
     * @param operatorOpenId 操作管理员 openId
     * @return 可配置角色列表
     */
    public Mono<List<AdminRoleResponse>> manageableRoles(String operatorOpenId) {
        return assertSuperAdmin(operatorOpenId).then(manageableRolesUnchecked());
    }

    /**
     * 查询启用且受后台管理的角色列表，不做权限校验。
     *
     * @return 可配置角色列表
     */
    private Mono<List<AdminRoleResponse>> manageableRolesUnchecked() {
        return roleRepository.findAll().map(roles -> {
            List<RoleEntity> managedRoles = roles.stream()
                    .filter(role -> role.getStatus() != null && role.getStatus() == 1)
                    .filter(role -> MANAGED_ROLE_CODES.contains(role.getCode()))
                    .toList();
            return adminUserAssembler.toRoleResponseList(managedRoles).stream()
                    .sorted(java.util.Comparator.comparing(AdminRoleResponse::id))
                    .toList();
        });
    }

    /**
     * 替换目标用户的后台角色集合。
     *
     * <p>不可修改自己；请求的角色必须全部属于可配置集合，非受管角色保持不变。</p>
     *
     * @param operatorOpenId 操作管理员 openId
     * @param targetUserId 目标用户 ID
     * @param roleIds 请求的角色 ID 集合，可为空
     * @return 更新后的用户详情
     */
    public Mono<AdminUserDetailResponse> replaceRoles(String operatorOpenId, Long targetUserId, Set<Long> roleIds) {
        Set<Long> requested = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        return assertSuperAdmin(operatorOpenId)
                .then(userRepository.findByOpenId(operatorOpenId))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "管理员不存在")))
                .flatMap(operator -> {
                    if (operator.getId().equals(targetUserId)) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "不能修改自己的后台角色"));
                    }
                    return requiredUser(targetUserId);
                })
                .then(manageableRolesUnchecked())
                .flatMap(managed -> {
                    Set<Long> allowed =
                            managed.stream().map(AdminRoleResponse::id).collect(Collectors.toSet());
                    if (!allowed.containsAll(requested)) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "包含不可配置的角色"));
                    }
                    return roleRepository.findByUserId(targetUserId).flatMap(current -> {
                        Set<Long> completeTarget = current.stream()
                                .filter(role -> !MANAGED_ROLE_CODES.contains(role.getCode()))
                                .map(RoleEntity::getId)
                                .collect(Collectors.toSet());
                        completeTarget.addAll(requested);
                        return userRoleRepository.replaceRoles(targetUserId, completeTarget);
                    });
                })
                .then(detailUnchecked(targetUserId));
    }

    /**
     * 强制下线目标用户，吊销其身份令牌与 refresh token。
     *
     * @param operatorOpenId 操作管理员 openId
     * @param targetUserId 目标用户 ID
     * @return 实际吊销的令牌数，目标无 openId 时为 0
     */
    public Mono<Long> revokeSessions(String operatorOpenId, Long targetUserId) {
        return assertSuperAdmin(operatorOpenId)
                .then(Mono.zip(
                        requiredUser(targetUserId),
                        userRepository
                                .findByOpenId(operatorOpenId)
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "管理员不存在")))))
                .flatMap(pair -> {
                    if (pair.getT1().getId().equals(pair.getT2().getId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "不能强制下线当前账号"));
                    }
                    String targetOpenId = pair.getT1().getOpenId();
                    return targetOpenId == null || targetOpenId.isBlank()
                            ? Mono.just(0L)
                            : revokeAllTokens(targetOpenId);
                });
    }

    /**
     * 启用或禁用目标用户账号，禁用时同步吊销其令牌。
     *
     * @param operatorOpenId 操作管理员 openId
     * @param targetUserId 目标用户 ID
     * @param enabled 为 true 启用，false 禁用
     * @return 更新后的用户详情
     */
    public Mono<AdminUserDetailResponse> updateStatus(String operatorOpenId, Long targetUserId, boolean enabled) {
        return assertSuperAdmin(operatorOpenId)
                .then(Mono.zip(
                        requiredUser(targetUserId),
                        userRepository
                                .findByOpenId(operatorOpenId)
                                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "管理员不存在")))))
                .flatMap(pair -> {
                    UserEntity target = pair.getT1();
                    if (target.getId().equals(pair.getT2().getId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "不能修改当前账号状态"));
                    }
                    if (enabled) {
                        target.enable();
                    } else {
                        target.disable();
                    }
                    return userRepository
                            .save(target)
                            .then(
                                    enabled || target.getOpenId() == null
                                            ? Mono.empty()
                                            : revokeAllTokens(target.getOpenId())
                                                    .then())
                            .then(detailUnchecked(targetUserId));
                });
    }

    /**
     * 吊销指定身份的全部令牌并写入 30 天黑名单标记。
     *
     * @param openId 目标身份 openId
     * @return 吊销操作结果
     */
    private Mono<Long> revokeAllTokens(String openId) {
        long markerTtl = java.time.Duration.ofDays(30).toMillis();
        return tokenBlacklistService.revokeSubject(openId, markerTtl).then(tokenService.deleteToken(openId));
    }

    /**
     * 校验操作者具备超级管理员角色，否则返回权限错误。
     *
     * @param openId 操作者 openId
     * @return 校验通过信号
     */
    private Mono<Void> assertSuperAdmin(String openId) {
        return userRepository
                .findByOpenId(openId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "管理员不存在")))
                .flatMap(user -> roleRepository.findByUserId(user.getId()))
                .filter(roles -> roles.stream().anyMatch(role -> "SUPER_ADMIN".equals(role.getCode())))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.FORBIDDEN, "仅超级管理员可访问")))
                .then();
    }

    /**
     * 加载目标用户，ID 非法或用户不存在时返回业务错误。
     *
     * @param userId 目标用户 ID
     * @return 用户实体
     */
    private Mono<UserEntity> requiredUser(Long userId) {
        if (userId == null || userId <= 0) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "userId不合法"));
        }
        return userRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")));
    }

    /**
     * 组装用户摘要响应。
     *
     * @param user 用户实体
     * @param roles 用户角色列表
     * @return 用户摘要响应
     */
    private AdminUserSummaryResponse summary(UserEntity user, List<RoleEntity> roles) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getNickName(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getPhone(),
                user.getStatus(),
                user.getOnboardingStatus(),
                activityStatus(user),
                user.getLastLoginTime(),
                user.getCreateTime(),
                toRoles(roles));
    }

    /**
     * 依据心跳与最近登录时间推导用户活动状态。
     *
     * @param user 用户实体
     * @return ONLINE/ACTIVE/INACTIVE 之一
     */
    private String activityStatus(UserEntity user) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (user.getLastHeartbeatTime() != null && user.getLastHeartbeatTime().isAfter(now.minusMinutes(5))) {
            return "ONLINE";
        }
        if (user.getLastLoginTime() != null
                && user.getLastLoginTime().isAfter(now.minusDays(INACTIVE_DAYS_THRESHOLD))) {
            return "ACTIVE";
        }
        return "INACTIVE";
    }

    /**
     * 过滤出受后台管理的角色并转换为响应。
     *
     * @param roles 用户角色列表
     * @return 角色响应列表
     */
    private List<AdminRoleResponse> toRoles(List<RoleEntity> roles) {
        return roles.stream()
                .filter(role -> MANAGED_ROLE_CODES.contains(role.getCode()))
                .map(adminUserAssembler::toRoleResponse)
                .toList();
    }

    /**
     * 将角色实体转换为响应 DTO。
     *
     * @param role 角色实体
     * @return 角色响应
     */
    private AdminRoleResponse toRole(RoleEntity role) {
        return adminUserAssembler.toRoleResponse(role);
    }

    /**
     * 将画像实体转换为响应 DTO。
     *
     * @param profile 用户画像实体
     * @return 画像响应
     */
    private AdminUserProfileResponse toProfile(com.apex.scaffold.domain.model.entity.UserProfileEntity profile) {
        return adminUserAssembler.toProfileResponse(profile);
    }

    /**
     * 将可空文本去首尾空白，空串归一为 null。
     *
     * @param value 原始文本，可为空
     * @return 归一化后的文本，或 null
     */
    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
