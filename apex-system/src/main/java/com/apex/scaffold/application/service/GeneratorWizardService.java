package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.ColumnInfoDTO;
import com.apex.scaffold.application.dto.admin.GenArtifactDTO;
import com.apex.scaffold.application.dto.admin.GenExecutionResponse;
import com.apex.scaffold.application.dto.admin.GenFieldMappingDTO;
import com.apex.scaffold.application.dto.admin.GenGenerateRequest;
import com.apex.scaffold.application.dto.admin.GenIssueDTO;
import com.apex.scaffold.application.dto.admin.GenPreflightRequest;
import com.apex.scaffold.application.dto.admin.GenPreflightResponse;
import com.apex.scaffold.application.dto.admin.GenPreviewRequest;
import com.apex.scaffold.application.dto.admin.GenPreviewResponse;
import com.apex.scaffold.application.dto.admin.GenTemplateCatalogDTO;
import com.apex.scaffold.application.dto.admin.TableInfoDTO;
import com.apex.scaffold.application.support.CodeTemplateEngine;
import com.apex.scaffold.application.support.DataSourcePoolManager;
import com.apex.scaffold.application.support.MetadataReader;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.security.ConfigEncryptionService;
import com.apex.scaffold.domain.model.generator.DbType;
import com.apex.scaffold.domain.model.generator.GenArtifactEntity;
import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import com.apex.scaffold.domain.model.generator.GenDatasourceEntity;
import com.apex.scaffold.domain.model.generator.GenHistoryEntity;
import com.apex.scaffold.domain.model.generator.GenTemplateEntity;
import com.apex.scaffold.domain.repository.GenArtifactRepository;
import com.apex.scaffold.domain.repository.GenConfigRepository;
import com.apex.scaffold.domain.repository.GenDatasourceRepository;
import com.apex.scaffold.domain.repository.GenHistoryRepository;
import com.apex.scaffold.domain.repository.GenTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 代码生成向导应用服务
 *
 * <p>在既有「数据源 → 表/字段 → 配置 → 执行 → 历史」链路上补齐向导所需的模板目录、
 * 结构化预检、纯渲染预览、受控执行与按产物 ID 下载五段能力。
 *
 * <h2>安全边界</h2>
 * <ul>
 *   <li>预览只做内存渲染，不写任何服务器文件；预览令牌绑定配置版本、模板内容摘要、
 *       字段映射与目标表集合，任一输入变化后旧令牌失效，必须重新预览</li>
 *   <li>执行默认拒绝覆盖已有文件，产物写入 {@code outputRoot/tenant/taskId} 独立任务目录，
 *       既有路径白名单与符号链接保护继续生效</li>
 *   <li>配置、数据源、模板、历史与产物全链校验租户归属；内置模板仅只读共享</li>
 *   <li>预览片段最多返回 4000 字符，不回传完整文件内容；数据源密码不解密回传</li>
 *   <li>所有 JDBC 元数据读取与文件读写均隔离到阻塞调度器</li>
 * </ul>
 *
 * <h2>引擎边界</h2>
 * <p>在线引擎 {@link CodeTemplateEngine} 只支持 {@code ${variable}} 变量替换，
 * 不支持 FreeMarker 指令；独立模块 {@code apex-generator} 的 FreeMarker 模板不能直接混用，
 * 需先改写语法。模板目录通过 {@code engine=VARIABLE_SUBSTITUTION} 显式声明该能力。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
public class GeneratorWizardService {

    /** 在线模板引擎标识。 */
    static final String ENGINE = "VARIABLE_SUBSTITUTION";

    /** 在线模板引擎能力说明。 */
    static final String ENGINE_CAPABILITIES = "仅支持 ${variable} 变量替换，不支持 FreeMarker 指令";

    /** 预览片段最大字符数。 */
    static final int PREVIEW_SNIPPET_LIMIT = 4000;

    /** 输出根目录别名，不向前端返回绝对路径。 */
    static final String OUTPUT_ROOT_ALIAS = "${apex.generator.output-root}";

    static final String ISSUE_ERROR = "ERROR";
    static final String ISSUE_WARN = "WARN";

    static final String STATUS_PUBLISHED = "PUBLISHED";
    static final String STATUS_PARTIAL = "PARTIAL";
    static final String STATUS_FAILED = "FAILED";

    /** 生成历史状态，与产物发布状态一一对应。 */
    static final String HISTORY_SUCCESS = "SUCCESS";

