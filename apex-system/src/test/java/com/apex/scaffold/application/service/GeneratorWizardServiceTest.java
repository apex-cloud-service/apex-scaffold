package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.admin.ColumnInfoDTO;
import com.apex.scaffold.application.dto.admin.GenExecutionResponse;
import com.apex.scaffold.application.dto.admin.GenGenerateRequest;
import com.apex.scaffold.application.dto.admin.GenPreflightRequest;
import com.apex.scaffold.application.dto.admin.GenPreviewRequest;
import com.apex.scaffold.application.dto.admin.TableInfoDTO;
import com.apex.scaffold.application.support.CodeTemplateEngine;
import com.apex.scaffold.application.support.DataSourcePoolManager;
import com.apex.scaffold.application.support.MetadataReader;
import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.security.ConfigEncryptionService;
import com.apex.scaffold.domain.model.generator.DbType;
import com.apex.scaffold.domain.model.generator.GenArtifactEntity;
import com.apex.scaffold.domain.model.generator.GenConfigEntity;
import com.apex.scaffold.domain.model.generator.GenDatasourceEntity;
import com.apex.scaffold.domain.model.generator.GenHistoryEntity;
import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.domain.model.generator.GenTemplateEntity;
import com.apex.scaffold.domain.repository.GenArtifactRepository;
import com.apex.scaffold.domain.repository.GenConfigRepository;
import com.apex.scaffold.domain.repository.GenDatasourceRepository;
import com.apex.scaffold.domain.repository.GenHistoryRepository;
import com.apex.scaffold.domain.repository.GenTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 代码生成向导应用服务测试
 *
 * <p>覆盖模板目录、预检、纯渲染预览、执行令牌校验与产物下载的租户/状态边界，
 * 以及预览不写盘、路径越界被拒绝等安全约束。
 */
@ExtendWith(MockitoExtension.class)
class GeneratorWizardServiceTest {

    private static final String TENANT = "tenant-a";
    private static final String BUILTIN_TENANT = "system";
    private static final String TEMPLATE_PATH = "${tableName}.java";

    @Mock
    GenConfigRepository configRepository;

    @Mock
    GenDatasourceRepository datasourceRepository;

    @Mock
    GenTemplateRepository templateRepository;

    @Mock
    GenHistoryRepository historyRepository;

    @Mock
    GenArtifactRepository artifactRepository;

    @Mock
    DataSourcePoolManager dataSourcePoolManager;

    @Mock
    MetadataReader metadataReader;

    @Mock
    ConfigEncryptionService encryptionService;

    @Mock
    DataSource dataSource;

    @TempDir
    Path outputRoot;

    private final CodeTemplateEngine templateEngine = new CodeTemplateEngine();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong idSequence = new AtomicLong(100);

    private GeneratorWizardService service;

    @BeforeEach
    void setUp() {
        service = new GeneratorWizardService(
                configRepository,
                datasourceRepository,
                templateRepository,
                historyRepository,
                artifactRepository,
                dataSourcePoolManager,
                metadataReader,
                templateEngine,
                encryptionService,
                objectMapper,
                outputRoot.toString());
    }

