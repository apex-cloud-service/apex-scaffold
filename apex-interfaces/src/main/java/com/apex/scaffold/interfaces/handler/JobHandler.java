package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.dto.admin.StatusRequest;
import com.apex.scaffold.application.service.JobService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
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
        return jobService.listJobs().flatMap(this::buildSuccess);
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
        return jobService.getJob(requirePositiveId(request)).flatMap(this::buildSuccess);
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
        return parseBody(request, JobRequest.class)
                .flatMap(jobService::createJob)
                .flatMap(this::buildSuccess);
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
        Long id = requirePositiveId(request);
        return parseBody(request, JobRequest.class)
                .flatMap(body -> jobService.updateJob(id, body))
                .flatMap(this::buildSuccess);
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
        return jobService
                .deleteJob(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
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
        Long id = requirePositiveId(request);
        return parseBody(request, StatusRequest.class)
                .flatMap(body -> jobService.updateStatus(id, body.isEnabled()))
                .flatMap(this::buildSuccess);
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
        return jobService.runOnce(requirePositiveId(request)).then(buildSuccess(java.util.Map.of("triggered", true)));
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
        return jobService.listLogs(requirePositiveId(request), limit).flatMap(this::buildSuccess);
    }
}
