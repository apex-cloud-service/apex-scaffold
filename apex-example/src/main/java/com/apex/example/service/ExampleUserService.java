package com.apex.example.service;

import com.apex.example.dto.CreateUserRequest;
import com.apex.example.dto.UserResponse;
import com.apex.example.model.ExampleUser;
import com.apex.example.repository.ExampleUserMapper;
import com.apex.scaffold.application.dto.PageResponse;
import com.apex.scaffold.common.annotation.DataSource;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 示例用户服务类
 *
 * <p>展示 Service 层的最佳实践:
 * <ul>
 *   <li>使用 {@code @Transactional} 管理事务</li>
 *   <li>使用 {@link Mono} 响应式编程</li>
 *   <li>业务逻辑封装在 Service 层</li>
 *   <li>统一异常：使用 {@link BusinessException} + {@link ResultCode} 而非裸 RuntimeException</li>
 *   <li>统一分页：复用脚手架的 {@link PageResponse} 分页模型（MyBatis-Plus 分页插件）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExampleUserService {

    /** 分页查询时每页大小上限，防止恶意大分页拖垮数据库。 */
    private static final long MAX_PAGE_SIZE = 100L;

    private final ExampleUserMapper userMapper;

    /**
     * 创建用户
     *
     * <p><b>响应式事务说明</b>：示例为单表操作，无需 {@code @Transactional}。
     * 响应式链路（Reactor 线程）下 JDBC {@code @Transactional} 会在方法返回时提前提交、
     * 导致实际 SQL 在事务外执行，这是 WebFlux + JDBC 的经典陷阱；
     * 多表事务应使用事务模板或专用 JDBC 调度器，或迁移 R2DBC。</p>
     *
     * @param request 创建请求
     * @return 用户响应
     */
    public Mono<UserResponse> createUser(CreateUserRequest request) {
        log.info("开始创建用户, username={}", request.getUsername());

        // 检查用户名是否已存在（抛脚手架统一业务异常）
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<ExampleUser>().eq(ExampleUser::getUsername, request.getUsername()));

        if (count != null && count > 0) {
            return Mono.error(new BusinessException(ResultCode.USER_ALREADY_EXISTS));
        }

        ExampleUser user = ExampleUser.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .age(request.getAge())
                .phone(request.getPhone())
                .status(1)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .deleted(0)
                .build();

        int rows = userMapper.insert(user);
        if (rows == 0) {
            return Mono.error(new BusinessException(ResultCode.ERROR, "创建用户失败"));
        }

        log.info("用户创建成功, userId={}", user.getId());

        // 转换为DTO返回
        return Mono.just(convertToResponse(user));
    }

    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户响应
     */
    public Mono<UserResponse> getUserById(Long id) {
        log.debug("查询用户, id={}", id);

        ExampleUser user = userMapper.selectById(id);
        if (user == null) {
            return Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND));
        }

        return Mono.just(convertToResponse(user));
    }

    /**
     * 数据源动态切换演示：从从库（slave）读取用户。
     *
     * <p>标注 {@link DataSource @DataSource("slave")} 后，由
     * {@code DataSourceSwitchAspect} 切面在方法执行期间切换到 {@code slave} 数据源
     * （对应 {@code spring.datasource.dynamic.datasource.slave} 配置），
     * 方法结束自动切回。本地演示默认指向同一 MySQL 实例，通过
     * {@code DB_SLAVE_URL} 环境变量可指向真实只读从库。</p>
     *
     * <p><b>响应式说明</b>：切换在<em>订阅时</em>生效（切面 {@code doFirst}），
     * 因此查询必须延迟到订阅期执行——使用 {@link Mono#fromCallable} 包裹 Mapper 调用
     * 而非在方法体直接调用（后者在装配期执行，早于切换）。示例模块未切换调度线程，
     * 订阅与切换同线程，切换对查询可见；主应用经 {@code BlockingIoSchedulers.jdbc()}
     * 切换线程的阻塞查询需显式装配或线程上下文透明传播（详见
     * {@code docs/DATA_SOURCE_SWITCH_USAGE.md}）。</p>
     *
     * @param id 用户ID
     * @return 用户响应（从从库读取）
     */
    @DataSource("slave")
    public Mono<UserResponse> getUserFromSlave(Long id) {
        log.debug("从从库(slave)查询用户, id={}", id);

        return Mono.fromCallable(() -> userMapper.selectById(id)).map(this::convertToResponse);
    }

    /**
     * 分页查询用户列表
     *
     * <p>演示两种分页实现之一：<b>手写分页</b>（COUNT + LIMIT）。
     * MyBatis-Plus 3.5.9+ 的 {@code selectPage} 分页插件已拆分为独立模块
     * （mybatis-plus-jsqlparser），未引入该依赖时 {@code IPage.total} 恒为 0；
     * 手写分页不依赖任何插件，逻辑显式清晰，适合教学示例。
     * 每页大小超过 {@link #MAX_PAGE_SIZE} 时按上限截断，页码小于 1 时按 1 处理。</p>
     *
     * @param pageNum  页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 分页响应（列表 + 总数 + 页码信息）
     */
    public Mono<PageResponse<UserResponse>> listUsers(long pageNum, long pageSize) {
        long safePage = Math.max(pageNum, 1L);
        long safeSize = Math.min(Math.max(pageSize, 1L), MAX_PAGE_SIZE);
        long offset = (safePage - 1) * safeSize;
        log.debug("分页查询用户, pageNum={}, pageSize={}, offset={}", safePage, safeSize, offset);

        // 1) COUNT：查询符合条件的总记录数
        LambdaQueryWrapper<ExampleUser> countWrapper =
                new LambdaQueryWrapper<ExampleUser>().eq(ExampleUser::getDeleted, 0);
        Long total = userMapper.selectCount(countWrapper);

        // 2) 数据：按创建时间倒序，LIMIT offset, size 截取当前页
        LambdaQueryWrapper<ExampleUser> pageWrapper = new LambdaQueryWrapper<ExampleUser>()
                .eq(ExampleUser::getDeleted, 0)
                .orderByDesc(ExampleUser::getCreateTime)
                .last("LIMIT " + offset + ", " + safeSize);
        List<ExampleUser> users = userMapper.selectList(pageWrapper);

        List<UserResponse> responses =
                users.stream().map(this::convertToResponse).collect(Collectors.toList());

        return Mono.just(PageResponse.of(responses, total != null ? total : 0L, (int) safePage, (int) safeSize));
    }

    /**
     * 更新用户
     *
     * @param id      用户ID
     * @param request 更新请求
     * @return 用户响应
     */
    public Mono<UserResponse> updateUser(Long id, CreateUserRequest request) {
        log.info("更新用户, id={}", id);

        ExampleUser user = userMapper.selectById(id);
        if (user == null) {
            return Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND));
        }

        user.setEmail(request.getEmail());
        user.setAge(request.getAge());
        user.setPhone(request.getPhone());
        user.setUpdateTime(LocalDateTime.now());

        int rows = userMapper.updateById(user);
        if (rows == 0) {
            return Mono.error(new BusinessException(ResultCode.ERROR, "更新用户失败"));
        }

        log.info("用户更新成功, userId={}", id);
        return Mono.just(convertToResponse(user));
    }

    /**
     * 删除用户（MyBatis-Plus 逻辑删除）
     *
     * <p>使用 {@code deleteById} 触发逻辑删除（配置了 {@code logic-delete-field: deleted}，
     * 生成 {@code UPDATE ... SET deleted=1 WHERE id=? AND deleted=0}）。
     * <b>注意</b>：不能通过 {@code setDeleted(1) + updateById} 实现——MyBatis-Plus
     * 会从 UPDATE 的 SET 子句中排除逻辑删除字段，导致删除不生效。</p>
     *
     * @param id 用户ID
     * @return 空 Mono（完成后响应）
     */
    public Mono<Void> deleteUser(Long id) {
        log.info("删除用户, id={}", id);

        ExampleUser user = userMapper.selectById(id);
        if (user == null) {
            return Mono.error(new BusinessException(ResultCode.USER_NOT_FOUND));
        }

        // 逻辑删除：MyBatis-Plus deleteById 自动置 deleted=1
        int rows = userMapper.deleteById(id);
        if (rows == 0) {
            return Mono.error(new BusinessException(ResultCode.ERROR, "删除用户失败"));
        }

        log.info("用户删除成功, userId={}", id);
        return Mono.empty();
    }

    /**
     * 实体转 DTO
     *
     * @param user 用户实体
     * @return 用户响应 DTO
     */
    private UserResponse convertToResponse(ExampleUser user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .age(user.getAge())
                .phone(maskPhone(user.getPhone()))
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }

    /**
     * 手机号脱敏展示（138****8000）
     *
     * @param phone 原始手机号
     * @return 脱敏后的手机号
     */
    private static final int MIN_PHONE_LENGTH = 7;

    private static final int PHONE_PREFIX_LENGTH = 3;
    private static final int PHONE_SUFFIX_LENGTH = 4;
    private static final String PHONE_MASK = "****";

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < MIN_PHONE_LENGTH) {
            return phone;
        }
        return phone.substring(0, PHONE_PREFIX_LENGTH)
                + PHONE_MASK
                + phone.substring(phone.length() - PHONE_SUFFIX_LENGTH);
    }
}