    @Test
    void templatesHideForeignTenantAndFlagBuiltin() {
        GenTemplateEntity own = template(1L, TENANT, 0);
        GenTemplateEntity builtin = template(2L, BUILTIN_TENANT, 1);
        GenTemplateEntity foreign = template(3L, "tenant-b", 0);
        when(templateRepository.findAll()).thenReturn(Mono.just(List.of(own, builtin, foreign)));

        StepVerifier.create(service.templates().contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(catalog -> {
                    assertThat(catalog).hasSize(2);
                    assertThat(catalog.get(0).getTemplateId()).isEqualTo(1L);
                    assertThat(catalog.get(0).getReadOnly()).isFalse();
                    assertThat(catalog.get(1).getTemplateId()).isEqualTo(2L);
                    assertThat(catalog.get(1).getBuiltin()).isTrue();
                    assertThat(catalog.get(1).getReadOnly()).isTrue();
                    assertThat(catalog.get(1).getEngine()).isEqualTo(GeneratorWizardService.ENGINE);
                    assertThat(catalog.get(1).getContentSha256()).hasSize(16);
                })
                .verifyComplete();
    }

    @Test
    void preflightRejectsUnsupportedDbType() {
        GenConfigEntity config = config(10L, TENANT);
        GenDatasourceEntity datasource = datasource(20L, TENANT, DbType.ORACLE);
        lenient().when(configRepository.findById(10L)).thenReturn(Mono.just(config));
        lenient().when(datasourceRepository.findById(20L)).thenReturn(Mono.just(datasource));
        lenient().when(templateRepository.findById(1L)).thenReturn(Mono.just(template(1L, TENANT, 0)));

        StepVerifier.create(service.preflight(preflightRequest(10L)).contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(response -> {
                    assertThat(response.getReady()).isFalse();
                    assertThat(response.getArtifactCount()).isZero();
                    assertThat(response.getIssues())
                            .anySatisfy(issue -> assertThat(issue.getCode()).isEqualTo("DATASOURCE_TYPE_UNSUPPORTED"));
                    assertThat(response.getOutputRootAlias()).isEqualTo(GeneratorWizardService.OUTPUT_ROOT_ALIAS);
                })
                .verifyComplete();
    }

    @Test
    void preflightReportsMissingTemplateWithoutError() {
        GenConfigEntity config = config(10L, TENANT);
        config.setTemplateIds("[99]");
        lenient().when(configRepository.findById(10L)).thenReturn(Mono.just(config));
        lenient().when(datasourceRepository.findById(20L)).thenReturn(Mono.just(datasource(20L, TENANT, DbType.MYSQL)));
        lenient().when(templateRepository.findById(99L)).thenReturn(Mono.empty());
        lenient().when(encryptionService.decrypt(any())).thenReturn("secret");
        lenient()
                .when(dataSourcePoolManager.getOrCreateDataSource(any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(dataSource));
        lenient()
                .when(metadataReader.getTableColumns(any(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.preflight(preflightRequest(10L)).contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(response -> {
                    assertThat(response.getReady()).isFalse();
                    assertThat(response.getTemplateCount()).isZero();
                    assertThat(response.getIssues())
                            .anySatisfy(issue -> assertThat(issue.getCode()).isEqualTo("TEMPLATE_NOT_CONFIGURED"));
                })
                .verifyComplete();
    }

    @Test
    void previewRendersWithoutWritingFiles() {
        String tableName = "sys_user";
        stubRenderable(tableName);

        StepVerifier.create(service.preview(previewRequest(10L)).contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(response -> {
                    assertThat(response.getReady()).isTrue();
                    assertThat(response.getPreviewToken())
                            .isNotBlank()
                            .isEqualTo(response.getInputDigest())
                            .hasSize(16);
                    assertThat(response.getFiles()).singleElement().satisfies(file -> {
                        assertThat(file.getTableName()).isEqualTo(tableName);
                        assertThat(file.getRelativePath()).isEqualTo(tableName + ".java");
                        assertThat(file.getContentPreview()).contains(tableName);
                        assertThat(file.getTruncated()).isFalse();
                    });
                })
                .verifyComplete();
        assertThat(outputRoot.toFile().listFiles()).isNullOrEmpty();
    }

    @Test
    void executeRejectsStalePreviewToken() {
        String tableName = "sys_user";
        stubRenderable(tableName);
        GenGenerateRequest request = GenGenerateRequest.builder()
                .configId(10L)
                .previewToken("stale-token")
                .build();

        StepVerifier.create(service.execute(request, 1L).contextWrite(TenantContext.withTenant(TENANT)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
        assertThat(outputRoot.toFile().listFiles()).isNullOrEmpty();
    }

    @Test
    void executePublishesArtifactsUnderTenantTaskDirectory() throws Exception {
        String tableName = "sys_user";
        stubRenderable(tableName);
        when(historyRepository.insert(any(GenHistoryEntity.class))).thenAnswer(invocation -> {
            GenHistoryEntity history = invocation.getArgument(0);
            history.setId(500L);
            return Mono.just(history);
        });
        when(artifactRepository.insertBatch(any())).thenAnswer(invocation -> {
            List<GenArtifactEntity> artifacts = invocation.getArgument(0);
            artifacts.forEach(artifact -> artifact.setId(idSequence.incrementAndGet()));
            return Mono.just(artifacts);
        });

        String token = service.preview(previewRequest(10L))
                .contextWrite(TenantContext.withTenant(TENANT))
                .block()
                .getPreviewToken();
        GenGenerateRequest request =
                GenGenerateRequest.builder().configId(10L).previewToken(token).build();

        StepVerifier.create(service.execute(request, 7L).contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(response -> {
                    assertThat(response.getStatus()).isEqualTo(GeneratorWizardService.STATUS_PUBLISHED);
                    assertThat(response.getHistoryId()).isEqualTo(500L);
                    assertThat(response.getArtifacts()).singleElement().satisfies(artifact -> {
                        assertThat(artifact.getStatus()).isEqualTo(GeneratorWizardService.STATUS_PUBLISHED);
                        assertThat(artifact.getFileSize()).isPositive();
                        assertThat(artifact.getId()).isNotNull();
                    });
                    assertThat(response.getTaskDirectory())
                            .startsWith(TENANT + "/")
                            .doesNotContain(outputRoot.toString());
                    Path written =
                            outputRoot.resolve(response.getTaskDirectory()).resolve(tableName + ".java");
                    assertThat(written).exists();
                    assertThat(readQuietly(written)).contains(tableName);
                })
                .verifyComplete();
    }

    @Test
    void executeRepeatedRunUsesSeparateTaskDirectoryAndKeepsPreviousArtifacts() throws Exception {
        String tableName = "sys_user";
        stubRenderable(tableName);
        when(historyRepository.insert(any(GenHistoryEntity.class))).thenAnswer(invocation -> {
            GenHistoryEntity history = invocation.getArgument(0);
            history.setId(600L);
            return Mono.just(history);
        });
        when(artifactRepository.insertBatch(any())).thenAnswer(invocation -> {
            List<GenArtifactEntity> artifacts = invocation.getArgument(0, List.class);
            artifacts.forEach(artifact -> artifact.setId(idSequence.incrementAndGet()));
            return Mono.just(artifacts);
        });

        GenGenerateRequest request = GenGenerateRequest.builder()
                .configId(10L)
                .previewToken(service.preview(previewRequest(10L))
                        .contextWrite(TenantContext.withTenant(TENANT))
                        .block()
                        .getPreviewToken())
                .build();

        GenExecutionResponse first = service.execute(request, 7L)
                .contextWrite(TenantContext.withTenant(TENANT))
                .block();
        assertThat(first).isNotNull();
        Path firstFile = outputRoot.resolve(first.getTaskDirectory()).resolve(tableName + ".java");
        assertThat(firstFile).exists();
        String firstContent = readQuietly(firstFile);

        GenExecutionResponse second = service.execute(request, 7L)
                .contextWrite(TenantContext.withTenant(TENANT))
                .block();
        assertThat(second).isNotNull();
        assertThat(second.getTaskId()).isNotEqualTo(first.getTaskId());
        assertThat(second.getTaskDirectory()).isNotEqualTo(first.getTaskDirectory());
        assertThat(readQuietly(firstFile)).isEqualTo(firstContent);
        assertThat(outputRoot.resolve(second.getTaskDirectory()).resolve(tableName + ".java"))
                .exists();
    }

    @Test
    void downloadRejectsForeignTenantArtifact() {
        GenArtifactEntity artifact = publishedArtifact(300L, "tenant-b");
        when(artifactRepository.findById(300L)).thenReturn(Mono.just(artifact));

        StepVerifier.create(service.download(300L).contextWrite(TenantContext.withTenant(TENANT)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode());
                })
                .verify();
    }

    @Test
    void downloadRejectsUnpublishedArtifact() {
        GenArtifactEntity artifact = publishedArtifact(301L, TENANT);
        artifact.setStatus(GeneratorWizardService.STATUS_FAILED);
        when(artifactRepository.findById(301L)).thenReturn(Mono.just(artifact));

        StepVerifier.create(service.download(301L).contextWrite(TenantContext.withTenant(TENANT)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
                })
                .verify();
    }

    @Test
    void downloadRejectsPathEscapingTaskDirectory() throws Exception {
        GenArtifactEntity artifact = publishedArtifact(302L, TENANT);
        artifact.setRelativePath("../escape.java");
        when(artifactRepository.findById(302L)).thenReturn(Mono.just(artifact));
        Path taskDirectory = outputRoot.resolve(TENANT).resolve(String.valueOf(artifact.getTaskId()));
        Files.createDirectories(taskDirectory);
        Files.writeString(outputRoot.resolve("escape.java"), "leaked", StandardCharsets.UTF_8);

        StepVerifier.create(service.download(302L).contextWrite(TenantContext.withTenant(TENANT)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode());
                })
                .verify();
    }

    @Test
    void downloadReadsPublishedArtifactContent() throws Exception {
        GenArtifactEntity artifact = publishedArtifact(303L, TENANT);
        artifact.setRelativePath("sys_user.java");
        artifact.setFileName("sys_user.java");
        when(artifactRepository.findById(303L)).thenReturn(Mono.just(artifact));
        Path taskDirectory = outputRoot.resolve(TENANT).resolve(String.valueOf(artifact.getTaskId()));
        Files.createDirectories(taskDirectory);
        Files.writeString(taskDirectory.resolve("sys_user.java"), "class SysUser {}", StandardCharsets.UTF_8);

        StepVerifier.create(service.download(303L).contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(file -> {
                    assertThat(file.fileName()).isEqualTo("sys_user.java");
                    assertThat(file.contentType()).startsWith("text/plain");
                    assertThat(new String(file.content(), StandardCharsets.UTF_8))
                            .contains("class SysUser");
                })
                .verifyComplete();
    }

    @Test
    void everyCapabilityRequiresTenantContext() {
        StepVerifier.create(service.templates())
                .expectErrorSatisfies(error ->
                        assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()))
                .verify();
    }

    @Test
    void runTaskRejectsDriftedPreviewTokenBeforeWriting() {
        stubRenderable("sys_user");
        GenTaskEntity task = asyncTask(700L, "stale-token", "[]");

        StepVerifier.create(service.runTask(task, 7L, TENANT, proceedListener(), leaseGuard(true))
                        .contextWrite(TenantContext.withTenant(TENANT)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
        assertThat(outputRoot.toFile().listFiles()).isNullOrEmpty();
        verify(historyRepository, never()).insert(any(GenHistoryEntity.class));
    }

    @Test
    void runTaskStopsBeforeWritingWhenLeaseLost() {
        stubRenderable("sys_user");
        GenTaskEntity task = asyncTask(701L, previewToken(), "[]");

        StepVerifier.create(service.runTask(task, 7L, TENANT, proceedListener(), leaseGuard(false))
                        .contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(outcome -> {
                    assertThat(outcome.stopped()).isTrue();
                    assertThat(outcome.historyId()).isNull();
                })
                .verifyComplete();
        assertThat(outputRoot.toFile().listFiles()).isNullOrEmpty();
        verify(historyRepository, never()).insert(any(GenHistoryEntity.class));
        verify(artifactRepository, never()).insertBatch(any());
    }

    @Test
    void runTaskSkipsHistoryWhenLeaseTakenOverBeforeFinish() throws Exception {
        stubRenderable("sys_user");
        GenTaskEntity task = asyncTask(702L, previewToken(), "[]");
        AtomicInteger guardCalls = new AtomicInteger();
        GeneratorWizardService.LeaseGuard takeoverAtFinish = () -> Mono.just(guardCalls.incrementAndGet() < 3);

        StepVerifier.create(service.runTask(task, 7L, TENANT, proceedListener(), takeoverAtFinish)
                        .contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(outcome -> {
                    assertThat(outcome.historyId()).isNull();
                    assertThat(outcome.stopped()).isTrue();
                    assertThat(outcome.processedCount()).isEqualTo(1);
                })
                .verifyComplete();
        assertThat(outputRoot.resolve(TENANT).resolve("702").resolve("sys_user.java"))
                .exists();
        verify(historyRepository, never()).insert(any(GenHistoryEntity.class));
        verify(artifactRepository, never()).insertBatch(any());
    }

    @Test
    void runTaskRecountsTotalAndPublishesUnderTenantTaskDirectory() throws Exception {
        stubRenderable("sys_user");
        when(historyRepository.insert(any(GenHistoryEntity.class))).thenAnswer(invocation -> {
            GenHistoryEntity history = invocation.getArgument(0);
            history.setId(520L);
            return Mono.just(history);
        });
        when(artifactRepository.insertBatch(any())).thenAnswer(invocation -> {
            List<GenArtifactEntity> artifacts = invocation.getArgument(0);
            artifacts.forEach(artifact -> artifact.setId(idSequence.incrementAndGet()));
            return Mono.just(artifacts);
        });
        GenTaskEntity task = asyncTask(703L, previewToken(), "[]");
        task.setTotalCount(99);

        StepVerifier.create(service.runTask(task, 7L, TENANT, proceedListener(), leaseGuard(true))
                        .contextWrite(TenantContext.withTenant(TENANT)))
                .assertNext(outcome -> {
                    assertThat(outcome.historyId()).isEqualTo(520L);
                    assertThat(outcome.processedCount()).isEqualTo(1);
                    assertThat(outcome.stopped()).isFalse();
                })
                .verifyComplete();
        assertThat(task.getTotalCount()).isEqualTo(1);
        assertThat(outputRoot.resolve(TENANT).resolve("703").resolve("sys_user.java"))
                .exists();
    }

    @Test
    void runTaskFailsFastWhenFieldMappingsCorrupted() {
        stubRenderable("sys_user");
        GenTaskEntity task = asyncTask(704L, previewToken(), "not-json");

        StepVerifier.create(service.runTask(task, 7L, TENANT, proceedListener(), leaseGuard(true))
                        .contextWrite(TenantContext.withTenant(TENANT)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
                })
                .verify();
        assertThat(outputRoot.toFile().listFiles()).isNullOrEmpty();
    }

    @Test
    void parseFieldMappingsTreatsBlankAsEmptyAndRejectsCorruptedJson() {
        assertThat(service.parseFieldMappings(null)).isEmpty();
        assertThat(service.parseFieldMappings("  ")).isEmpty();
        assertThat(service.parseFieldMappings("[]")).isEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.parseFieldMappings("not-json"))
                .isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------ 夹具

    /**
     * 通过真实预览链获取绑定当前输入摘要的预览令牌。
     *
     * @return 16 位摘要令牌
     */
    private String previewToken() {
        return service.preview(previewRequest(10L))
                .contextWrite(TenantContext.withTenant(TENANT))
                .block()
                .getPreviewToken();
    }

    /**
     * 构建异步任务夹具。
     *
     * @param id          任务 ID
     * @param token       预览令牌
     * @param mappingsJson 持久化字段映射 JSON
     * @return 已认领状态的任务实体
     */
    private GenTaskEntity asyncTask(long id, String token, String mappingsJson) {
        GenTaskEntity task = new GenTaskEntity();
        task.setId(id);
        task.setConfigId(10L);
        task.setStatus("RUNNING");
        task.setPreviewToken(token);
        task.setFieldMappingsJson(mappingsJson);
        task.setOverwriteFlag(0);
        task.setOperatorId(7L);
        task.setAttempts(1);
        task.setTenantId(TENANT);
        task.setLockedBy("generator-task-test");
        return task;
    }

    /**
     * 恒定放行的进度监听器。
     *
     * @return 始终继续执行的监听器
     */
    private GeneratorWizardService.TaskProgressListener proceedListener() {
        return (processedCount, failedCount, errorMessage) -> Mono.just(true);
    }

    /**
     * 恒定返回指定结果的租约守卫。
     *
     * @param owned 是否持有租约
     * @return 租约守卫
     */
    private GeneratorWizardService.LeaseGuard leaseGuard(boolean owned) {
        return () -> Mono.just(owned);
    }

    private void stubRenderable(String tableName) {
        lenient().when(configRepository.findById(10L)).thenReturn(Mono.just(config(10L, TENANT)));
        lenient().when(datasourceRepository.findById(20L)).thenReturn(Mono.just(datasource(20L, TENANT, DbType.MYSQL)));
        lenient().when(templateRepository.findById(1L)).thenReturn(Mono.just(template(1L, TENANT, 0)));
        lenient().when(encryptionService.decrypt(any())).thenReturn("secret");
        lenient()
                .when(dataSourcePoolManager.getOrCreateDataSource(any(), anyLong(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(dataSource));
        lenient()
                .when(metadataReader.getTableColumns(any(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String requested = invocation.getArgument(2);
                    if (!tableName.equals(requested)) {
                        return Mono.empty();
                    }
                    return Mono.just(TableInfoDTO.builder()
                            .tableName(tableName)
                            .tableComment("用户表")
                            .tableType("TABLE")
                            .columns(List.of(ColumnInfoDTO.builder()
                                    .columnName("id")
                                    .dataType("BIGINT")
                                    .javaType("Long")
                                    .javaFieldName("id")
                                    .isPrimaryKey(true)
                                    .isNullable(false)
                                    .build()))
                            .build());
                });
    }

    private GenConfigEntity config(Long id, String tenant) {
        GenConfigEntity config = new GenConfigEntity();
        config.setId(id);
        config.setTenantId(tenant);
        config.setDatasourceId(20L);
        config.setTableNames("[\"sys_user\"]");
        config.setTemplateIds("[1]");
        config.setPackageName("com.apex.demo");
        config.setModuleName("demo");
        config.setAuthor("tester");
        config.setUpdateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        config.setLockVersion(0);
        return config;
    }

    private GenDatasourceEntity datasource(Long id, String tenant, DbType dbType) {
        GenDatasourceEntity datasource = new GenDatasourceEntity();
        datasource.setId(id);
        datasource.setTenantId(tenant);
        datasource.setDbType(dbType);
        datasource.setParams("jdbc:mysql://127.0.0.1:3306/demo");
        datasource.setUsername("root");
        datasource.setPassword("cipher");
        return datasource;
    }

    private GenTemplateEntity template(Long id, String tenant, int builtin) {
        GenTemplateEntity template = new GenTemplateEntity();
        template.setId(id);
        template.setTenantId(tenant);
        template.setIsBuiltin(builtin);
        template.setTemplateName("entity-" + id);
        template.setTemplateType("ENTITY");
        template.setFileSuffix(".java");
        template.setTemplatePath(TEMPLATE_PATH);
        template.setTemplateContent("public class ${tableName} { }");
        template.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        return template;
    }

    private GenPreflightRequest preflightRequest(Long configId) {
        return GenPreflightRequest.builder().configId(configId).build();
    }

    private GenPreviewRequest previewRequest(Long configId) {
        return GenPreviewRequest.builder().configId(configId).build();
    }

    private GenArtifactEntity publishedArtifact(Long id, String tenant) {
        GenArtifactEntity artifact = new GenArtifactEntity();
        artifact.setId(id);
        artifact.setTenantId(tenant);
        artifact.setTaskId(900L);
        artifact.setConfigId(10L);
        artifact.setTableName("sys_user");
        artifact.setStatus(GeneratorWizardService.STATUS_PUBLISHED);
        return artifact;
    }

    private String readQuietly(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception error) {
            return "";
        }
    }
}
