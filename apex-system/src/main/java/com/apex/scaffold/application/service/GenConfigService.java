package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.GenConfigDTO;
import com.apex.scaffold.application.dto.admin.GenConfigRequest;
import com.apex.scaffold.application.dto.admin.GenHistoryDTO;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import com.apex.scaffold.domain.repository.GenConfigRepository;
import com.apex.scaffold.domain.repository.GenDatasourceRepository;
import com.apex.scaffold.domain.repository.GenHistoryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 代码生成配置应用服务。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenConfigService {

    private static final String[] ALLOWED_OUTPUT_PATHS = {"/Users/work/apex-scaffold/output", "/tmp/apex-gen"};

    private final GenConfigRepository configRepository;
    private final GenDatasourceRepository datasourceRepository;
    private final GenHistoryRepository historyRepository;
    private final ObjectMapper objectMapper;
    private final TenantGuard tenantGuard;

    /**
     * 创建代码生成配置。
     *
     * <p>校验输出路径白名单与数据源租户归属后写入新配置。</p>
     *
     * @param request 生成配置创建请求
     * @return 创建后的生成配置 DTO
     */
    public Mono<GenConfigDTO> create(GenConfigRequest request) {
        return tenantGuard.assertActive().then(Mono.defer(() -> tenantId()
                .flatMap(tenant -> validateOutputPath(request.getOutputPath())
                        .then(Mono.defer(() -> datasourceRepository.findById(request.getDatasourceId())))
                        .publishOn(BlockingIoSchedulers.jdbc())
                        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在")))
                        .flatMap(datasource -> requireTenant(datasource.getTenantId(), tenant))
                        .then(Mono.defer(() -> {
                            GenConfigEntity entity = toEntity(request, tenant);
                            return configRepository.insert(entity).thenReturn(toDTO(entity));
                        })))));
    }

    /**
     * 更新代码生成配置。
     *
     * <p>校验配置与数据源的租户归属后按请求内容更新原记录。</p>
     *
     * @param id 配置 ID
     * @param request 生成配置更新请求
     * @return 更新后的生成配置 DTO
     */
    public Mono<GenConfigDTO> update(Long id, GenConfigRequest request) {
        return tenantId().flatMap(tenant -> validateOutputPath(request.getOutputPath())
                .then(Mono.defer(() -> configRepository.findById(id)))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "生成配置不存在")))
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant).thenReturn(entity))
                .flatMap(entity -> datasourceRepository
                        .findById(request.getDatasourceId())
                        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在")))
                        .flatMap(datasource -> requireTenant(datasource.getTenantId(), tenant))
                        .then(Mono.defer(() -> {
                            copy(request, entity, tenant);
                            return configRepository.updateById(entity).thenReturn(toDTO(entity));
                        }))));
    }

    /**
     * 删除代码生成配置。
     *
     * <p>校验租户归属后执行删除，返回是否删除成功。</p>
     *
     * @param id 配置 ID
     * @return 是否删除成功
     */
    public Mono<Boolean> delete(Long id) {
        return tenantId().flatMap(tenant -> configRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "生成配置不存在")))
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant).thenReturn(entity))
                .then(Mono.defer(() -> configRepository.deleteById(id)))
                .map(rows -> rows > 0));
    }

    /**
     * 按 ID 查询代码生成配置。
     *
     * <p>校验租户归属，配置不存在时返回空 Mono。</p>
     *
     * @param id 配置 ID
     * @return 生成配置 DTO，不存在时返回空 Mono
     */
    public Mono<GenConfigDTO> findById(Long id) {
        return tenantId().flatMap(tenant -> configRepository
                .findById(id)
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant).thenReturn(toDTO(entity))));
    }

    /**
     * 分页查询当前租户的代码生成配置。
     *
     * <p>过滤与分页在数据库侧完成，返回真实总数，前端按 {@link PageResponse} 渲染分页器。
     *
     * @param configName 配置名称模糊匹配条件，可为空
     * @param datasourceId 数据源 ID，为空时查询全部
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 生成配置分页结果
     */
    public Mono<PageResponse<GenConfigDTO>> list(String configName, Long datasourceId, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        return tenantId().flatMap(tenant -> configRepository
                .search(configName, datasourceId, pageNum, pageSize)
                .map(page -> PageResponse.of(
                        page.getRecords().stream()
                                .filter(item -> tenant.equals(item.getTenantId()))
                                .map(this::toDTO)
                                .toList(),
                        page.getTotal(),
                        pageNum,
                        pageSize)));
    }

    /**
     * 分页查询指定代码生成配置的生成历史。
     *
     * @param configId 配置 ID
     * @param pageNum 页码（从 1 开始）
     * @param pageSize 每页大小
     * @return 生成历史分页结果
     */
    public Mono<PageResponse<GenHistoryDTO>> listHistory(Long configId, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        return findById(configId).flatMap(config -> historyRepository
                .searchByConfigId(configId, pageNum, pageSize)
                .map(page -> PageResponse.of(
                        page.getRecords().stream()
                                .filter(item -> config.getTenantId().equals(item.getTenantId()))
                                .map(this::toHistoryDTO)
                                .toList(),
                        page.getTotal(),
                        pageNum,
                        pageSize)));
    }

    private Mono<String> tenantId() {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            return tenant == null || tenant.isBlank()
                    ? Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户上下文不能为空"))
                    : Mono.just(tenant);
        });
    }

    private Mono<Void> requireTenant(String entityTenant, String tenant) {
        if (!tenant.equals(entityTenant)) {
            return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权访问其他租户的数据"));
        }
        return Mono.empty();
    }

    private GenConfigEntity toEntity(GenConfigRequest request, String tenant) {
        GenConfigEntity entity = new GenConfigEntity();
        copy(request, entity, tenant);
        return entity;
    }

    private void copy(GenConfigRequest request, GenConfigEntity entity, String tenant) {
        entity.setTenantId(tenant);
        entity.setConfigName(request.getConfigName());
        entity.setDatasourceId(request.getDatasourceId());
        entity.setTableNames(jsonOrSingle(request.getTableNames(), request.getTableName()));
        entity.setPackageName(request.getPackageName());
        entity.setModuleName(request.getModuleName());
        entity.setAuthor(request.getAuthor());
        entity.setTablePrefix(request.getTablePrefix());
        entity.setOutputPath(request.getOutputPath());
        entity.setTemplateIds(request.getTemplateIds());
        entity.setExtraConfig(request.getExtraConfig());
    }

    private GenConfigDTO toDTO(GenConfigEntity entity) {
        GenConfigDTO dto = new GenConfigDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setDatasourceId(entity.getDatasourceId());
        dto.setConfigName(entity.getConfigName());
        dto.setTableNames(entity.getTableNames());
        dto.setTemplateIds(entity.getTemplateIds());
        dto.setExtraConfig(entity.getExtraConfig());
        dto.setTableName(firstTable(entity.getTableNames()));
        dto.setPackageName(entity.getPackageName());
        dto.setModuleName(entity.getModuleName());
        dto.setAuthor(entity.getAuthor());
        dto.setTablePrefix(entity.getTablePrefix());
        dto.setOutputPath(entity.getOutputPath());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        return dto;
    }

    private void validatePage(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分页参数必须为正数");
        }
    }

    private GenHistoryDTO toHistoryDTO(com.apex.scaffold.domain.model.generator.GenHistoryEntity entity) {
        return GenHistoryDTO.builder()
                .id(entity.getId())
                .configId(entity.getConfigId())
                .generatedFiles(entity.getGeneratedFiles())
                .outputPath(entity.getOutputPath())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .operatorId(entity.getOperatorId())
                .tenantId(entity.getTenantId())
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private String jsonOrSingle(String json, String tableName) {
        if (json != null && !json.isBlank()) {
            return json;
        }
        if (tableName == null || tableName.isBlank()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(List.of(tableName));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "表名格式不合法");
        }
    }

    private String firstTable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            List<String> tables = objectMapper.readValue(
                    value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return tables.isEmpty() ? null : tables.get(0);
        } catch (Exception e) {
            return value;
        }
    }

    private Mono<Void> validateOutputPath(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "输出路径不能为空"));
        }
        Path path = Paths.get(outputPath).toAbsolutePath().normalize();
        boolean allowed = false;
        for (String root : ALLOWED_OUTPUT_PATHS) {
            if (path.startsWith(Paths.get(root).toAbsolutePath().normalize())) {
                allowed = true;
                break;
            }
        }
        return allowed ? Mono.empty() : Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "输出路径必须在允许的路径白名单内"));
    }
}
