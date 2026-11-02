package com.apex.scaffold.integration.device.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.device.application.DeviceRuleManageService;
import com.apex.scaffold.integration.device.interfaces.dto.RuleCreateRequest;
import com.apex.scaffold.integration.device.interfaces.dto.RuleResponse;
import com.apex.scaffold.integration.device.interfaces.dto.RuleToggleRequest;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 物联网规则管理 Handler（方案 M5 / 批次 E3）。
 *
 * <p>租户始终取可信上下文；{@code @RequirePermission} 为元数据声明，由宿主安全切面执行。
 * 条件 JSON 的结构化白名单预检由应用层（{@code RuleCondition.parse}）完成。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "apex.component.integration.device",
        name = "interfaces-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IotRuleHandler {

    private final DeviceRuleManageService manageService;

    private final Validator validator;

    /**
     * 新增规则（结构化条件，禁止脚本执行）。
     *
     * @param request 当前请求（JSON body）
     * @return 落库后的规则
     */
    @RequirePermission("iot:rule:manage")
    @OperationLog(module = "iot", type = OperationLog.OperationType.INSERT, description = "新增物联网规则")
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(RuleCreateRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IotResponses.validate(body, validator))
                .flatMap(body -> IotResponses.currentTenant(request).flatMap(tenantId -> manageService
                        .create(
                                tenantId,
                                body.getRuleName(),
                                body.getConditionJson(),
                                body.getActionType(),
                                body.getActionConfig(),
                                body.getEnabled() == null || body.getEnabled())
                        .map(RuleResponse::from)))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 按 ID 查询规则。
     *
     * @param request 当前请求（path: id）
     * @return 规则详情
     */
    @RequirePermission("iot:rule:manage")
    public Mono<ServerResponse> get(ServerRequest request) {
        return IotResponses.currentTenant(request)
                .flatMap(tenantId -> manageService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .map(RuleResponse::from))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IotResponses::error);
    }

    /**
     * 规则启停。
     *
     * @param request 当前请求（path: id，JSON body）
     * @return 完成信号
     */
    @RequirePermission("iot:rule:manage")
    @OperationLog(module = "iot", type = OperationLog.OperationType.UPDATE, description = "启停物联网规则")
    public Mono<ServerResponse> toggle(ServerRequest request) {
        return request.bodyToMono(RuleToggleRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IotResponses.validate(body, validator))
                .map(body -> parseEnabled(body.getEnabled()))
                .flatMap(enabled -> IotResponses.currentTenant(request)
                        .flatMap(tenantId ->
                                manageService.toggle(tenantId, Long.valueOf(request.pathVariable("id")), enabled)))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(IotResponses::error);
    }

    private boolean parseEnabled(String enabled) {
        if ("true".equalsIgnoreCase(enabled)) {
            return true;
        }
        if ("false".equalsIgnoreCase(enabled)) {
            return false;
        }
        throw new BusinessException(ResultCode.PARAM_ERROR, "enabled 仅支持 true/false");
    }
}
