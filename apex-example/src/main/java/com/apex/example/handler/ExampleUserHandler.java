package com.apex.example.handler;

import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import com.apex.example.dto.CreateUserRequest;
import com.apex.example.service.ExampleUserService;
import com.apex.scaffold.common.annotation.FeatureGate;
import com.apex.scaffold.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 示例用户处理器
 *
 * <p>展示如何编写WebFlux函数式端点:
 * <ul>
 *   <li>使用ServerRequest/ServerResponse</li>
 *   <li>集成Swagger文档注解</li>
 *   <li>统一响应格式封装</li>
 *   <li>商业版功能授权(@FeatureGate)</li>
 * </ul>
 *
 * <p>异常处理约定: 处理器内不捕获异常,统一由
 * {@link com.apex.scaffold.interfaces.exception.GlobalExceptionHandler}
 * 转换为标准 {@link Result} 错误响应。
 *
 * @author Apex Team
 * @version 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "示例模块-用户管理", description = "展示脚手架的使用方法")
public class ExampleUserHandler {

    private final ExampleUserService userService;

    /**
     * 创建用户
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(summary = "创建用户", description = "创建一个新用户")
    public Mono<ServerResponse> createUser(ServerRequest request) {
        return request.bodyToMono(CreateUserRequest.class)
                .flatMap(userService::createUser)
                .flatMap(response -> ok().bodyValue(Result.success(response)));
    }

    /**
     * 查询用户详情
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(summary = "查询用户", description = "根据ID查询用户详情")
    public Mono<ServerResponse> getUser(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));

        return userService.getUserById(id).flatMap(response -> ok().bodyValue(Result.success(response)));
    }

    /**
     * 查询用户列表
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(summary = "查询用户列表", description = "查询所有用户")
    public Mono<ServerResponse> listUsers(ServerRequest request) {
        return userService.listUsers().flatMap(responses -> ok().bodyValue(Result.success(responses)));
    }

    /**
     * 更新用户
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(summary = "更新用户", description = "根据ID更新用户信息")
    public Mono<ServerResponse> updateUser(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));

        return request.bodyToMono(CreateUserRequest.class)
                .flatMap(dto -> userService.updateUser(id, dto))
                .flatMap(response -> ok().bodyValue(Result.success(response)));
    }

    /**
     * 删除用户
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(summary = "删除用户", description = "根据ID逻辑删除用户")
    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));

        return userService.deleteUser(id).then(ok().bodyValue(Result.success("删除成功")));
    }

    /**
     * 高级运营报表(商业版功能)
     *
     * <p>{@code @FeatureGate}示例接口: 该接口需要License中授权了
     * {@code premium_report}功能才能访问(标准版/企业版)。
     * 未授权时由{@link com.apex.scaffold.common.license.FeatureGateAspect}
     * 返回权限不足的错误响应。
     *
     * @param request HTTP请求
     * @return 响应结果
     */
    @Operation(summary = "高级运营报表(商业版)", description = "@FeatureGate示例: 需标准版/企业版License授权 premium_report 功能")
    @FeatureGate(value = "premium_report", message = "高级运营报表需要标准版或企业版License,请升级后重试")
    public Mono<ServerResponse> premiumReport(ServerRequest request) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportId", "RPT-" + LocalDate.now() + "-001");
        report.put("title", "月度运营报表");
        report.put("generatedAt", LocalDate.now().toString());
        report.put("totalUsers", 1280);
        report.put("activeUsers", 356);
        return ok().bodyValue(Result.success(report));
    }
}