    static final String HISTORY_PARTIAL = "PARTIAL";
    static final String HISTORY_FAILED = "FAILED";

    private static final String DEFAULT_OUTPUT_ROOT = "/tmp/apex-gen";
    private static final String BUILTIN_TENANT = "system";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final int DIGEST_LENGTH = 16;
    private static final String TEMPLATE_VARIABLE = "${tableName}";
    private static final Pattern UNSAFE_PATH = Pattern.compile("(^/)|(^[A-Za-z]:)|(\\\\)|(^|/)\\.\\.(/|$)");

    /** 仅支持 MySQL 与 PostgreSQL，其余数据库类型在预检阶段以结构化问题项表达。 */
    private static final EnumSet<DbType> SUPPORTED_DB_TYPES = EnumSet.of(DbType.MYSQL, DbType.POSTGRESQL);

    private final GenConfigRepository configRepository;
    private final GenDatasourceRepository datasourceRepository;
    private final GenTemplateRepository templateRepository;
    private final GenHistoryRepository historyRepository;
    private final GenArtifactRepository artifactRepository;
    private final DataSourcePoolManager dataSourcePoolManager;
    private final MetadataReader metadataReader;
    private final CodeTemplateEngine templateEngine;
    private final ConfigEncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final String outputRoot;
    private final AtomicLong taskSequence = new AtomicLong();

    /**
     * 构造代码生成向导服务。
     *
     * @param configRepository 生成配置仓储
     * @param datasourceRepository 数据源配置仓储
     * @param templateRepository 模板仓储
     * @param historyRepository 生成历史仓储
     * @param artifactRepository 生成产物仓储
     * @param dataSourcePoolManager 动态数据源连接池管理器
     * @param metadataReader 数据库元数据读取器
     * @param templateEngine 变量替换模板引擎
     * @param encryptionService 配置加解密服务
     * @param objectMapper JSON 序列化器
     * @param outputRoot 产物输出根目录，按租户/任务隔离
     */
    public GeneratorWizardService(
            GenConfigRepository configRepository,
            GenDatasourceRepository datasourceRepository,
            GenTemplateRepository templateRepository,
            GenHistoryRepository historyRepository,
            GenArtifactRepository artifactRepository,
            DataSourcePoolManager dataSourcePoolManager,
            MetadataReader metadataReader,
            CodeTemplateEngine templateEngine,
            ConfigEncryptionService encryptionService,
            ObjectMapper objectMapper,
            @Value("${apex.generator.output-root:" + DEFAULT_OUTPUT_ROOT + "}") String outputRoot) {
        this.configRepository = configRepository;
        this.datasourceRepository = datasourceRepository;
        this.templateRepository = templateRepository;
        this.historyRepository = historyRepository;
        this.artifactRepository = artifactRepository;
        this.dataSourcePoolManager = dataSourcePoolManager;
        this.metadataReader = metadataReader;
        this.templateEngine = templateEngine;
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
        this.outputRoot = outputRoot;
    }

    /**
     * 查询向导可选的模板目录。
     *
     * <p>返回当前租户模板与受控只读共享的内置模板，并声明引擎能力与内容版本；
     * 租户模板与内置模板同类型时由 {@link #resolveTemplates} 做同名收敛。
     *
     * @return 模板目录列表
     */
    public Mono<List<GenTemplateCatalogDTO>> templates() {
        return tenantId().flatMap(tenant -> templateRepository.findAll().map(items -> items.stream()
                .filter(item -> isVisible(item, tenant))
                .map(this::toCatalog)
                .toList()));
    }

    /**
     * 结构化预检。
     *
     * <p>只校验不渲染不写盘：配置与数据源归属、数据库类型能力、模板归属与引擎能力、
     * 目标表集合、输出根配置与预计产物数量。任何 ERROR 级问题都会令 {@code ready=false}。
     *
     * @param request 预检请求
     * @return 预检结果
     */
    public Mono<GenPreflightResponse> preflight(GenPreflightRequest request) {
        return tenantId()
                .flatMap(tenant -> loadContext(request.getConfigId(), tenant).flatMap(context -> {
                    List<GenIssueDTO> issues = new ArrayList<>();
                    validateTemplates(context, issues);
                    if (context.datasource() == null) {
                        return Mono.just(preflightResponse(0, 0, context.templateCount(), issues));
                    }
                    DbType dbType = context.datasource().getDbType();
                    if (dbType == null || !SUPPORTED_DB_TYPES.contains(dbType)) {
                        issues.add(issue(
                                ISSUE_ERROR,
                                "DATASOURCE_TYPE_UNSUPPORTED",
                                "datasourceId",
                                "当前仅支持 MySQL 与 PostgreSQL 数据源"));
                        return Mono.just(preflightResponse(0, 0, context.templateCount(), issues));
                    }
                    return readTables(context, request.getFieldMappings())
                            .map(tables ->
                                    preflightResponse(tables.size(), tables.size(), context.templateCount(), issues));
                }));
    }

