package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.context.DataScopeJdbcBridge;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.infrastructure.persistence.converter.UserPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.UserMapper;
import com.apex.scaffold.infrastructure.persistence.po.UserPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 用户仓储实现（基础设施层）
 *
 * <p>
 * 实现领域层定义的UserRepository接口，负责用户数据的持久化和查询。
 * 采用响应式编程模型，支持非阻塞IO操作。
 * </p>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>转换器模式：Entity-PO转换由MapStruct自动处理</li>
 *   <li>响应式模式：使用Project Reactor实现非阻塞数据访问</li>
 * </ul>
 *
 * <h2>线程调度</h2>
 * <p>
 * 所有数据库操作通过专用阻塞 I/O 调度器调度到工作线程池，
 * 避免阻塞事件循环线程。
 * </p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see UserRepository
 * @see UserPoConverter
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private static final String ORDER_BY_ID = "id";

    /**
     * 用户Mapper
     *
     * <p>
     * MyBatis-Plus Mapper，提供底层数据库操作能力。
     * </p>
     */
    private final UserMapper userMapper;

    /**
     * 用户类型转换器
     *
     * <p>
     * MapStruct生成的转换器，负责Entity和PO之间的双向转换。
     * </p>
     */
    private final UserPoConverter userPoConverter;

    /** 同步批量写入使用的编程式事务模板。 */
    private final TransactionTemplate transactionTemplate;

    /**
     * 查询：根据ID获取用户
     *
     * <p>
     * 通过用户主键ID查询用户信息。
     * 使用非阻塞方式执行数据库查询。
     * </p>
     *
     * <h3>调度说明</h3>
     * <p>
     * 查询操作在专用阻塞 I/O 调度器的线程池中执行，
     * 避免阻塞Netty事件循环。
     * </p>
     *
     * @param id 用户ID
     * @return 用户实体Mono，不存在时返回empty Mono
     */
    @Override
    public Mono<UserEntity> findById(Long id) {
        return Mono.fromCallable(() -> userMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(this::convertToEntity);
    }

    /**
     * 查询：根据OpenID获取用户
     *
     * <p>
     * 通过微信OpenID查询用户信息。
     * OpenID是微信用户的唯一标识。
     * </p>
     *
     * @param openId 微信OpenID
     * @return 用户实体Mono，不存在时返回empty Mono
     */
    @Override
    public Mono<UserEntity> findByOpenId(String openId) {
        return Mono.fromCallable(() ->
                        userMapper.selectOne(ConditionUtil.lambda(UserPO.class).eqIfPresent(UserPO::getOpenId, openId)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(this::convertToEntity);
    }

    /**
     * 查询：根据手机号获取用户
     *
     * <p>
     * 通过手机号查询用户信息。
     * 用于微信一键登录场景，通过手机号定位用户。
     * </p>
     *
     * @param phone 手机号
     * @return 用户实体Mono，不存在时返回empty Mono
     */
    @Override
    public Mono<UserEntity> findByPhone(String phone) {
        return Mono.fromCallable(() ->
                        userMapper.selectOne(ConditionUtil.lambda(UserPO.class).eqIfPresent(UserPO::getPhone, phone)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(this::convertToEntity);
    }

    /**
     * 根据登录账号查询用户（账号密码登录）。
     *
     * <p>按 {@code username} 精确匹配（{@code uk_username} 唯一索引），
     * 查询结果含 BCrypt 密码哈希字段，供密码认证策略校验。</p>
     *
     * @param username 登录账号
     * @return 用户实体（含密码哈希），不存在时返回 {@link Mono#empty()}
     * @since 2.2.0
     */
    @Override
    public Mono<UserEntity> findByUsername(String username) {
        return Mono.fromCallable(() -> userMapper.selectOne(
                        ConditionUtil.lambda(UserPO.class).eqIfPresent(UserPO::getUsername, username)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(this::convertToEntity);
    }

    /**
     * 根据 Keycloak 用户唯一标识查询用户。
     *
     * <p>按 {@code keycloak_sub} 精确匹配（{@code uk_keycloak_sub} 唯一索引），
     * Keycloak OIDC 登录场景使用。</p>
     *
     * @param keycloakSub Keycloak 用户唯一标识（sub claim）
     * @return 用户实体，不存在时返回 {@link Mono#empty()}
     * @since 2.4.0
     */
    @Override
    public Mono<UserEntity> findByKeycloakSub(String keycloakSub) {
        return Mono.fromCallable(() -> userMapper.selectOne(
                        ConditionUtil.lambda(UserPO.class).eqIfPresent(UserPO::getKeycloakSub, keycloakSub)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(this::convertToEntity);
    }

    /**
     * 保存：创建或更新用户
     *
     * <p>
     * 根据用户ID是否为空自动判断操作类型：
     * <ul>
     *   <li>ID为空：执行插入操作</li>
     *   <li>ID非空：执行更新操作</li>
     * </ul>
     * </p>
     *
     * <h3>返回说明</h3>
     * <p>
     * 返回保存后的实体，包含自动生成的主键ID（插入场景）。
     * </p>
     *
     * @param user 用户实体
     * @return 保存后的用户实体Mono
     */
    @Override
    public Mono<UserEntity> save(UserEntity user) {
        return Mono.fromCallable(() -> {
                    UserPO po = userPoConverter.toPo(user);
                    if (po.getId() == null) {
                        userMapper.insert(po);
                    } else {
                        userMapper.updateById(po);
                    }
                    return po;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(this::convertToEntity);
    }

    /**
     * 更新用户最后心跳时间（在线判定）。
     *
     * <p>将 {@code last_heartbeat_time} 刷新为当前时间，供在线用户列表
     * （最近 5 分钟有心跳视为在线）判定使用。</p>
     *
     * @param userId 用户 ID
     * @return 更新完成信号
     * @since 2.2.0
     */
    @Override
    public Mono<Void> updateLastHeartbeat(Long userId) {
        return Mono.fromCallable(() -> {
                    UserPO update = new UserPO();
                    update.setLastHeartbeatTime(java.time.LocalDateTime.now());
                    userMapper.update(update, ConditionUtil.lambda(UserPO.class).eq(UserPO::getId, userId));
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    /**
     * 分页查询在线用户。
     *
     * <p>在线判定：最近 {@link UserRepository#ONLINE_WINDOW_MINUTES} 分钟内有心跳
     * （{@code last_heartbeat_time} ≥ 当前时间 - 窗口）的用户，按心跳时间倒序。</p>
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页大小
     * @return 在线用户分页结果
     * @since 2.2.0
     */
    @Override
    public Mono<com.apex.scaffold.common.dto.PageResponse<UserEntity>> findOnlineUsers(int pageNum, int pageSize) {
        return Mono.fromCallable(() -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<UserPO> page =
                            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
                    userMapper.selectPage(
                            page,
                            ConditionUtil.lambda(UserPO.class)
                                    .ge(
                                            UserPO::getLastHeartbeatTime,
                                            java.time.LocalDateTime.now()
                                                    .minusMinutes(UserRepository.ONLINE_WINDOW_MINUTES))
                                    .orderByDesc(UserPO::getLastHeartbeatTime));
                    return com.apex.scaffold.common.dto.PageResponse.of(
                            page.getRecords().stream()
                                    .map(userPoConverter::toEntity)
                                    .collect(java.util.stream.Collectors.toList()),
                            page.getTotal(),
                            pageNum,
                            pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除：根据ID删除用户
     *
     * <p>
     * 执行物理删除操作。
     * 通常业务场景建议使用逻辑删除。
     * </p>
     *
     * @param id 用户ID
     * @return 完成信号Mono
     */
    @Override
    public Mono<Void> deleteById(Long id) {
        return Mono.fromRunnable(() -> userMapper.deleteById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .then();
    }

    /**
     * 批量保存用户
     *
     * <p>
     * 批量插入或更新用户数据。
     * </p>
     *
     * @param users 用户实体列表
     * @return 保存后的用户实体列表Mono
     */
    public Mono<List<UserEntity>> saveAll(List<UserEntity> users) {
        return Mono.fromCallable(() -> transactionTemplate.execute(status -> {
                    List<UserPO> pos = userPoConverter.toPoList(users);
                    pos.forEach(po -> {
                        if (po.getId() == null) {
                            userMapper.insert(po);
                        } else {
                            userMapper.updateById(po);
                        }
                    });
                    return pos;
                }))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(userPoConverter::toEntityList);
    }

    /**
     * 根据ID列表批量查询
     *
     * <p>
     * 根据用户主键ID列表批量查询用户信息。
     * 使用 MyBatis-Plus 的 {@code selectBatchIds} 方法执行批量查询。
     * </p>
     *
     * @param ids 用户ID列表
     * @return 用户实体流 {@code Flux<UserEntity>}
     */
    public Flux<UserEntity> findByIds(List<Long> ids) {
        return Mono.fromCallable(() -> userMapper.selectByIds(ids))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(list -> Flux.fromIterable(userPoConverter.toEntityList(list)));
    }

    /**
     * 查询所有用户。
     *
     * <p>查询所有未被逻辑删除的用户，返回完整用户实体流。
     * 适用于统计分析、批处理等需要全量数据的场景。</p>
     *
     * <h3>性能提示</h3>
     * <p>当用户数量较大时，建议使用 {@link #findPage(Integer, Integer)} 分页查询，
     * 避免一次性加载全量数据造成内存压力。</p>
     *
     * @return 所有用户实体的 Flux 流
     * @since 1.0.0
     */
    @Override
    public Flux<UserEntity> findAll() {
        return Mono.fromCallable(() -> {
                    LambdaQueryWrapper<UserPO> wrapper = new LambdaQueryWrapper<UserPO>()
                            .eq(UserPO::getDeleted, 0)
                            .orderByDesc(UserPO::getCreateTime);
                    List<UserPO> poList = userMapper.selectList(wrapper);
                    return userPoConverter.toEntityList(poList);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * 分页查询用户
     *
     * <p>
     * 支持分页查询用户列表，默认按创建时间降序排列，
     * 仅查询未被逻辑删除（{@code deleted = 0}）的用户。
     * </p>
     *
     * <h3>分页参数</h3>
     * <ul>
     *   <li>{@code pageNum} - 当前页码，从 1 开始</li>
     *   <li>{@code pageSize} - 每页记录数</li>
     * </ul>
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @return 分页响应结果，包含用户实体列表和分页信息
     */
    @Override
    public Mono<PageResponse<UserEntity>> findPage(Integer pageNum, Integer pageSize, String orderBy, Boolean isAsc) {
        return Mono.fromCallable(() -> {
                    Page<UserPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<UserPO> wrapper = new LambdaQueryWrapper<UserPO>().eq(UserPO::getDeleted, 0);
                    boolean defaultOrder = orderBy == null || orderBy.isBlank();
                    SFunction<UserPO, ?> orderColumn = defaultOrder
                            ? UserPO::getCreateTime
                            : switch (orderBy) {
                                case "nick_name" -> UserPO::getNickName;
                                case "status" -> UserPO::getStatus;
                                case "id" -> UserPO::getId;
                                default -> UserPO::getCreateTime;
                            };
                    if (!defaultOrder && Boolean.TRUE.equals(isAsc)) {
                        wrapper.orderByAsc(orderColumn);
                        if (!ORDER_BY_ID.equals(orderBy)) {
                            wrapper.orderByAsc(UserPO::getId);
                        }
                    } else {
                        wrapper.orderByDesc(orderColumn);
                        if (!ORDER_BY_ID.equals(orderBy)) {
                            wrapper.orderByDesc(UserPO::getId);
                        }
                    }
                    Page<UserPO> result = userMapper.selectPage(page, wrapper);
                    List<UserEntity> entities = userPoConverter.toEntityList(result.getRecords());
                    return PageResponse.of(entities, result.getTotal(), pageNum, pageSize);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 管理端分页搜索用户。
     *
     * <p>关键字按昵称模糊、手机号模糊或用户 ID 精确匹配；纯数字关键字
     * 会额外按 ID 精确匹配。结果按创建时间倒序返回。</p>
     *
     * @param keyword  搜索关键字，可为 {@code null}（不按关键字过滤）
     * @param status   用户状态过滤，可为 {@code null}
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public Mono<PageResponse<UserEntity>> searchForAdministration(
            String keyword, Integer status, Integer pageNum, Integer pageSize) {
        return Mono.deferContextual(contextView -> Mono.fromCallable(DataScopeJdbcBridge.required(contextView, () -> {
                    Page<UserPO> page = new Page<>(pageNum, pageSize);
                    LambdaQueryWrapper<UserPO> wrapper = new LambdaQueryWrapper<UserPO>().eq(UserPO::getDeleted, 0);
                    if (status != null) {
                        wrapper.eq(UserPO::getStatus, status);
                    }
                    String query = keyword == null ? "" : keyword.strip();
                    if (!query.isEmpty()) {
                        Long userId = null;
                        try {
                            userId = Long.valueOf(query);
                        } catch (NumberFormatException ignored) {
                        }
                        Long exactId = userId;
                        wrapper.and(nested -> {
                            nested.like(UserPO::getNickName, query).or().like(UserPO::getPhone, query);
                            if (exactId != null) {
                                nested.or()
                                        .eq(UserPO::getId, exactId)
                                        .or()
                                        .inSql(
                                                UserPO::getId,
                                                "SELECT owner_user_id FROM biz_photo WHERE id = "
                                                        + exactId
                                                        + " AND deleted = 0");
                            }
                        });
                    }
                    wrapper.orderByDesc(UserPO::getCreateTime);
                    Page<UserPO> result = userMapper.selectPage(page, wrapper);
                    return PageResponse.of(
                            userPoConverter.toEntityList(result.getRecords()), result.getTotal(), pageNum, pageSize);
                })))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 转换为实体
     *
     * @param po 持久化对象
     * @return 实体Mono
     */
    private Mono<UserEntity> convertToEntity(UserPO po) {
        if (po == null) {
            return Mono.empty();
        }
        return Mono.just(userPoConverter.toEntity(po));
    }
}
