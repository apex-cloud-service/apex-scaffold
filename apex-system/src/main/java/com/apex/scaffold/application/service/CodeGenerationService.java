package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.GeneratedFileDTO;
import com.apex.scaffold.application.dto.admin.TableInfoDTO;
import com.apex.scaffold.application.support.CodeTemplateEngine;
import com.apex.scaffold.application.support.DataSourcePoolManager;
import com.apex.scaffold.application.support.MetadataReader;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.security.ConfigEncryptionService;
import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import com.apex.scaffold.domain.model.generator.GenDatasourceEntity;
import com.apex.scaffold.domain.model.generator.GenHistoryEntity;
import com.apex.scaffold.domain.model.generator.GenTemplateEntity;
import com.apex.scaffold.domain.repository.GenConfigRepository;
import com.apex.scaffold.domain.repository.GenDatasourceRepository;
import com.apex.scaffold.domain.repository.GenHistoryRepository;
import com.apex.scaffold.domain.repository.GenTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 代码生成服务
 *
 * <p>提供数据源表查询、表结构获取和代码生成功能。
 * 使用缓存优化表元数据查询和模板编译性能。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final DataSourcePoolManager dataSourcePoolManager;
    private final MetadataReader metadataReader;
    private final CodeTemplateEngine templateEngine;
    private final GenConfigRepository configRepository;
    private final GenDatasourceRepository datasourceRepository;
    private final GenTemplateRepository templateRepository;
    private final GenHistoryRepository historyRepository;
    private final ConfigEncryptionService encryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 模板编译缓存
     * key: 模板内容的hash
     * value: 编译后的模板对象
     * @since 1.2.0
     */
    private final ConcurrentHashMap<String, String> templateCache = new ConcurrentHashMap<>(256);

    /**
     * 获取数据源表列表
     *
     * @param datasourceId 数据源ID
     * @return 表名列表
     * @since 1.2.0
     */
    public Flux<String> getTables(Long datasourceId) {
        log.debug("获取数据源表列表: datasourceId={}", datasourceId);
        return tenantId()
                .flatMapMany(tenant -> datasourceForTenant(datasourceId).flatMapMany(datasource -> dataSourcePoolManager
                        .getOrCreateDataSource(
                                tenant,
                                datasourceId,
                                datasource.getParams(),
                                datasource.getUsername(),
                                encryptionService.decrypt(datasource.getPassword()))
                        .flatMapMany(pool -> metadataReader.getTables(
                                pool, datasource.getDbType().name().toLowerCase()))));
    }

    /**
     * 获取表结构信息(带缓存)
     * 缓存10分钟,减少数据库元数据查询
     *
     * @param datasourceId 数据源ID
     * @param tableName 表名
     * @return 表结构信息
     * @since 1.2.0
     */
    public Mono<TableInfoDTO> getTableColumns(Long datasourceId, String tableName) {
        log.debug("获取表列信息(缓存未命中): datasourceId={}, tableName={}", datasourceId, tableName);
        return tenantId().flatMap(tenant -> datasourceForTenant(datasourceId)
                .flatMap(datasource -> dataSourcePoolManager
                        .getOrCreateDataSource(
                                tenant,
                                datasourceId,
                                datasource.getParams(),
                                datasource.getUsername(),
                                encryptionService.decrypt(datasource.getPassword()))
                        .flatMap(pool -> metadataReader.getTableColumns(
                                pool, datasource.getDbType().name().toLowerCase(), tableName)))
                .cache(Duration.ofMinutes(10)));
    }

    /**
     * 执行代码生成
     *
     * @param configId 生成配置ID
     * @return 生成的文件列表
     * @since 1.2.0
     */
    public Mono<List<GeneratedFileDTO>> generate(Long configId) {
        log.info("开始代码生成: configId={}", configId);
        return tenantId().flatMap(tenant -> configRepository
                .findById(configId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "生成配置不存在")))
                .flatMap(config -> requireTenant(config.getTenantId(), tenant).thenReturn(config))
                .flatMap(config -> datasourceForTenant(config.getDatasourceId()).flatMap(datasource -> templates(
                                config.getTemplateIds(), tenant)
                        .flatMap(templateList -> tables(config.getTableNames())
                                .flatMapMany(Flux::fromIterable)
                                .concatMap(table -> getTableColumns(config.getDatasourceId(), table)
                                        .flatMap(info -> executeGeneration(config, datasource, templateList, info)))
                                .flatMapIterable(files -> files)
                                .collectList()
                                .flatMap(files ->
                                        saveHistory(config, tenant, files).thenReturn(files)))
                        .onErrorResume(
                                error -> saveFailure(config, tenant, error).then(Mono.error(error))))));
    }

    /**
     * 执行代码生成核心逻辑
     * 使用阻塞IO调度器隔离文件写入操作
     *
     * @param config 生成配置
     * @param datasource 数据源
     * @param templates 模板列表
     * @param tableInfo 表信息
     * @return 生成的文件列表
     * @since 1.2.0
     */
    private Mono<List<GeneratedFileDTO>> executeGeneration(
            GenConfigEntity config,
            GenDatasourceEntity datasource,
            List<GenTemplateEntity> templates,
            TableInfoDTO tableInfo) {
        Map<String, Object> dataModel = buildDataModel(config, tableInfo);
        return Flux.fromIterable(templates)
                .concatMap(template -> templateEngine
                        .render(template.getTemplateContent(), dataModel)
                        .flatMap(content -> Mono.fromCallable(() -> {
                                    String output = buildOutputFilePath(config, template, dataModel);
                                    writeFile(config.getOutputPath(), output, content);
                                    return GeneratedFileDTO.builder()
                                            .fileType(template.getTemplateType())
                                            .filePath(output)
                                            .fileName(Paths.get(output)
                                                    .getFileName()
                                                    .toString())
                                            .fileSize((long) content.getBytes(StandardCharsets.UTF_8).length)
                                            .success(true)
                                            .build();
                                })
                                .subscribeOn(BlockingIoSchedulers.storage()))
                        .onErrorResume(error -> Mono.just(GeneratedFileDTO.builder()
                                .fileType(template.getTemplateType())
                                .success(false)
                                .errorMessage(error.getMessage())
                                .build())))
                .collectList();
    }

    /**
     * 构建数据模型
     *
     * @param config 生成配置
     * @param tableInfo 表信息
     * @return 数据模型
     * @since 1.2.0
     */
    private Map<String, Object> buildDataModel(GenConfigEntity config, TableInfoDTO tableInfo) {
        Map<String, Object> model = new HashMap<>(8);

        model.put("tableName", tableInfo.getTableName());
        model.put("tableComment", tableInfo.getTableComment());
        model.put("columns", tableInfo.getColumns());
        model.put("packageName", config.getPackageName());
        model.put("moduleName", config.getModuleName());
        model.put("author", config.getAuthor());
        model.put("datetime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        return model;
    }

    private Mono<List<GenTemplateEntity>> templates(String value, String tenant) {
        List<Long> templateIds = ids(value);
        if (templateIds.isEmpty()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "未配置代码模板"));
        }
        return Flux.fromIterable(templateIds)
                .concatMap(id -> templateRepository
                        .findById(id)
                        .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "代码模板不存在"))))
                .handle((template, sink) -> {
                    boolean allowed = tenant.equals(template.getTenantId())
                            || (Integer.valueOf(1).equals(template.getIsBuiltin())
                                    && "system".equals(template.getTenantId()));
                    if (allowed) {
                        sink.next(template);
                    } else {
                        sink.error(new BusinessException(ResultCode.FORBIDDEN, "无权使用该代码模板"));
                    }
                })
                .cast(GenTemplateEntity.class)
                .collectList();
    }

    private Mono<List<String>> tables(String json) {
        if (json == null || json.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "未配置生成表"));
        }
        try {
            List<String> names = objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return names.isEmpty() || names.stream().anyMatch(name -> name == null || name.isBlank())
                    ? Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "生成表配置不合法"))
                    : Mono.just(names);
        } catch (Exception e) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "生成表配置不合法"));
        }
    }

    private List<Long> ids(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private Mono<GenDatasourceEntity> datasourceForTenant(Long id) {
        return tenantId().flatMap(tenant -> datasourceRepository
                .findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在")))
                .flatMap(entity -> requireTenant(entity.getTenantId(), tenant).thenReturn(entity)));
    }

    private Mono<String> tenantId() {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            return tenant == null || tenant.isBlank()
                    ? Mono.error(new BusinessException(ResultCode.FORBIDDEN, "租户上下文不能为空"))
                    : Mono.just(tenant);
        });
    }

    private Mono<Void> requireTenant(String actual, String expected) {
        return expected.equals(actual)
                ? Mono.empty()
                : Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权访问其他租户的数据"));
    }

    private Mono<Void> saveHistory(GenConfigEntity config, String tenant, List<GeneratedFileDTO> files) {
        GenHistoryEntity history = history(config, tenant);
        history.setStatus(
                files.stream().allMatch(file -> Boolean.TRUE.equals(file.getSuccess())) ? "SUCCESS" : "FAILED");
        try {
            history.setGeneratedFiles(objectMapper.writeValueAsString(files));
        } catch (Exception e) {
            history.setGeneratedFiles("[]");
        }
        return historyRepository.insert(history).then();
    }

    private Mono<Void> saveFailure(GenConfigEntity config, String tenant, Throwable error) {
        GenHistoryEntity history = history(config, tenant);
        history.setStatus("FAILED");
        history.setErrorMessage(error.getMessage());
        return historyRepository.insert(history).then();
    }

    private GenHistoryEntity history(GenConfigEntity config, String tenant) {
        GenHistoryEntity history = new GenHistoryEntity();
        history.setConfigId(config.getId());
        history.setOutputPath(config.getOutputPath());
        history.setTenantId(tenant);
        return history;
    }

    /**
     * 构建输出文件路径
     * @since 1.2.0
     */
    private String buildOutputFilePath(
            GenConfigEntity config, GenTemplateEntity template, Map<String, Object> dataModel) {
        Path root = Paths.get(config.getOutputPath()).toAbsolutePath().normalize();
        String name = String.valueOf(dataModel.get("tableName"));
        String relative = template.getTemplatePath();
        if (relative == null || relative.isBlank()) {
            relative = template.getTemplateType() + "/" + name
                    + (template.getFileSuffix() == null ? "" : template.getFileSuffix());
        } else {
            relative = relative.replace("${tableName}", name);
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板输出路径超出配置目录");
        }
        return target.toString();
    }

    /**
     * 获取模板类型
     * @since 1.2.0
     */
    private String getTemplateType(GenTemplateEntity template) {
        return template.getTemplateType();
    }

    /**
     * 写入文件
     *
     * @param rootPath 输出根目录
     * @param filePath 文件路径
     * @param content 文件内容
     * @throws IOException IO异常
     * @since 1.2.0
     */
    private void writeFile(String rootPath, String filePath, String content) throws IOException {
        Path root = Paths.get(rootPath).toAbsolutePath().normalize();
        Path path = Paths.get(filePath).toAbsolutePath().normalize();
        if (!path.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板输出路径超出配置目录");
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            if (!parent.toRealPath().startsWith(root.toRealPath())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "模板输出路径包含非法符号链接");
            }
        }
        if (Files.exists(path) && Files.isSymbolicLink(path)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "禁止覆盖符号链接文件");
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * 清除模板缓存
     * 用于热重载或模板更新后清理缓存
     * @since 1.2.0
     */
    public void clearTemplateCache() {
        int size = templateCache.size();
        templateCache.clear();
        log.info("已清除模板缓存: {} 个模板", size);
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存大小
     * @since 1.2.0
     */
    public int getTemplateCacheSize() {
        return templateCache.size();
    }
}
