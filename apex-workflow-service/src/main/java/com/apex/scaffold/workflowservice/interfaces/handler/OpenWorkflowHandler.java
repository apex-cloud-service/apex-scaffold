package com.apex.scaffold.workflowservice.interfaces.handler;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflowservice.interfaces.security.AuthenticatedUser;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放工作流接口：只读流程定义、发起实例与查询本人待办。
 *
 * <p>发起人必须来自开放应用绑定的主体账号（{@code sub_uid}），否则拒绝；不开放审批、
 * 转办、委派、挂起等需要用户权限的管理操作。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
public class OpenWorkflowHandler {

    private final WorkflowQueryService queries;

    private final WorkflowService workflows;

    public OpenWorkflowHandler(WorkflowQueryService queries, WorkflowService workflows) {
        this.queries = queries;
        this.workflows = workflows;
    }

    public Mono<ServerResponse> definitions(ServerRequest request) {
        int page = parse(request.queryParam("pageNum").orElse("1"));
        int size = parse(request.queryParam("pageSize").orElse("20"));
        return queries.listDefinitions(null, null, page, size)
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    public Mono<ServerResponse> definition(ServerRequest request) {
        return queries.getDefinitionDetail(request.pathVariable("workflowCode"))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    public Mono<ServerResponse> start(ServerRequest request) {
        return request.bodyToMono(StartRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> currentSubject().flatMap(subject -> workflows
                        .startProcessInstance(
                                request.pathVariable("workflowCode"), body.businessKey(), subject, body.contextJson())
                        .flatMap(instance -> ServerResponse.ok().bodyValue(Result.success(instance)))));
    }

    public Mono<ServerResponse> myTasks(ServerRequest request) {
        int page = parse(request.queryParam("pageNum").orElse("1"));
        int size = parse(request.queryParam("pageSize").orElse("20"));
        return currentSubject()
                .flatMap(subject -> queries.listMyTasks(subject, page, size))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    private Mono<Long> currentSubject() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .map(authentication -> authentication == null ? null : authentication.getPrincipal())
                .ofType(AuthenticatedUser.class)
                .map(AuthenticatedUser::userId)
                .filter(id -> id != null && id > 0)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.FORBIDDEN, "开放应用未绑定主体账号，无法发起或查询个人待办")));
    }

    private int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分页参数非法");
        }
    }

    /**
     * 发起流程请求体。
     *
     * @param businessKey 业务主键
     * @param contextJson 流程上下文 JSON
     */
    public record StartRequest(String businessKey, String contextJson) {}
}
