package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.service.JobService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 定时任务处理器（函数式端点）。
 *
 * <p>提供定时任务的查询、创建、更新、删除、启停、手动触发与执行日志
 * 回溯 API，所有端点要求 {@code system:job:*} 权限。任务通过动态调度器
 * 在应用内执行，支持 Cron 表达式驱动的周期调度。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/jobs - 查询任务列表</li>
 *   <li>GET /admin-api/v1/jobs/{id} - 查询任务详情</li>
 *   <li>POST /admin-api/v1/jobs - 创建任务</li>
 *   <li>PUT /admin-api/v1/jobs/{id} - 更新任务</li>
 *   <li>DELETE /admin-api/v1/jobs/{id} - 删除任务</li>
 *   <li>PUT /admin-api/v1/jobs/{id}/status - 启用/停用任务</li>
 *   <li>POST /admin-api/v1/jobs/{id}/run - 立即执行一次任务</li>
 *   <li>GET /admin-api/v1/jobs/{id}/logs - 查询任务执行日志</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see JobService
 */
@Component
@Tag(name = "后台-定时任务", description = "定时任务管理与执行日志")
public class JobHandler extends BaseHandler {

    /**
     * 定时任务应用服务。
     */
    private final JobService jobService;

    /**
     * 创建定时任务处理器。
     *
     * @param jobService 定时任务应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public JobHandler(JobService jobService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.jobService = jobService;
    }

    /**
     * 查询任务列表。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为任务 DTO 列表
     */
    @RequirePermission("system:job:list")
    @Operation(operationId = "adminListJobs", summary = "查询定时任务列表")
    public Mono<ServerResponse> list(ServerRequest request) {
        return jobService.listJobs().flatMap(this::ok);
    }

    /**
     * 查询任务详情。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为任务实体
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:query")
    @Operation(operationId = "adminGetJob", summary = "查询任务详情")
    public Mono<ServerResponse> detail(ServerRequest request) {
        return jobService.getJob(jobId(request)).flatMap(this::ok);
    }

    /**
     * 创建任务。
     *
     * <p>请求体必填并经 Jakarta Bean Validation 校验，Cron 表达式
     * 合法性由服务端解析器校验。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的任务实体
     * @throws BusinessException 当请求体为空时抛出
     */
    @RequirePermission("system:job:add")
    @Operation(operationId = "adminCreateJob", summary = "创建定时任务")
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.INSERT, description = "创建定时任务")
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(JobRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(jobService::createJob)
                .flatMap(this::ok);
    }

    /**
     * 更新任务。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的任务实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:job:edit")
    @Operation(operationId = "adminUpdateJob", summary = "更新定时任务")
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.UPDATE, description = "更新定时任务")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = jobId(request);
        return request.bodyToMono(JobRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> jobService.updateJob(id, body))
                .flatMap(this::ok);
    }

    /**
     * 删除任务。
     *
     * <p>删除后同步取消调度链。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:remove")
    @Operation(operationId = "adminDeleteJob", summary = "删除定时任务")
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.DELETE, description = "删除定时任务")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return jobService.deleteJob(jobId(request)).flatMap(deleted -> ok(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 启用或停用任务。
     *
     * <p>启用后注册调度，停用后取消调度。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的任务实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:job:edit")
    @Operation(operationId = "adminUpdateJobStatus", summary = "启用或停用定时任务")
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.UPDATE, description = "更新任务状态")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = jobId(request);
        return request.bodyToMono(StatusRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(this::validateAndThrow)
                .flatMap(body -> jobService.updateStatus(id, body.isEnabled()))
                .flatMap(this::ok);
    }

    /**
     * 立即执行一次任务。
     *
     * <p>不改变调度状态，仅触发一次执行并记录日志。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"triggered": true}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:run")
    @Operation(operationId = "adminRunJob", summary = "立即执行一次任务")
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.UPDATE, description = "手动触发任务")
    public Mono<ServerResponse> runOnce(ServerRequest request) {
        return jobService.runOnce(jobId(request)).then(ok(java.util.Map.of("triggered", true)));
    }

    /**
     * 查询任务执行日志。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数，
     *                查询参数 {@code limit} 缺省 20
     * @return 带 traceId 的统一成功响应，data 为执行日志 DTO 列表
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:query")
    @Operation(operationId = "adminListJobLogs", summary = "查询任务执行日志")
    public Mono<ServerResponse> logs(ServerRequest request) {
        int limit = safeParseInt(request.queryParam("limit").orElse(null), 20);
        return jobService.listLogs(jobId(request), limit).flatMap(this::ok);
    }

    /**
     * 解析并校验路径参数 {@code id}。
     *
     * @param request HTTP 请求
     * @return 大于 0 的任务 ID
     * @throws BusinessException 当参数缺失、非数字或不大于 0 时抛出
     */
    private Long jobId(ServerRequest request) {
        Long id = safeParseLong(request.pathVariable("id"));
        if (id == null || id <= 0) throw new BusinessException(ResultCode.PARAM_ERROR, "id格式错误");
        return id;
    }

    /**
     * 构造统一 JSON 成功响应。
     *
     * @param data 业务数据，可为 {@code null}
     * @return 携带当前 traceId 的 200 JSON 响应
     */
    private <T> Mono<ServerResponse> ok(T data) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(data, TraceIdUtil.getTraceId()));
    }

    /**
     * 状态更新请求体。
     *
     * <p>仅承载 {@code enabled} 布尔值，表示启用或停用目标对象。</p>
     */
    public static class StatusRequest {

        /** 是否启用：true-启用，false-停用 */
        private boolean enabled;

        /**
         * 构造状态更新请求体。
         */
        public StatusRequest() {}

        /**
         * 获取是否启用。
         *
         * @return true-启用，false-停用
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置是否启用。
         *
         * @param enabled true-启用，false-停用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
