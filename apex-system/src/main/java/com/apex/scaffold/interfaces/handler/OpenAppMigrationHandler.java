package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.openplatform.OpenAppAdminService;
import com.apex.scaffold.application.openplatform.OpenAppMigrationService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.contract.dto.internal.OpenAppImportRequest;
import java.util.List;
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
@Component
public class OpenAppMigrationHandler {

    private final OpenAppMigrationService migration;

    private final OpenAppAdminService admin;

    public OpenAppMigrationHandler(OpenAppMigrationService migration, OpenAppAdminService admin) {
        this.migration = migration;
        this.admin = admin;
    }

    @RequirePermission("open:app:manage")
    @OperationLog(
            module = "OpenAppMigration",
            type = OperationLog.OperationType.SELECT,
            description = "exportLegacyOpenApps")
    public Mono<ServerResponse> export(ServerRequest request) {
        return migration.export().flatMap(items -> ServerResponse.ok().bodyValue(Result.success(items)));
    }

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
