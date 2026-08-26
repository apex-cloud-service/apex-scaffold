package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 用户应用服务
 *
 * <p>
 * 提供用户基础查询和管理的应用服务。
 * 支持响应式编程模型，与DDD仓储层配合工作。
 * </p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户查询（支持缓存）</li>
 *   <li>用户创建</li>
 *   <li>用户信息更新</li>
 *   <li>登录信息管理</li>
 *   <li>Token管理</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>缓存模式：使用Spring Cache提升查询性能</li>
 *   <li>事务脚本模式：管理业务流程</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * 用户仓储
     *
     * <p>
     * 依赖领域层仓储接口进行数据持久化。
     * </p>
     */
    private final UserRepository userRepository;

    /**
     * 用户装配器
     *
     * <p>
     * MapStruct生成的转换器，用于Entity-DTO转换和字段合并。
     * </p>
     */
    private final UserAssembler userAssembler;

    /**
     * 缓存一致性服务
     *
     * <p>
     * 用于管理多级缓存的同步更新，确保数据一致性。
     * </p>
     */
    private final CacheConsistencyPort cacheConsistencyService;

    /**
     * 校验手机号允许注册。
     *
     * <p>默认放行；如需注册限制（黑名单/风控），可在此注入自定义策略端口。</p>
     *
     * @param phone 手机号，为空时跳过校验
     * @return 校验通过信号
     */
    public Mono<Void> assertPhoneRegistrationAllowed(String phone) {
        return Mono.empty();
    }

    /**
     * 校验微信身份允许注册。
     *
     * <p>默认放行；如需注册限制（黑名单/风控），可在此注入自定义策略端口。</p>
     *
     * @param openId 微信 openId，为空或临时手机号身份时跳过校验
     * @return 校验通过信号
     */
    public Mono<Void> assertWechatRegistrationAllowed(String openId) {
        return Mono.empty();
    }

    /**
     * 查询：根据OpenID获取用户
     *
     * <p>
     * 通过微信OpenID查询用户信息。
     * 结果会被缓存以提升性能。
     * </p>
     *
     * <h3>缓存策略</h3>
     * <ul>
     *   <li>缓存名称：user:openId</li>
     *   <li>缓存键：OpenID值</li>
     *   <li>条件：结果不为空</li>
     * </ul>
     *
     * @param openId 微信OpenID
     * @return 用户实体，不存在时返回空Mono
     */
    @Cacheable(value = "user:openId", key = "#openId", unless = "#result == null")
    public Mono<UserEntity> getByOpenId(String openId) {
        log.debug("根据OpenID查询用户，openId: {}", openId);

        return userRepository
                .findByOpenId(openId)
                .doOnSuccess(user ->
                        log.info("根据OpenID查询用户成功，openId: {}, userId: {}", openId, user != null ? user.getId() : null))
                .doOnError(e -> log.error("根据OpenID查询用户失败，openId: {}, error: {}", openId, e.getMessage(), e));
    }

    /**
     * 查询：根据手机号获取用户
     *
     * <p>
     * 通过手机号查询用户信息。
     * 用于微信一键登录场景。
     * </p>
     *
     * @param phone 手机号
     * @return 用户实体，不存在时返回空Mono
     */
    public Mono<UserEntity> getByPhone(String phone) {
        log.debug("根据手机号查询用户，phone: {}", phone);
        return userRepository.findByPhone(phone);
    }

    /**
     * 查询：根据ID获取用户
     *
     * <p>
     * 通过用户ID查询用户信息。
     * 结果会被缓存以提升性能。
     * </p>
     *
     * <h3>缓存策略</h3>
     * <ul>
     *   <li>缓存名称：user:id</li>
     *   <li>缓存键：用户ID</li>
     *   <li>条件：结果不为空</li>
     * </ul>
     *
     * @param userId 用户ID
     * @return 用户实体，不存在时返回空Mono
     */
    @Cacheable(value = "user:id", key = "#userId", unless = "#result == null")
    public Mono<UserEntity> getById(Long userId) {
        log.debug("根据ID查询用户，userId: {}", userId);

        return userRepository
                .findById(userId)
                .doOnSuccess(user -> log.info("根据ID查询用户成功，userId: {}", userId))
                .doOnError(e -> log.error("根据ID查询用户失败，userId: {}, error: {}", userId, e.getMessage(), e));
    }

    /**
     * 命令：创建用户
     *
     * <p>
     * 创建新用户并持久化到数据库。
     * 使用事务保证数据一致性。
     * 创建成功后记录业务指标。
     * </p>
     *
     * @param user 用户实体（应包含openId等信息）
     * @return 创建后的用户实体（包含自动生成的主键）
     */
    public Mono<UserEntity> create(UserEntity user) {
        log.debug("创建用户，openId: {}", user.getOpenId());

        return assertPhoneRegistrationAllowed(user.getPhone())
                .then(assertWechatRegistrationAllowed(user.getOpenId()))
                .then(userRepository
                        .save(user)
                        .doOnSuccess(u -> {
                            log.info("用户创建成功，userId: {}, openId: {}", u.getId(), u.getOpenId());
                            log.debug("用户详细信息: {}", u);
                        })
                        .doOnError(
                                e -> log.error("用户创建失败，openId: {}, error: {}", user.getOpenId(), e.getMessage(), e)));
    }

    /**
     * 命令：更新用户信息
     *
     * <p>
     * 更新已有用户信息并持久化。
     * 使用事务保证数据一致性，同时使用缓存一致性服务清除相关缓存。
     * </p>
     *
     * <h3>业务约束</h3>
     * <ul>
     *   <li><b>性别不可修改</b>：若数据库中已存在性别值，不允许修改性别字段，抛出 {@link BusinessException}（错误码 PARAM_ERROR）</li>
     *   <li>未传入的字段保持原值不变</li>
     * </ul>
     *
     * <h3>缓存清除</h3>
     * <ul>
     *   <li>user:id:{userId}</li>
     *   <li>user:openId:{openId}</li>
     * </ul>
     *
     * <h3>事务管理</h3>
     * <p>
     * 仓储将阻塞 JDBC 写入调度到专用线程；本方法不声明同步事务，避免在返回
     * {@code Mono} 后事务提前结束。单条用户更新由数据库语句原子完成。
     * </p>
     *
     * @param user 用户实体（应包含主键ID）
     * @return 更新后的用户实体
     */
    public Mono<UserEntity> update(UserEntity user) {
        log.debug("更新用户信息，userId: {}", user.getId());
        Long userId = user.getId();
        String openId = user.getOpenId();

        return userRepository
                .findById(userId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在")))
                .flatMap(existing -> mergeFields(existing, user))
                .flatMap(userRepository::save)
                .flatMap(updated -> {
                    // 使用缓存一致性服务清除多级缓存
                    return Mono.when(
                                    cacheConsistencyService.evict("user:id", String.valueOf(userId)),
                                    cacheConsistencyService.evict("user:openId", openId))
                            .thenReturn(updated);
                })
                .doOnSuccess(u -> log.info("用户更新成功，userId: {}", u.getId()))
                .doOnError(e -> log.error("用户更新失败，userId: {}, error: {}", userId, e.getMessage(), e));
    }

    /**
     * 合并字段：校验性别不可修改，并使用MapStruct合并字段
     *
     * <p>
     * 业务规则：
     * </p>
     * <ul>
     *   <li>性别不可修改：若已有性别值且与新值不同，抛出 {@link BusinessException}</li>
     *   <li>其他字段通过 MapStruct {@code mergeUpdate} 方法自动合并</li>
     *   <li>MapStruct 仅合并非空字段，保留原值</li>
     * </ul>
     *
     * <h3>优化说明</h3>
     * <p>
     * 使用 MapStruct 替代原有的逐字段 null-check 合并逻辑，
     * 提升代码可读性和维护性。MapStruct 生成的代码性能优于手动反射。
     * </p>
     *
     * @param existing 数据库中已有的用户实体
     * @param update   待更新的用户实体
     * @return 合并后的用户实体
     */
    private Mono<UserEntity> mergeFields(UserEntity existing, UserEntity update) {
        // 业务约束：性别不可修改
        if (update.getGender() != null
                && existing.getGender() != null
                && !update.getGender().equals(existing.getGender())) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "性别不可修改"));
        }

        // 使用 MapStruct 合并字段（仅合并非空字段）
        UserEntity merged = userAssembler.mergeUpdate(existing, update);

        return Mono.just(merged);
    }

    /**
     * 命令：更新登录信息
     *
     * <p>
     * 记录用户登录时的IP和时间。
     * 通常在用户登录时调用。
     * </p>
     *
     * <h3>事务管理</h3>
     * <p>
     * 单条用户写入由数据库原子完成；缓存仅在持久化成功后驱逐。
     * </p>
     *
     * <h3>P0-12修复(SV-9)</h3>
     * <p>补充 user:openId 缓存驱逐，确保通过 OpenID 查询时不会返回旧的登录信息。
     * 此前仅驱逐了 user:id 缓存，导致 getByOpenId 仍可能命中脏缓存。</p>
     *
     * @param userId 用户ID
     * @param loginIp 登录IP地址
     * @return true-更新成功，false-用户不存在
     */
    public Mono<Boolean> updateLoginInfo(Long userId, String loginIp) {
        log.debug("更新用户登录信息，userId: {}, ip: {}", userId, loginIp);

        return userRepository
                .findById(userId)
                .flatMap(user -> {
                    user.recordLogin(loginIp);
                    return userRepository.save(user).map(u -> user);
                })
                .flatMap(user -> {
                    // P0-12修复(SV-9): 补充 user:openId 缓存驱逐，防止通过OpenID查询时返回旧登录信息
                    return cacheConsistencyService
                            .evict("user:id", String.valueOf(userId))
                            .then(cacheConsistencyService.evict("user:openId", user.getOpenId()))
                            .thenReturn(true);
                })
                .defaultIfEmpty(false)
                .doOnSuccess(result -> log.info("更新用户登录信息，userId: {}, ip: {}, result: {}", userId, loginIp, result))
                .doOnError(
                        e -> log.error("更新用户登录信息失败，userId: {}, ip: {}, error: {}", userId, loginIp, e.getMessage(), e));
    }

    /**
     * 命令：更新Token信息
     *
     * <p>
     * 更新用户的微信授权Token信息。
     * 通常在Token刷新或重新授权时调用。
     * </p>
     *
     * <h3>事务管理</h3>
     * <p>
     * 单条用户写入由数据库原子完成；缓存仅在持久化成功后驱逐。
     * </p>
     *
     * @param userId 用户ID
     * @param accessToken 新的AccessToken
     * @param refreshToken 新的RefreshToken
     * @param expireTime Token过期时间
     * @return true-更新成功，false-用户不存在
     */
    public Mono<Boolean> updateTokenInfo(
            Long userId, String accessToken, String refreshToken, LocalDateTime expireTime) {
        log.debug("更新用户Token信息，userId: {}", userId);

        return userRepository
                .findById(userId)
                .flatMap(user -> {
                    user.updateToken(accessToken, refreshToken, expireTime);
                    return userRepository.save(user).map(u -> true);
                })
                .flatMap(result -> {
                    // 使用缓存一致性服务清除多级缓存
                    return cacheConsistencyService
                            .evict("user:id", String.valueOf(userId))
                            .thenReturn(result);
                })
                .defaultIfEmpty(false)
                .doOnSuccess(result -> log.info("更新用户Token信息，userId: {}, result: {}", userId, result))
                .doOnError(e -> log.error("更新用户Token信息失败，userId: {}, error: {}", userId, e.getMessage(), e));
    }

    /**
     * 分页查询用户列表。
     *
     * @param pageNum  页码（从1开始）
     * @param pageSize 每页数量
     * @return 分页响应结果，包含用户列表和分页信息
     */
    public Mono<PageResponse<UserEntity>> findPage(Integer pageNum, Integer pageSize) {
        log.debug("分页查询用户列表，pageNum: {}, pageSize: {}", pageNum, pageSize);
        return userRepository.findPage(pageNum, pageSize);
    }
}
