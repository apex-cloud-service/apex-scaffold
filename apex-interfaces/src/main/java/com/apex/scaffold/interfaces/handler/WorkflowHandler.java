package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.service.WorkflowService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.interfaces.dto.workflow.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 工作流 Handler。
 *
 * <p>基于 WebFlux 函数式编程模型的工作流接口处理器，
 * 提供流程回退、任务转办、任务委派、流程挂起/恢复等功能。
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>POST /api/workflow/tasks/{taskId}/rollback - 流程回退</li>
 *   <li>POST /api/workflow/tasks/{taskId}/transfer - 任务转办</li>
 *   <li>POST /api/workflow/tasks/{taskId}/delegate - 任务委派</li>
 *   <li>POST /api/workflow/instances/{instanceId}/suspend - 挂起流程</li>
 *   <li>POST /api/workflow/instances/{instanceId}/resume - 恢复流程</li>
 * </ul>
 *
 * <h2>请求示例</h2>
 * <pre>
 * POST /api/workflow/tasks/1000/rollback
 * Content-Type: application/json
 *
 * {
 *   "targetNodeCode": "manager_approval",
 *   "reason": "材料不完整，请补充后重新提交"
 * }
 * </pre>
 *
 * <h2>响应格式</h2>
 * <pre>
 * {
 *   "code": 0,
 *   "message": "操作成功",
 *   "data": null
 * }
 * </pre>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see WorkflowService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowHandler {

    private final WorkflowService workflowService;
    private final Validator validator;

    /**
     * 流程回退。
     *
     * <p>将当前任务回退到流程中的前置节点。
     *
     * @param request ServerRequest，路径参数 taskId，请求体 RollbackTaskRequest
     * @return ServerResponse，包含操作结果
     */
    public Mono<ServerResponse> rollbackTask(ServerRequest request) {
        Long taskId = Long.valueOf(request.pathVariable("taskId"));

        return request.bodyToMono(RollbackTaskRequest.class)
                .flatMap(req -> validate(req).thenReturn(req))
                .flatMap(req -> workflowService.rollbackToNode(
                        taskId, req.getTargetNodeCode(), getCurrentUserId(request), req.getReason()))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(this::handleError);
    }

    /**
     * 任务转办。
     *
     * <p>将当前待办任务转交给其他用户处理。
     *
     * @param request ServerRequest，路径参数 taskId，请求体 TransferTaskRequest
     * @return ServerResponse，包含操作结果
     */
    public Mono<ServerResponse> transferTask(ServerRequest request) {
        Long taskId = Long.valueOf(request.pathVariable("taskId"));

        return request.bodyToMono(TransferTaskRequest.class)
                .flatMap(req -> validate(req).thenReturn(req))
                .flatMap(req -> workflowService.transferTask(
                        taskId, req.getNewAssigneeId(), getCurrentUserId(request), req.getReason()))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(this::handleError);
    }

    /**
     * 任务委派。
     *
     * <p>将当前待办任务临时委托给其他用户处理。
     *
     * @param request ServerRequest，路径参数 taskId，请求体 DelegateTaskRequest
     * @return ServerResponse，包含操作结果
     */
    public Mono<ServerResponse> delegateTask(ServerRequest request) {
        Long taskId = Long.valueOf(request.pathVariable("taskId"));

        return request.bodyToMono(DelegateTaskRequest.class)
                .flatMap(req -> validate(req).thenReturn(req))
                .flatMap(req -> workflowService.delegateTask(
                        taskId, req.getDelegateToId(), getCurrentUserId(request), req.getReason()))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(this::handleError);
    }

    /**
     * 挂起流程实例。
     *
     * <p>暂停流程实例的执行，所有待办任务无法审批。
     *
     * @param request ServerRequest，路径参数 instanceId，请求体 SuspendInstanceRequest
     * @return ServerResponse，包含操作结果
     */
    public Mono<ServerResponse> suspendInstance(ServerRequest request) {
        Long instanceId = Long.valueOf(request.pathVariable("instanceId"));

        return request.bodyToMono(SuspendInstanceRequest.class)
                .flatMap(req -> validate(req).thenReturn(req))
                .flatMap(req -> workflowService.suspendInstance(instanceId, getCurrentUserId(request), req.getReason()))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(this::handleError);
    }

    /**
     * 恢复流程实例。
     *
     * <p>恢复挂起的流程实例，允许待办任务继续审批。
     *
     * @param request ServerRequest，路径参数 instanceId，请求体 ResumeInstanceRequest
     * @return ServerResponse，包含操作结果
     */
    public Mono<ServerResponse> resumeInstance(ServerRequest request) {
        Long instanceId = Long.valueOf(request.pathVariable("instanceId"));

        return request.bodyToMono(ResumeInstanceRequest.class)
                .flatMap(req -> validate(req).thenReturn(req))
                .flatMap(req -> workflowService.resumeInstance(instanceId, getCurrentUserId(request), req.getReason()))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(this::handleError);
    }

    /**
     * 参数校验。
     *
     * @param request 请求对象
     * @return 校验结果 Mono
     */
    private <T> Mono<Void> validate(T request) {
        BeanPropertyBindingResult errors =
                new BeanPropertyBindingResult(request, request.getClass().getName());
        validator.validate(request, errors);

        if (errors.hasErrors()) {
            String errorMsg = errors.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("参数校验失败");
            return Mono.error(new IllegalArgumentException(errorMsg));
        }

        return Mono.empty();
    }

    /**
     * 获取当前用户ID。
     *
     * @param request ServerRequest
     * @return 当前用户ID
     */
    private Long getCurrentUserId(ServerRequest request) {
        String userIdHeader = request.headers().firstHeader("X-User-Id");
        return userIdHeader != null ? Long.valueOf(userIdHeader) : 1L;
    }

    /**
     * 统一错误处理。
     *
     * @param error 异常
     * @return ServerResponse
     */
    private Mono<ServerResponse> handleError(Throwable error) {
        log.error("工作流操作失败", error);

        if (error instanceof IllegalArgumentException) {
            return ServerResponse.badRequest().bodyValue(Result.error(400, error.getMessage()));
        } else if (error instanceof IllegalStateException) {
            return ServerResponse.badRequest().bodyValue(Result.error(400, error.getMessage()));
        } else {
            return ServerResponse.status(500).bodyValue(Result.error(500, "系统内部错误：" + error.getMessage()));
        }
    }
}
