package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.domain.model.entity.UserEntity;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放用户组织只读接口：仅返回当前租户内的最小用户字段。
 *
 * <p>租户由开放应用令牌的服务侧认证写入 Reactor Context / TenantContext，查询沿用既有
 * 租户行级隔离；不返回手机号、邮箱等敏感字段，避免超出 {@code open:user:read} 语义。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
public class OpenUserHandler {

    private final UserService users;

    /**
     * 构造处理器。
     *
     * @param users 用户查询服务
     */
    public OpenUserHandler(UserService users) {
        this.users = users;
    }

    /**
     * 分页查询当前租户用户，仅返回 id/username/nickname/deptId/status。
     *
     * @param request 服务器请求（query 参数 pageNum/pageSize，越界自动收敛）
     * @return 统一响应，{@code data.rows} 为用户精简列表
     */
    public Mono<ServerResponse> list(ServerRequest request) {
        int page = parse(request.queryParam("pageNum").orElse("1"));
        int size = parse(request.queryParam("pageSize").orElse("20"));
        return users.findPage(page, Math.min(Math.max(size, 1), 100), "id", true)
                .map(pageData -> pageData.getRecords().stream()
                        .map(OpenUserHandler::minimal)
                        .toList())
                .map(rows -> Result.success(Map.of("rows", rows)))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private static Map<String, Object> minimal(UserEntity user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername() == null ? "" : user.getUsername(),
                "nickname", user.getNickName() == null ? "" : user.getNickName(),
                "deptId", user.getDeptId() == null ? 0L : user.getDeptId(),
                "status", user.getStatus() == null ? 0 : user.getStatus());
    }

    private int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            return 1;
        }
    }
}
