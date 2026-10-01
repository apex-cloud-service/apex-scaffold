package com.apex.scaffold.application.service;

import com.apex.scaffold.application.dto.admin.GeneratedFileDTO;
import com.apex.scaffold.application.dto.admin.TableInfoDTO;
import com.apex.scaffold.application.support.CodeTemplateEngine;
import com.apex.scaffold.application.support.DataSourcePoolManager;
import com.apex.scaffold.application.support.MetadataReader;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * 模板编译缓存
     * key: 模板内容的hash
     * value: 编译后的模板对象
     * @since 1.2.0
     */
    private final ConcurrentHashMap<String, Object> templateCache = new ConcurrentHashMap<>(256);

    /**
     * 获取数据源表列表
     *
     * @param datasourceId 数据源ID
     * @return 表名列表
     * @since 1.2.0
     */
    public Flux<String> getTables(Long datasourceId) {
        log.debug("获取数据源表列表: datasourceId={}", datasourceId);
        return Mono.error(new BusinessException(
                        ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenDatasourceEntity 和 GenDatasourceRepository 后完善此方法"))
                .flatMapMany(ds -> Flux.empty());
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
    @Cacheable(value = "table-metadata", key = "#datasourceId + ':' + #tableName")
    public Mono<TableInfoDTO> getTableColumns(Long datasourceId, String tableName) {
        log.debug("获取表列信息(缓存未命中): datasourceId={}, tableName={}", datasourceId, tableName);
        return Mono.<TableInfoDTO>error(new BusinessException(
                        ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenDatasourceEntity 和 GenDatasourceRepository 后完善此方法"))
                .cache(Duration.ofMinutes(10));
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
        return Mono.error(new BusinessException(
                ResultCode.ERROR, "TODO: 需要在 domain 层创建 GenConfigEntity、GenTemplateEntity 和相关 Repository 后完善此方法"));
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
            Object config, Object datasource, List<Object> templates, TableInfoDTO tableInfo) {
        return Mono.fromCallable(() -> {
                    List<GeneratedFileDTO> results = new ArrayList<>(16);

                    Map<String, Object> dataModel = buildDataModel(config, tableInfo);

                    for (Object template : templates) {
                        try {
                            // 使用缓存的模板编译结果
                            String content = renderTemplateWithCache(template, dataModel);
                            String outputFilePath = buildOutputFilePath(config, template, dataModel);

                            writeFile(outputFilePath, content);

                            GeneratedFileDTO fileDTO = GeneratedFileDTO.builder()
                                    .fileType(getTemplateType(template))
                                    .filePath(outputFilePath)
                                    .fileName(Paths.get(outputFilePath)
                                            .getFileName()
                                            .toString())
                                    .fileSize((long) content.getBytes().length)
                                    .success(true)
                                    .build();
                            results.add(fileDTO);

                            log.info("生成文件成功: {}", outputFilePath);
                        } catch (Exception e) {
                            log.error("生成文件失败: template={}", template, e);
                            GeneratedFileDTO fileDTO = GeneratedFileDTO.builder()
                                    .fileType(getTemplateType(template))
                                    .success(false)
                                    .errorMessage(e.getMessage())
                                    .build();
                            results.add(fileDTO);
                        }
                    }

                    return results;
                })
                .subscribeOn(BlockingIoSchedulers.storage());
    }

    /**
     * 构建数据模型
     *
     * @param config 生成配置
     * @param tableInfo 表信息
     * @return 数据模型
     * @since 1.2.0
     */
    private Map<String, Object> buildDataModel(Object config, TableInfoDTO tableInfo) {
        Map<String, Object> model = new HashMap<>(8);

        model.put("tableName", tableInfo.getTableName());
        model.put("tableComment", tableInfo.getTableComment());
        model.put("columns", tableInfo.getColumns());

        model.put("author", "Apex Team");
        model.put("datetime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        model.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        return model;
    }

    /**
     * 渲染模板(带缓存)
     * 使用ConcurrentHashMap缓存编译后的模板,避免重复编译
     *
     * @param template 模板对象
     * @param dataModel 数据模型
     * @return 渲染后的内容
     * @since 1.2.0
     */
    private String renderTemplateWithCache(Object template, Map<String, Object> dataModel) {
        String templateContent = getTemplateContent(template);
        String cacheKey = String.valueOf(templateContent.hashCode());

        Object compiledTemplate = templateCache.computeIfAbsent(cacheKey, key -> {
            log.debug("编译模板并缓存: key={}", key);
            return compileTemplate(templateContent);
        });

        return renderCompiledTemplate(compiledTemplate, dataModel);
    }

    /**
     * 获取模板内容
     * TODO: 实现从template对象获取内容的逻辑
     * @since 1.2.0
     */
    private String getTemplateContent(Object template) {
        return "";
    }

    /**
     * 编译模板
     * TODO: 使用实际的模板引擎编译
     * @since 1.2.0
     */
    private Object compileTemplate(String templateContent) {
        return templateContent;
    }

    /**
     * 渲染已编译的模板
     * TODO: 使用实际的模板引擎渲染
     * @since 1.2.0
     */
    private String renderCompiledTemplate(Object compiledTemplate, Map<String, Object> dataModel) {
        return compiledTemplate.toString();
    }

    /**
     * 旧的渲染方法(保留用于向后兼容)
     * @deprecated 使用 renderTemplateWithCache 替代
     * @since 1.2.0
     */
    @Deprecated
    private String renderTemplate(Object template, Map<String, Object> dataModel) {
        return "";
    }

    /**
     * 构建输出文件路径
     * TODO: 实现根据配置和模板生成输出路径
     * @since 1.2.0
     */
    private String buildOutputFilePath(Object config, Object template, Map<String, Object> dataModel) {
        return "";
    }

    /**
     * 获取模板类型
     * TODO: 实现从template对象获取类型的逻辑
     * @since 1.2.0
     */
    private String getTemplateType(Object template) {
        return "entity";
    }

    /**
     * 写入文件
     *
     * @param filePath 文件路径
     * @param content 文件内容
     * @throws IOException IO异常
     * @since 1.2.0
     */
    private void writeFile(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
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
