package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.openplatform.OpenAppAdminService;
import com.apex.scaffold.application.openplatform.OpenAppMigrationService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 存量开放应用迁移端点：只允许平台管理员触发，导出只读。
 *
 * <p>导出返回待导入清单（含 bcrypt 哈希，不含明文）；导入逐条写 auth 单源并汇总失败项。
 * 端点不暴露删除旧数据的能力，旧表由运维在比对窗口结束后单独下线。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Tag(name = "开放应用迁移", description = "平台管理员专用的存量开放应用导出与批量导入")
@Component
public class OpenAppMigrationHandler {

    private final OpenAppMigrationService migration;

    private final OpenAppAdminService admin;

    /**
     * 构造处理器。
     *
     * @param migration 迁移导出服务
     * @param admin 开放应用管理服务，导入复用其单源写入
     */
    public OpenAppMigrationHandler(OpenAppMigrationService migration, OpenAppAdminService admin) {
        this.migration = migration;
        this.admin = admin;
    }

    /**
     * 导出待迁移的存量开放应用清单（含哈希，不含明文密钥）。
     *
     * @param request 服务器请求
     * @return 统一响应，{@code data} 为待导入清单
     */
    @Operation(
            operationId = "exportLegacyOpenApps",
            summary = "导出待迁移的存量开放应用清单",
            description = "返回待导入清单（含密码哈希，不含明文密钥），需平台管理员权限")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "导出成功，data 为待导入清单",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 open:app:manage"),
        @ApiResponse(responseCode = "500", description = "服务端异常")
    })
    @RequirePermission("open:app:manage")
    @OperationLog(
            module = "OpenAppMigration",
            type = OperationLog.OperationType.SELECT,
            description = "exportLegacyOpenApps")
    public Mono<ServerResponse> export(ServerRequest request) {
        return migration.export().flatMap(items -> ServerResponse.ok().bodyValue(Result.success(items)));
    }

    /**
     * 批量导入存量开放应用，逐条汇总成功与失败。
     *
     * <p>单条失败不影响其余条目；返回的失败项只含异常类型，不带敏感信息。
     *
     * @param request 服务器请求，请求体为 {@link OpenAppImportRequest} 数组
     * @return 统一响应，{@code data} 为导入汇总
     */
    @Operation(
            operationId = "importLegacyOpenApps",
            summary = "批量导入存量开放应用",
            description = "逐条写入并汇总成功与失败；单条失败不影响其余条目，失败项只含异常类型")
    @RequestBody(
            required = true,
            description = "待导入的开放应用数组",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            array = @ArraySchema(schema = @Schema(implementation = OpenAppImportRequest.class))))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "导入完成，data 为成功/失败汇总",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
        @ApiResponse(responseCode = "400", description = "请求体为空或非 JSON 数组"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 open:app:manage"),
        @ApiResponse(responseCode = "500", description = "服务端异常")
    })
    @RequirePermission("open:app:manage")
    @OperationLog(
            module = "OpenAppMigration",
            type = OperationLog.OperationType.INSERT,
            description = "importLegacyOpenApps")
    public Mono<ServerResponse> importApps(ServerRequest request) {
        return importList(request);
    }

    private Mono<ServerResponse> importList(ServerRequest request) {
        return request.bodyToFlux(OpenAppImportRequest.class)
                .concatMap(item -> admin.importApp(item)
                        .map(response -> new Outcome(item.getImportId(), true, null))
                        .onErrorResume(error -> Mono.just(new Outcome(
                                item.getImportId(), false, error.getClass().getSimpleName()))))
                .collectList()
                .map(results -> new Summary(
                        results.size(),
                        results.stream().filter(Outcome::success).count(),
                        results.stream().filter(result -> !result.success()).toList()))
                .flatMap(summary -> ServerResponse.ok().bodyValue(Result.success(summary)));
    }

    /**
     * 单条导入结果。
     *
     * @param importId 源标识
     * @param success 是否成功
     * @param error 失败类型（不含敏感信息）
     */
    public record Outcome(String importId, boolean success, String error) {}

    /**
     * 导入汇总。
     *
     * @param total 总数
     * @param succeeded 成功数
     * @param failures 失败清单
     */
    public record Summary(long total, long succeeded, List<Outcome> failures) {}
}
