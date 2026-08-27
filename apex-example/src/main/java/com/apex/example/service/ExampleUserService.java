package com.apex.example.service;

import com.apex.example.dto.CreateUserRequest;
import com.apex.example.dto.UserResponse;
import com.apex.example.model.ExampleUser;
import com.apex.example.repository.ExampleUserMapper;
import com.apex.scaffold.application.dto.PageResponse;
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
     * @param request 创建请求
     * @return 用户响应
     */
    /**
     * <p><b>响应式事务说明</b>：示例为单表操作，无需 {@code @Transactional}。
     * 响应式链路（Reactor 线程）下 JDBC {@code @Transactional} 会在方法返回时提前提交、
     * 导致实际 SQL 在事务外执行，这是 WebFlux + JDBC 的经典陷阱；
     * 多表事务应使用事务模板或专用 JDBC 调度器，或迁移 R2DBC。</p>
     */
    public Mono<UserResponse> createUser(CreateUserRequest request) {
        log.info("开始创建用户, username={}", request.getUsername());

        // 检查用户名是否已存在（抛脚手架统一业务异常）
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<ExampleUser>().eq(ExampleUser::getUsername, request.getUsername()));

        if (count != null && count > 0) {
            return Mono.error(new BusinessException(ResultCode.USER_ALREADY_EXISTS));
        }

        // 构建实体
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

        // 保存到数据库
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

        // 更新字段
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
     * 删除用户
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

        // 逻辑删除：设置 deleted=1（MyBatis-Plus 逻辑删除配置见 application.yml）
        user.setDeleted(1);
        user.setUpdateTime(LocalDateTime.now());
        int rows = userMapper.updateById(user);
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
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
