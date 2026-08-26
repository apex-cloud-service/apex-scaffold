package com.apex.example.service;

import com.apex.example.dto.CreateUserRequest;
import com.apex.example.dto.UserResponse;
import com.apex.example.model.ExampleUser;
import com.apex.example.repository.ExampleUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

/**
 * 示例用户服务类
 *
 * <p>展示Service层的最佳实践:
 * <ul>
 *   <li>使用@Transactional管理事务</li>
 *   <li>使用Mono响应式编程</li>
 *   <li>业务逻辑封装在Service层</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExampleUserService {

    private final ExampleUserMapper userMapper;

    /**
     * 创建用户
     *
     * @param request 创建请求
     * @return 用户响应
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<UserResponse> createUser(CreateUserRequest request) {
        log.info("开始创建用户, username={}", request.getUsername());

        // 检查用户名是否已存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<ExampleUser>().eq(ExampleUser::getUsername, request.getUsername()));

        if (count > 0) {
            return Mono.error(new RuntimeException("用户名已存在"));
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
            return Mono.error(new RuntimeException("创建用户失败"));
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
            return Mono.error(new RuntimeException("用户不存在"));
        }

        return Mono.just(convertToResponse(user));
    }

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    public Mono<List<UserResponse>> listUsers() {
        log.debug("查询所有用户");

        List<ExampleUser> users = userMapper.selectList(new LambdaQueryWrapper<ExampleUser>()
                .eq(ExampleUser::getDeleted, 0)
                .orderByDesc(ExampleUser::getCreateTime));

        List<UserResponse> responses =
                users.stream().map(this::convertToResponse).collect(Collectors.toList());

        return Mono.just(responses);
    }

    /**
     * 更新用户
     *
     * @param id      用户ID
     * @param request 更新请求
     * @return 用户响应
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<UserResponse> updateUser(Long id, CreateUserRequest request) {
        log.info("更新用户, id={}", id);

        ExampleUser user = userMapper.selectById(id);
        if (user == null) {
            return Mono.error(new RuntimeException("用户不存在"));
        }

        // 更新字段
        user.setEmail(request.getEmail());
        user.setAge(request.getAge());
        user.setPhone(request.getPhone());
        user.setUpdateTime(LocalDateTime.now());

        int rows = userMapper.updateById(user);
        if (rows == 0) {
            return Mono.error(new RuntimeException("更新用户失败"));
        }

        log.info("用户更新成功, userId={}", id);
        return Mono.just(convertToResponse(user));
    }

    /**
     * 删除用户(逻辑删除)
     *
     * @param id 用户ID
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public Mono<Void> deleteUser(Long id) {
        log.info("删除用户, id={}", id);

        ExampleUser user = userMapper.selectById(id);
        if (user == null) {
            return Mono.error(new RuntimeException("用户不存在"));
        }

        // 逻辑删除
        user.setDeleted(1);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户删除成功, userId={}", id);
        return Mono.empty();
    }

    /**
     * 实体转DTO
     *
     * @param user 用户实体
     * @return 用户响应DTO
     */
    private UserResponse convertToResponse(ExampleUser user) {
        // 手机号脱敏
        String maskedPhone = maskPhone(user.getPhone());

        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .age(user.getAge())
                .phone(maskedPhone)
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }

    /**
     * 手机号脱敏
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
