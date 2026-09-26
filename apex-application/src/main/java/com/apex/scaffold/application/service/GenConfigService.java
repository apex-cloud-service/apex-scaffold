package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.GenConfigDTO;
import com.apex.scaffold.application.dto.admin.GenConfigRequest;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 代码生成配置应用服务。
 *
 * <p>负责代码生成器配置的创建、更新、删除、分页查询及生成历史查询等用例编排，
 * 并对输出路径执行非空、路径遍历（{@code ..}）与白名单安全校验。
 *
 * <p>当前除路径校验外的持久化用例尚未接入领域仓储，调用将抛出业务异常。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenConfigService {

    private static final String[] ALLOWED_OUTPUT_PATHS = {
        "/Users/work/apex-scaffold/output", "/tmp/apex-gen", "./output", "../output"
    };

    public Mono<GenConfigDTO> create(GenConfigRequest request) {
        return validateOutputPath(request.getOutputPath()).then(Mono.defer(() -> {
            log.info("创建代码生成配置: tableName={}, entityName={}", request.getTableName(), request.getEntityName());
            return Mono.error(new BusinessException(
                    ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenConfigEntity 和 GenConfigRepository 后完善此方法"));
        }));
    }

    public Mono<GenConfigDTO> update(Long id, GenConfigRequest request) {
        return validateOutputPath(request.getOutputPath()).then(Mono.defer(() -> {
            log.info("更新代码生成配置: id={}, tableName={}", id, request.getTableName());
            return Mono.error(new BusinessException(
                    ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenConfigEntity 和 GenConfigRepository 后完善此方法"));
        }));
    }

    public Mono<Boolean> delete(Long id) {
        log.info("删除代码生成配置: id={}", id);
        return Mono.error(new BusinessException(
                ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenConfigEntity 和 GenConfigRepository 后完善此方法"));
    }

    public Mono<GenConfigDTO> findById(Long id) {
        log.debug("查询代码生成配置: id={}", id);
        return Mono.error(new BusinessException(
                ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenConfigEntity 和 GenConfigRepository 后完善此方法"));
    }

    public Mono<PageResponse<GenConfigDTO>> list(Long datasourceId, int pageNum, int pageSize) {
        log.debug("分页查询代码生成配置: datasourceId={}, pageNum={}, pageSize={}", datasourceId, pageNum, pageSize);
        return Mono.error(new BusinessException(
                ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenConfigEntity 和 GenConfigRepository 后完善此方法"));
    }

    public Mono<PageResponse<GenConfigDTO>> listHistory(Long configId, int pageNum, int pageSize) {
        log.debug("分页查询代码生成历史: configId={}, pageNum={}, pageSize={}", configId, pageNum, pageSize);
        return Mono.error(new BusinessException(
                ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenConfigEntity 和 GenConfigRepository 后完善此方法"));
    }

    private Mono<Void> validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.trim().isEmpty()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "输出路径不能为空"));
        }

        if (outputPath.contains("..")) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "输出路径不能包含 '..'，存在路径遍历安全风险"));
        }

        String normalizedPath = Paths.get(outputPath).normalize().toString();
        boolean isAllowed = false;
        for (String allowedPath : ALLOWED_OUTPUT_PATHS) {
            String normalizedAllowed = Paths.get(allowedPath).normalize().toString();
            if (normalizedPath.startsWith(normalizedAllowed)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            log.warn("输出路径不在白名单内: {}", outputPath);
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "输出路径必须在允许的路径白名单内"));
        }

        return Mono.empty();
    }
}
