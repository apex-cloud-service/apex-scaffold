package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.OnlineUserDTO;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.entity.UserEntity;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 在线用户查询服务。
 *
 * <p>面向管理后台「在线用户」页面提供分页查询（C4），在线判定基于
 * {@code last_heartbeat_time}（最近 5 分钟有心跳），并将领域实体转换为
 * 应用层 {@link OnlineUserDTO}（剔除敏感字段）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see UserService#findOnlineUsers(int, int)
 */
@Service
/**n * OnlineUserQueryServicen *n * <p>TODO: 补充服务的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class OnlineUserQueryService {

    /** 用户应用服务 */
    private final UserService userService;

    /**
     * 构造在线用户查询服务。
     *
     * @param userService 用户应用服务
     */
    public OnlineUserQueryService(UserService userService) {
        this.userService = userService;
    }

    /**
     * 分页查询在线用户。
     *
     * @param pageNum  页码，从 1 开始
     * @param pageSize 每页大小
     * @return 在线用户 DTO 分页结果
     */
    public Mono<PageResponse<OnlineUserDTO>> findOnlineUsers(int pageNum, int pageSize) {
        return userService.findOnlineUsers(pageNum, pageSize).map(page -> {
            var dtos = page.getList().stream().map(this::toDto).collect(Collectors.toList());
            return PageResponse.of(dtos, page.getTotal(), page.getPageNum(), page.getPageSize());
        });
    }

    /**
     * 将用户实体转换为在线用户 DTO。
     *
     * @param user 用户实体
     * @return 在线用户 DTO（不含密码等敏感字段）
     */
    private OnlineUserDTO toDto(UserEntity user) {
        return new OnlineUserDTO(
                user.getId(),
                user.getUsername(),
                user.getNickName(),
                user.getPhone(),
                user.getLastLoginIp(),
                user.getLastHeartbeatTime());
    }
}