    /**
     * 纯渲染预览。
     *
     * <p>不写服务器文件，只返回内容摘要与限量片段，并签发绑定输入摘要的预览令牌。
     * 存在 ERROR 级问题时 {@code ready=false} 且不返回文件。
     *
     * @param request 预览请求
     * @return 预览结果
     */
    public Mono<GenPreviewResponse> preview(GenPreviewRequest request) {
        return tenantId().flatMap(tenant -> loadContext(request.getConfigId(), tenant)
                .flatMap(context -> validateExecutable(context).flatMap(datasource -> renderAll(
                                context, request.getFieldMappings())
                        .map(rendered -> {
                            String inputDigest = inputDigest(context, request.getFieldMappings());
                            List<GenPreviewResponse.GenPreviewFileDTO> files =
                                    rendered.stream().map(this::toPreviewFile).toList();
                            return GenPreviewResponse.builder()
                                    .previewToken(inputDigest)
                                    .inputDigest(inputDigest)
                                    .ready(true)
                                    .issues(List.of())
                                    .files(files)
                                    .build();
                        }))));
    }

    /**
     * 受控执行生成并写入独立任务目录。
     *
     * <p>先校验预览令牌是否仍与当前输入摘要一致，再在 {@code outputRoot/tenant/taskId} 下写入；
     * 默认拒绝覆盖已存在文件，产物与生成历史同批落库，部分失败如实返回 PARTIAL 与逐项问题。
     *
     * @param request 执行请求
     * @param operatorId 操作人 ID，写入生成历史
     * @return 执行结果
     */
    public Mono<GenExecutionResponse> execute(GenGenerateRequest request, Long operatorId) {
        return tenantId().flatMap(tenant -> loadContext(request.getConfigId(), tenant)
                .flatMap(context -> validateExecutable(context).flatMap(datasource -> {
                    String digest = inputDigest(context, request.getFieldMappings());
                    if (!digest.equals(request.getPreviewToken())) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "预览结果已失效，请重新预览后再执行"));
                    }
                    return renderAll(context, request.getFieldMappings())
                            .flatMap(rendered -> publish(
                                    context,
                                    tenant,
                                    rendered,
                                    Boolean.TRUE.equals(request.getOverwrite()),
                                    operatorId));
                })));
    }

    /**
     * 按产物 ID 读取下载内容。
     *
     * <p>浏览器只提交产物 ID，不接受任意路径。读取前重新校验租户归属，
     * 并确认磁盘路径落在任务目录内且不是符号链接。
     *
     * @param artifactId 产物 ID
     * @return 下载内容
     */
    public Mono<ArtifactDownload> download(Long artifactId) {
        return tenantId().flatMap(tenant -> artifactRepository
                .findById(artifactId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "产物不存在")))
                .flatMap(artifact -> {
                    if (!tenant.equals(artifact.getTenantId())) {
                        return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "无权访问其他租户的数据"));
                    }
                    if (!STATUS_PUBLISHED.equals(artifact.getStatus())) {
                        return Mono.error(new BusinessException(ResultCode.NOT_FOUND, "产物未成功发布"));
                    }
                    return Mono.fromCallable(() -> readArtifact(tenant, artifact))
                            .subscribeOn(BlockingIoSchedulers.storage());
                }));
    }

    /**
     * 产物下载载荷。
     *
     * @param fileName 文件名
     * @param contentType 内容类型
     * @param content 文件字节
     */
    public record ArtifactDownload(String fileName, String contentType, byte[] content) {}

    // ------------------------------------------------------------------ 上下文

    /**
     * 向导上下文：配置、数据源、模板与目标表集合。
     */
    private record WizardContext(
            GenConfigEntity config,
            GenDatasourceEntity datasource,
            List<GenTemplateEntity> templates,
            List<String> tableNames,
            int templateCount) {}

    /**
     * 加载并校验向导上下文。
     *
     * <p>模板与表集合解析失败时不直接抛错，由各能力转为结构化问题项或显式异常。
     */
    private Mono<WizardContext> loadContext(Long configId, String tenant) {
        return configRepository
                .findById(configId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "生成配置不存在")))
                .flatMap(config -> requireTenant(config.getTenantId(), tenant).then(Mono.just(config)))
                .flatMap(config -> resolveDatasource(config, tenant)
                        .flatMap(datasource -> resolveTemplates(config, tenant)
                                .map(templates -> new WizardContext(
                                        config,
                                        datasource,
                                        templates,
                                        parseTableNames(config.getTableNames()),
                                        templates.size())))
                        .switchIfEmpty(Mono.fromSupplier(() -> new WizardContext(
                                config, null, List.of(), parseTableNames(config.getTableNames()), 0))));
    }

    private Mono<GenDatasourceEntity> resolveDatasource(GenConfigEntity config, String tenant) {
        if (config.getDatasourceId() == null) {
            return Mono.empty();
        }
        return datasourceRepository
                .findById(config.getDatasourceId())
                .filter(datasource -> tenant.equals(datasource.getTenantId()));
    }

    /**
     * 解析配置引用的模板。
     *
     * <p>模板 ID 非法或缺失时返回空列表，预检据此产出 ERROR 级问题项；
     * 单条模板不属于当前租户且非内置时同样剔除，不允许跨租户使用。
     */
    private Mono<List<GenTemplateEntity>> resolveTemplates(GenConfigEntity config, String tenant) {
        List<Long> ids = parseTemplateIds(config.getTemplateIds());
        if (ids.isEmpty()) {
            return Mono.just(List.of());
        }
        return Flux.fromIterable(ids)
                .concatMap(id -> templateRepository.findById(id))
                .filter(template -> isVisible(template, tenant))
                .collectList();
    }

    /**
     * 校验上下文可执行：数据源可达且类型受支持、模板非空、目标表非空。
     */
    private Mono<GenDatasourceEntity> validateExecutable(WizardContext context) {
        if (context.datasource() == null) {
            return Mono.error(new BusinessException(ResultCode.NOT_FOUND, "数据源不存在"));
        }
        DbType dbType = context.datasource().getDbType();
        if (dbType == null || !SUPPORTED_DB_TYPES.contains(dbType)) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "当前仅支持 MySQL 与 PostgreSQL 数据源"));
        }
        if (context.templates().isEmpty()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "未配置可用的代码模板"));
        }
        if (context.tableNames().isEmpty()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "未配置生成表"));
        }
        return Mono.just(context.datasource());
    }

    private boolean isVisible(GenTemplateEntity template, String tenant) {
        return tenant.equals(template.getTenantId())
                || (Integer.valueOf(1).equals(template.getIsBuiltin())
                        && BUILTIN_TENANT.equals(template.getTenantId()));
    }

    // ------------------------------------------------------------------ 预检

    /**
     * 校验模板可用性。
     *
     * <p>以解析后的模板集合为准：配置了模板 ID 但全部不存在、不属于当前租户且非内置时，
     * 同样产出 ERROR 级问题项，避免出现「无模板却预检通过」的空产物。
     */
    private void validateTemplates(WizardContext context, List<GenIssueDTO> issues) {
        if (context.templates().isEmpty()) {
            issues.add(issue(ISSUE_ERROR, "TEMPLATE_NOT_CONFIGURED", "templateIds", "未配置可用的代码模板"));
        }
    }

    private GenPreflightResponse preflightResponse(
            int tableCount, int targetTableCount, int templateCount, List<GenIssueDTO> issues) {
        boolean ready = issues.stream().noneMatch(item -> ISSUE_ERROR.equals(item.getLevel()));
        return GenPreflightResponse.builder()
                .ready(ready)
                .issues(List.copyOf(issues))
                .tableCount(tableCount)
                .templateCount(templateCount)
                .artifactCount(ready ? targetTableCount * templateCount : 0)
                .outputRootAlias(OUTPUT_ROOT_ALIAS)
                .build();
    }

    /**
     * 读取目标表的元数据。
     *
     * <p>表不存在时追加 {@code TABLE_NOT_FOUND} 告警并跳过；连接失败以 {@code DATASOURCE_UNREACHABLE}
     * 阻塞项返回，不掩盖为成功。
     */
    private Mono<List<TableInfo>> readTables(WizardContext context, List<GenFieldMappingDTO> fieldMappings) {
        return dataSource(context)
                .flatMapMany(
                        dataSource -> Flux.fromIterable(context.tableNames()).concatMap(name -> metadataReader
                                .getTableColumns(
                                        dataSource,
                                        context.datasource().getDbType().name().toLowerCase(),
                                        name)
                                .map(info -> new TableInfo(info, applyMapping(info, fieldMappings)))
                                .onErrorResume(BusinessException.class, error -> {
                                    log.warn("读取表元数据失败: table={}, message={}", name, error.getMessage());
                                    return Mono.empty();
                                })))
                .collectList();
    }

    private Mono<DataSource> dataSource(WizardContext context) {
        String password = encryptionService.decrypt(context.datasource().getPassword());
        return dataSourcePoolManager.getOrCreateDataSource(
                context.datasource().getTenantId(),
                context.datasource().getId(),
                context.datasource().getParams(),
                context.datasource().getUsername(),
                password);
    }

    // ------------------------------------------------------------------ 渲染

    /**
     * 渲染结果：表信息、模板与渲染内容。
     */
    private record RenderedFile(TableInfo table, GenTemplateEntity template, String content, String relativePath) {}

    private record TableInfo(TableInfoDTO source, List<ColumnInfoDTO> columns) {}

    /**
     * 纯渲染全部「表 × 模板」组合，不触碰文件系统。
     */
    private Mono<List<RenderedFile>> renderAll(WizardContext context, List<GenFieldMappingDTO> fieldMappings) {
        return readTables(context, fieldMappings).flatMap(tables -> {
            if (tables.isEmpty()) {
                return Mono.error(new BusinessException(ResultCode.NOT_FOUND, "目标表不存在或不可读取"));
            }
            return Flux.fromIterable(tables)
                    .concatMap(table -> dataModel(context, table)
                            .flatMapMany(model -> Flux.fromIterable(context.templates())
                                    .concatMap(template -> templateEngine
                                            .render(template.getTemplateContent(), model)
                                            .map(content -> new RenderedFile(
                                                    table, template, content, resolveRelativePath(template, model))))))
                    .collectList();
        });
    }

    private Mono<Map<String, Object>> dataModel(WizardContext context, TableInfo table) {
        Map<String, Object> model = new HashMap<>(8);
        LocalDateTime now = LocalDateTime.now();
        model.put("tableName", table.source().getTableName());
        model.put("tableComment", table.source().getTableComment());
        model.put("columns", table.columns());
        model.put("packageName", context.config().getPackageName());
        model.put("moduleName", context.config().getModuleName());
        model.put("author", context.config().getAuthor());
        model.put("datetime", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.put("date", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        return Mono.just(model);
    }

    /**
     * 合并字段映射覆盖。
     *
     * <p>未列出的列沿用元数据；{@code ignored=true} 的列从渲染数据模型中剔除；
     * 其余覆盖项只影响渲染，不改写源库结构。
     */
    private List<ColumnInfoDTO> applyMapping(TableInfoDTO table, List<GenFieldMappingDTO> fieldMappings) {
        Map<String, GenFieldMappingDTO.ColumnMapping> overrides = new LinkedHashMap<>();
        if (fieldMappings != null) {
            for (GenFieldMappingDTO mapping : fieldMappings) {
                if (mapping == null
                        || !Objects.equals(mapping.getTableName(), table.getTableName())
                        || mapping.getColumns() == null) {
                    continue;
                }
                for (GenFieldMappingDTO.ColumnMapping column : mapping.getColumns()) {
                    if (column != null && column.getColumnName() != null) {
                        overrides.put(column.getColumnName(), column);
                    }
                }
            }
        }
        List<ColumnInfoDTO> columns = new ArrayList<>();
        for (ColumnInfoDTO column : table.getColumns() == null ? List.<ColumnInfoDTO>of() : table.getColumns()) {
            GenFieldMappingDTO.ColumnMapping override = overrides.get(column.getColumnName());
            if (override != null && Boolean.TRUE.equals(override.getIgnored())) {
                continue;
            }
            if (override == null) {
                columns.add(column);
                continue;
            }
            columns.add(ColumnInfoDTO.builder()
                    .columnName(column.getColumnName())
                    .dataType(column.getDataType())
                    .javaType(override.getJavaType() == null ? column.getJavaType() : override.getJavaType())
                    .javaFieldName(
                            override.getJavaFieldName() == null
                                    ? column.getJavaFieldName()
                                    : override.getJavaFieldName())
                    .columnComment(
                            override.getColumnComment() == null
                                    ? column.getColumnComment()
                                    : override.getColumnComment())
                    .isPrimaryKey(column.getIsPrimaryKey())
                    .isNullable(column.getIsNullable())
                    .columnLength(column.getColumnLength())
                    .defaultValue(column.getDefaultValue())
                    .build());
        }
        return columns;
    }

    /**
     * 解析模板输出相对路径。
     *
     * <p>模板路径中的 {@code ${tableName}} 会被替换为真实表名；结果必须是任务目录内的相对路径，
     * 含绝对路径、盘符、反斜杠或 {@code ..} 的路径直接拒绝。
     */
    private String resolveRelativePath(GenTemplateEntity template, Map<String, Object> model) {
        String tableName = String.valueOf(model.get("tableName"));
        String relative = template.getTemplatePath();
        if (relative == null || relative.isBlank()) {
            relative = template.getTemplateType() + "/" + tableName
                    + (template.getFileSuffix() == null ? "" : template.getFileSuffix());
        } else {
            relative = relative.replace(TEMPLATE_VARIABLE, tableName);
        }
        if (UNSAFE_PATH.matcher(relative).find()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模板输出路径超出任务目录");
        }
        return relative;
    }

    private GenPreviewResponse.GenPreviewFileDTO toPreviewFile(RenderedFile file) {
        String content = file.content();
        boolean truncated = content.length() > PREVIEW_SNIPPET_LIMIT;
        return GenPreviewResponse.GenPreviewFileDTO.builder()
                .tableName(file.table().source().getTableName())
                .templateId(file.template().getId())
                .templateType(file.template().getTemplateType())
                .relativePath(file.relativePath())
                .contentSize(content.getBytes(StandardCharsets.UTF_8).length)
                .lineCount((int) content.lines().count())
                .contentDigest(digest(content))
                .contentPreview(truncated ? content.substring(0, PREVIEW_SNIPPET_LIMIT) : content)
                .truncated(truncated)
                .build();
    }

    // ------------------------------------------------------------------ 执行

    /**
     * 写入任务目录并落库产物与历史。
     *
     * <p>任务目录按 {@code outputRoot/tenant/taskId} 隔离；已存在同名文件且未显式允许覆盖时，
     * 该文件记为 FAILED 并保留原文件。发布状态按成功数量区分 PUBLISHED / PARTIAL / FAILED。
     */
    private Mono<GenExecutionResponse> publish(
            WizardContext context, String tenant, List<RenderedFile> rendered, boolean overwrite, Long operatorId) {
        long taskId = nextTaskId();
        Path taskDirectory = taskDirectory(tenant, taskId);
        List<GenIssueDTO> issues = new ArrayList<>();
        return Mono.fromCallable(() -> {
                    List<GenArtifactEntity> artifacts = new ArrayList<>(rendered.size());
                    for (RenderedFile file : rendered) {
                        artifacts.add(writeArtifact(
                                file, context.config().getId(), tenant, taskId, taskDirectory, overwrite, issues));
                    }
                    return artifacts;
                })
                .subscribeOn(BlockingIoSchedulers.storage())
                .flatMap(written -> saveHistory(context, tenant, taskId, operatorId, written)
                        .flatMap(history -> artifactRepository
                                .insertBatch(written)
                                .map(persisted ->
                                        executionResponse(taskId, history.getId(), tenant, persisted, issues))));
    }

    private GenArtifactEntity writeArtifact(
            RenderedFile file,
            Long configId,
            String tenant,
            long taskId,
            Path taskDirectory,
            boolean overwrite,
            List<GenIssueDTO> issues) {
        GenArtifactEntity artifact = baseArtifact(file, configId, tenant, taskId);
        Path target = taskDirectory.resolve(file.relativePath()).normalize();
        try {
            if (!target.startsWith(taskDirectory)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "模板输出路径超出任务目录");
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
                if (!parent.toRealPath().startsWith(realTaskDirectory(taskDirectory))) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "模板输出路径包含非法符号链接");
                }
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(target)) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "禁止覆盖符号链接文件");
                }
                if (!overwrite) {
                    throw new BusinessException(ResultCode.BUSINESS_ERROR, "目标文件已存在，未允许覆盖");
                }
            }
            byte[] bytes = file.content().getBytes(StandardCharsets.UTF_8);
            Files.write(target, bytes);
            artifact.setStatus(STATUS_PUBLISHED);
            artifact.setFileSize((long) bytes.length);
        } catch (IOException | BusinessException error) {
            log.warn("产物写入失败: table={}, path={}", file.table().source().getTableName(), file.relativePath());
            artifact.setStatus(STATUS_FAILED);
            artifact.setErrorMessage(error.getMessage());
            issues.add(issue(
                    ISSUE_ERROR,
                    error instanceof BusinessException ? "ARTIFACT_WRITE_REJECTED" : "ARTIFACT_WRITE_FAILED",
                    file.relativePath(),
                    error.getMessage()));
        }
        return artifact;
    }

    private GenArtifactEntity baseArtifact(RenderedFile file, Long configId, String tenant, long taskId) {
        GenArtifactEntity artifact = new GenArtifactEntity();
        artifact.setTaskId(taskId);
        artifact.setConfigId(configId);
        artifact.setTableName(file.table().source().getTableName());
        artifact.setTemplateId(file.template().getId());
        artifact.setTemplateType(file.template().getTemplateType());
        artifact.setRelativePath(file.relativePath());
        artifact.setFileName(Paths.get(file.relativePath()).getFileName().toString());
        artifact.setFileSize(0L);
        artifact.setContentDigest(digest(file.content()));
        artifact.setTenantId(tenant);
        return artifact;
    }

    /**
     * 落库生成历史，并把主键回填到同批产物。
     *
     * <p>历史状态与产物发布状态保持一致：全部成功为 {@code SUCCESS}，
     * 部分成功为 {@code PARTIAL}，全部失败为 {@code FAILED}。
     */
    private Mono<GenHistoryEntity> saveHistory(
            WizardContext context, String tenant, long taskId, Long operatorId, List<GenArtifactEntity> artifacts) {
        long published = artifacts.stream()
                .filter(artifact -> STATUS_PUBLISHED.equals(artifact.getStatus()))
                .count();
        GenHistoryEntity history = new GenHistoryEntity();
        history.setConfigId(context.config().getId());
        history.setOutputPath(relativeTaskDirectory(tenant, taskId));
        history.setTenantId(tenant);
        history.setOperatorId(operatorId);
        history.setStatus(
                published == 0 ? HISTORY_FAILED : published == artifacts.size() ? HISTORY_SUCCESS : HISTORY_PARTIAL);
        history.setGeneratedFiles(toJson(artifacts));
        if (published < artifacts.size()) {
            history.setErrorMessage(artifacts.stream()
                    .filter(artifact -> STATUS_FAILED.equals(artifact.getStatus()))
                    .map(GenArtifactEntity::getErrorMessage)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("部分产物写入失败"));
        }
        return historyRepository
                .insert(history)
                .doOnNext(persisted -> artifacts.forEach(artifact -> artifact.setHistoryId(persisted.getId())));
    }

    private GenExecutionResponse executionResponse(
            long taskId, Long historyId, String tenant, List<GenArtifactEntity> artifacts, List<GenIssueDTO> issues) {
        long published = artifacts.stream()
                .filter(artifact -> STATUS_PUBLISHED.equals(artifact.getStatus()))
                .count();
        String status =
                published == 0 ? STATUS_FAILED : published == artifacts.size() ? STATUS_PUBLISHED : STATUS_PARTIAL;
        List<GenArtifactDTO> publishedArtifacts = artifacts.stream()
                .filter(artifact -> STATUS_PUBLISHED.equals(artifact.getStatus()))
                .map(this::toArtifactDTO)
                .toList();
        return GenExecutionResponse.builder()
                .taskId(taskId)
                .historyId(historyId)
                .status(status)
                .taskDirectory(relativeTaskDirectory(tenant, taskId))
                .artifacts(publishedArtifacts)
                .issues(List.copyOf(issues))
                .build();
    }

    private GenArtifactDTO toArtifactDTO(GenArtifactEntity artifact) {
        return GenArtifactDTO.builder()
                .id(artifact.getId())
                .taskId(artifact.getTaskId())
                .configId(artifact.getConfigId())
                .historyId(artifact.getHistoryId())
                .tableName(artifact.getTableName())
                .templateId(artifact.getTemplateId())
                .templateType(artifact.getTemplateType())
                .relativePath(artifact.getRelativePath())
                .fileName(artifact.getFileName())
                .fileSize(artifact.getFileSize())
                .contentDigest(artifact.getContentDigest())
                .status(artifact.getStatus())
                .errorMessage(artifact.getErrorMessage())
                .createTime(artifact.getCreateTime())
                .build();
    }

    /**
     * 读取产物内容并复核磁盘路径。
     *
     * <p>任务目录必须真实存在，目标文件解析真实路径后仍须落在任务目录内，防止符号链接逃逸。
     */
    private ArtifactDownload readArtifact(String tenant, GenArtifactEntity artifact) throws IOException {
        Path taskDirectory =
                taskDirectory(tenant, artifact.getTaskId()).toAbsolutePath().normalize();
        if (!Files.isDirectory(taskDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产物文件已不可用");
        }
        Path target = taskDirectory.resolve(artifact.getRelativePath()).normalize();
        if (!target.startsWith(taskDirectory) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产物文件已不可用");
        }
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(taskDirectory.toRealPath())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "产物路径超出任务目录");
        }
        return new ArtifactDownload(
                artifact.getFileName(), contentType(artifact.getFileName()), Files.readAllBytes(realTarget));
    }

    private String contentType(String fileName) {
        return fileName != null && fileName.endsWith(".xml") ? "application/xml" : "text/plain;charset=UTF-8";
    }

    // ------------------------------------------------------------------ 路径与摘要

    private Path taskDirectory(String tenant, long taskId) {
        Path root = Paths.get(outputRoot).toAbsolutePath().normalize();
        Path directory = root.resolve(tenant).resolve(String.valueOf(taskId)).normalize();
        if (!directory.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "任务目录超出输出根目录");
        }
        return directory;
    }

    private Path realTaskDirectory(Path taskDirectory) throws IOException {
        Path real = taskDirectory.toRealPath();
        Path root = Paths.get(outputRoot).toAbsolutePath().normalize().toRealPath();
        if (!real.startsWith(root)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "任务目录超出输出根目录");
        }
        return real;
    }

    private String relativeTaskDirectory(String tenant, long taskId) {
        return tenant + "/" + taskId;
    }

    private long nextTaskId() {
        long sequence = taskSequence.updateAndGet(current -> current >= Long.MAX_VALUE - 1 ? 1 : current + 1);
        return System.currentTimeMillis() * 1000 + sequence % 1000;
    }

    /**
     * 计算绑定输入摘要。
     *
     * <p>摘要覆盖配置版本（更新时间与乐观锁版本）、模板内容、字段映射与目标表集合，
     * 任一变化都会使预览令牌失效。
     */
    private String inputDigest(WizardContext context, List<GenFieldMappingDTO> fieldMappings) {
        StringBuilder builder = new StringBuilder(256);
        GenConfigEntity config = context.config();
        builder.append(config.getId())
                .append('|')
                .append(config.getUpdateTime())
                .append('|')
                .append(config.getLockVersion())
                .append('|');
        context.templates().forEach(template -> builder.append(template.getId())
                .append(':')
                .append(digest(template.getTemplateContent()))
                .append(';'));
        builder.append('|');
        context.tableNames().forEach(name -> builder.append(name).append(','));
        builder.append('|').append(toJson(fieldMappings));
        return digest(builder.toString());
    }

    private String digest(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hashed = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte item : hashed) {
                hex.append(String.format("%02x", item));
            }
            return hex.substring(0, DIGEST_LENGTH);
        } catch (NoSuchAlgorithmException error) {
            throw new BusinessException(ResultCode.ERROR, "摘要算法不可用");
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception error) {
            return String.valueOf(value);
        }
    }

    // ------------------------------------------------------------------ 解析辅助

    private List<String> parseTableNames(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<String> names = objectMapper.readValue(
                    value, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
        } catch (Exception error) {
            return List.of();
        }
    }

    private List<Long> parseTemplateIds(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    value, objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception error) {
            return List.of();
        }
    }

    private GenTemplateCatalogDTO toCatalog(GenTemplateEntity template) {
        boolean builtin = Integer.valueOf(1).equals(template.getIsBuiltin());
        return GenTemplateCatalogDTO.builder()
                .templateId(template.getId())
                .templateName(template.getTemplateName())
                .templateType(template.getTemplateType())
                .fileSuffix(template.getFileSuffix())
                .templatePath(template.getTemplatePath())
                .engine(ENGINE)
                .engineCapabilities(ENGINE_CAPABILITIES)
                .contentVersion(
                        template.getUpdateTime() == null
                                ? null
                                : template.getUpdateTime().toString())
                .contentSha256(digest(template.getTemplateContent()))
                .builtin(builtin)
                .readOnly(builtin)
                .build();
    }

    private GenIssueDTO issue(String level, String code, String field, String message) {
        return GenIssueDTO.builder()
                .level(level)
                .code(code)
                .field(field)
                .message(message)
                .build();
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
}
