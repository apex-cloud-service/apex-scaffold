package com.apex.scaffold.generator;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * DDD 五层代码生成器引擎。
 *
 * <p>基于 FreeMarker 模板，按 {@link TableDefinition} 生成一套完整的
 * 通用 CRUD 代码：领域实体与仓储、持久化对象与 Mapper/转换器/仓储实现、
 * 应用服务与 DTO、接口层函数式 Handler，以及路由注册片段、数据库迁移
 * 片段与单元测试骨架。生成的代码遵循项目既有 Javadoc / Lombok / DDD 规范。</p>
 *
 * <h2>输出文件</h2>
 * <ul>
 *   <li>{@code domain/model/entity/{Entity}Entity.java}</li>
 *   <li>{@code domain/repository/{Entity}Repository.java}</li>
 *   <li>{@code infrastructure/persistence/po/{Entity}PO.java}</li>
 *   <li>{@code infrastructure/persistence/mapper/{Entity}Mapper.java}</li>
 *   <li>{@code infrastructure/persistence/converter/{Entity}PoConverter.java}</li>
 *   <li>{@code infrastructure/persistence/repository/{Entity}RepositoryImpl.java}</li>
 *   <li>{@code application/dto/{module}/{Entity}Request.java} / {@code {Entity}DTO.java}</li>
 *   <li>{@code application/service/{Entity}Service.java}</li>
 *   <li>{@code interfaces/handler/{module}/{Entity}Handler.java}</li>
 *   <li>路由注册片段、迁移 SQL 片段、Service 单元测试骨架</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public class CodeGenerator {

    /**
     * 生成代码的基础包名。
     */
    public static final String BASE_PACKAGE = "com.apex.scaffold";

    /**
     * 生成代码的作者标识。
     */
    public static final String AUTHOR = "Apex Team";

    private static final String JAVA_STRING_TYPE = "String";

    /**
     * FreeMarker 配置（类路径模板目录）。
     */
    private final Configuration freemarker;

    /**
     * 构造代码生成器。
     *
     * @throws IOException 当模板目录加载失败时抛出
     */
    public CodeGenerator() throws IOException {
        this.freemarker = new Configuration(Configuration.VERSION_2_3_33);
        this.freemarker.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        this.freemarker.setDefaultEncoding(StandardCharsets.UTF_8.name());
        this.freemarker.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        this.freemarker.setLogTemplateExceptions(false);
    }

    /**
     * 依据表定义生成全套代码。
     *
     * @param definition 表结构定义
     * @param outputRoot 输出根目录（包结构在此目录下创建）
     * @param migrationVersion 迁移脚本版本号（如 3）
     * @return 生成的文件相对路径列表
     * @throws IOException 当模板渲染或文件写入失败时抛出
     */
    public List<String> generate(TableDefinition definition, Path outputRoot, int migrationVersion) throws IOException {
        Map<String, Object> model = buildModel(definition, migrationVersion);
        List<GeneratedFile> files = collectFiles(definition, model);

        for (GeneratedFile file : files) {
            Path target = outputRoot.resolve(file.path()).normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, render(file.template(), model), StandardCharsets.UTF_8);
            log.info("生成: {}", file.path());
        }
        return files.stream().map(GeneratedFile::path).toList();
    }

    /**
     * 依据表定义生成配套 Vue3 管理前端代码（前后端分离工程 apex-admin-web）。
     *
     * <p>输出与 {@code apex-admin-web} 工程目录结构一致的页面，可直接消费：
     * <ul>
     *   <li>{@code src/api/{module}/{entityVar}.ts} - 类型化 API 封装（Result 解包）</li>
     *   <li>{@code src/views/{module}/{entityVar}/index.vue} - 列表页（搜索 + 表格 + 分页）</li>
     *   <li>{@code src/views/{module}/{entityVar}/form.vue} - 新增/编辑表单</li>
     *   <li>{@code generated/{entityName}RouteFragment.txt} - 动态路由注册说明（VIEW_REGISTRY + 菜单）</li>
     * </ul>
     * 代码风格与 apex-admin-web 的现有页面（如 system/user）保持一致。</p>
     *
     * @param definition 表结构定义
     * @param outputRoot 前端工程根目录（含 src/）
     * @return 生成的文件相对路径列表
     * @throws IOException 当模板渲染或文件写入失败时抛出
     */
    public List<String> generateFrontend(TableDefinition definition, Path outputRoot) throws IOException {
        Map<String, Object> model = buildModel(definition, 0);
        String entity = definition.getEntityName();
        String module = definition.getModule();
        String var = lowerFirst(entity);

        List<GeneratedFile> files = new ArrayList<>();
        files.add(new GeneratedFile("frontendApi.ftl", "src/api/" + module + "/" + var + ".ts"));
        files.add(new GeneratedFile("frontendList.ftl", "src/views/" + module + "/" + var + "/index.vue"));
        files.add(new GeneratedFile("frontendForm.ftl", "src/views/" + module + "/" + var + "/form.vue"));
        files.add(new GeneratedFile("frontendRouteFragment.ftl", "generated/" + entity + "RouteFragment.txt"));

        for (GeneratedFile file : files) {
            Path target = outputRoot.resolve(file.path()).normalize();
            Files.createDirectories(target.getParent());
            Files.writeString(target, render(file.template(), model), StandardCharsets.UTF_8);
            log.info("生成前端: {}", file.path());
        }
        return files.stream().map(GeneratedFile::path).toList();
    }

    /**
     * 依据数据库部署配置生成可合并的 YAML 配置片段。
     *
     * <p>将前端页面采集的数据库类型、主机、端口、库名、用户名等信息渲染为
     * dynamic-datasource 数据源、Flyway 迁移目录与 MyBatis-Plus 分页方言
     * 配置。密码统一使用环境变量占位符 {@code ${DB_MASTER_PASSWORD}}，
     * 不写入明文。</p>
     *
     * <p>首期支持 {@code mysql} 与 {@code postgresql}，其余类型抛出
     * {@link IllegalArgumentException}，避免生成不可用配置。</p>
     *
     * @param config 数据库部署配置（不含密码）
     * @param outputRoot 输出根目录
     * @return 生成的文件相对路径列表（仅 1 个：{@code generated/database-deployment.yml}）
     * @throws IOException 当模板渲染或文件写入失败时抛出
     * @throws IllegalArgumentException 当数据库类型不受支持时抛出
     */
    public List<String> generateDatabaseDeploymentConfig(DatabaseDeploymentConfig config, Path outputRoot)
            throws IOException {
        String dbType = normalizeDbType(config.getDbType());
        DatabaseProfile profile = DatabaseProfile.of(dbType);

        Map<String, Object> model = new HashMap<>(16);
        model.put("dbType", dbType);
        model.put("driverClassName", profile.driverClassName());
        model.put("jdbcUrl", profile.buildJdbcUrl(config));
        model.put("username", config.getUsername());
        model.put("validationQuery", profile.validationQuery());
        // Spring 环境变量占位符（不通过 FreeMarker 插值，避免嵌套 ${} 解析冲突）
        model.put("dbMasterPasswordPlaceholder", "${DB_MASTER_PASSWORD}");
        model.put("dbMaxActivePlaceholder", "${DB_MAX_ACTIVE:20}");

        String outputPath = "generated/database-deployment.yml";
        Path target = outputRoot.resolve(outputPath).normalize();
        Files.createDirectories(target.getParent());
        Files.writeString(target, render("databaseDeployment.ftl", model), StandardCharsets.UTF_8);
        log.info("生成数据库部署配置: {}", outputPath);
        return List.of(outputPath);
    }

    private static String normalizeDbType(String dbType) {
        if (dbType == null || dbType.isBlank()) {
            throw new IllegalArgumentException("数据库类型不能为空");
        }
        return dbType.trim().toLowerCase();
    }

    /**
     * 数据库方言概要（驱动、URL 前缀、校验查询、迁移目录）。
     *
     * @param dbType 规范化后的数据库类型
     * @param driverClassName JDBC 驱动类全限定名
     * @param urlPrefix JDBC URL 前缀（含 {@code //}）
     * @param validationQuery 连接池校验查询
     */
    private record DatabaseProfile(String dbType, String driverClassName, String urlPrefix, String validationQuery) {

        static DatabaseProfile of(String dbType) {
            return switch (dbType) {
                case "mysql" -> new DatabaseProfile("mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://", "SELECT 1");
                case "postgresql" -> new DatabaseProfile(
                        "postgresql", "org.postgresql.Driver", "jdbc:postgresql://", "SELECT 1");
                default -> throw new IllegalArgumentException("不支持的数据库类型: " + dbType);
            };
        }

        String buildJdbcUrl(DatabaseDeploymentConfig config) {
            StringBuilder url = new StringBuilder()
                    .append(urlPrefix)
                    .append(config.getHost())
                    .append(':')
                    .append(config.getPort())
                    .append('/')
                    .append(config.getDatabase());
            String params = config.getConnectionParams();
            if (params != null && !params.isBlank()) {
                url.append('?').append(params);
            }
            return url.toString();
        }
    }

    /**
     * 构造 FreeMarker 数据模型。
     *
     * @param definition 表结构定义
     * @param migrationVersion 迁移版本号
     * @return 数据模型
     */
    private Map<String, Object> buildModel(TableDefinition definition, int migrationVersion) {
        Map<String, Object> model = new HashMap<>(16);
        model.put("module", definition.getModule());
        model.put("entityName", definition.getEntityName());
        model.put("entityVar", lowerFirst(definition.getEntityName()));
        model.put("tableName", definition.getTableName());
        model.put("tableComment", definition.getTableComment());
        model.put("tenantAware", definition.isTenantAware());
        model.put("basePackage", BASE_PACKAGE);
        model.put("domainPackage", BASE_PACKAGE + ".domain");
        model.put("infraPackage", BASE_PACKAGE + ".infrastructure");
        model.put("appPackage", BASE_PACKAGE + ".application");
        model.put("ifacePackage", BASE_PACKAGE + ".interfaces");
        model.put("author", AUTHOR);
        model.put("migrationVersion", migrationVersion);

        StateMachineDefinition stateMachine = definition.getStateMachine();
        validateStateMachine(definition, stateMachine);
        boolean stateMachineEnabled = stateMachine != null;
        String stateField = stateMachineEnabled ? stateMachine.getField() : null;

        List<Map<String, Object>> fields = new ArrayList<>();
        List<Map<String, Object>> createFields = new ArrayList<>();
        List<Map<String, Object>> updateFields = new ArrayList<>();
        List<Map<String, Object>> formFields = new ArrayList<>();
        boolean hasBigDecimal = false;
        boolean hasLocalDateTime = false;
        for (FieldDefinition field : definition.getFields()) {
            Map<String, Object> item = new HashMap<>(16);
            item.put("name", field.getName());
            item.put("type", field.simpleType());
            item.put("comment", field.getComment());
            item.put("required", field.isRequired());
            item.put("columnName", field.columnName());
            item.put("sqlType", field.sqlType());
            // 前端 TypeScript 类型映射（供 Vue3 页面模板消费）
            item.put("tsType", toTsType(field.simpleType()));
            fields.add(item);
            boolean managedStateField = stateMachineEnabled && stateField.equals(field.getName());
            if (!managedStateField) {
                createFields.add(item);
                updateFields.add(item);
            }
            // 表单字段排除主键 id（主键由后端生成/列表行携带）
            if (!"id".equals(field.getName()) && !managedStateField) {
                formFields.add(item);
            }
            hasBigDecimal |= "BigDecimal".equals(field.simpleType());
            hasLocalDateTime |= "LocalDateTime".equals(field.simpleType());
        }
        model.put("fields", fields);
        model.put("createFields", createFields);
        model.put("updateFields", updateFields);
        model.put("formFields", formFields);
        model.put("stateMachineEnabled", stateMachineEnabled);
        model.put("stateField", stateField);
        model.put("initialState", stateMachineEnabled ? stateMachine.getInitialState() : null);
        model.put("stateTransitions", stateMachineEnabled ? stateMachine.getTransitions() : List.of());
        model.put("invalidState", stateMachineEnabled ? invalidState(stateMachine) : null);
        model.put("hasBigDecimal", hasBigDecimal);
        model.put("hasLocalDateTime", hasLocalDateTime);
        return model;
    }

    private static void validateStateMachine(TableDefinition definition, StateMachineDefinition stateMachine) {
        if (stateMachine == null) {
            return;
        }
        if (stateMachine.getField() == null || stateMachine.getField().isBlank()) {
            throw new IllegalArgumentException("状态机字段不能为空");
        }
        if (definition.getFields() == null) {
            throw new IllegalArgumentException("状态机字段列表不能为空");
        }
        FieldDefinition stateField = definition.getFields().stream()
                .filter(field -> stateMachine.getField().equals(field.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("状态机字段不存在: " + stateMachine.getField()));
        if (!JAVA_STRING_TYPE.equals(stateField.simpleType())) {
            throw new IllegalArgumentException("状态机字段必须为 String 类型");
        }
        if (stateMachine.getInitialState() == null
                || stateMachine.getInitialState().isBlank()) {
            throw new IllegalArgumentException("状态机初始状态不能为空");
        }
        if (stateMachine.getTransitions() == null
                || stateMachine.getTransitions().isEmpty()) {
            throw new IllegalArgumentException("状态迁移列表不能为空");
        }
        for (StateTransitionDefinition transition : stateMachine.getTransitions()) {
            if (transition == null) {
                throw new IllegalArgumentException("状态迁移不能为空");
            }
            if (transition.getFrom() == null
                    || transition.getFrom().isBlank()
                    || transition.getTo() == null
                    || transition.getTo().isBlank()) {
                throw new IllegalArgumentException("状态迁移端点不能为空");
            }
            if (transition.getFrom().equals(transition.getTo())) {
                throw new IllegalArgumentException("状态迁移不允许自循环");
            }
        }
        boolean initialStateInGraph = stateMachine.getTransitions().stream()
                .anyMatch(transition -> stateMachine.getInitialState().equals(transition.getFrom())
                        || stateMachine.getInitialState().equals(transition.getTo()));
        if (!initialStateInGraph) {
            throw new IllegalArgumentException("状态机初始状态必须存在于迁移图中: " + stateMachine.getInitialState());
        }
    }

    private static String invalidState(StateMachineDefinition stateMachine) {
        Set<String> states = stateMachine.getTransitions().stream()
                .flatMap(transition -> List.of(transition.getFrom(), transition.getTo()).stream())
                .collect(Collectors.toSet());
        String invalidState = "__INVALID_STATE__";
        while (states.contains(invalidState)) {
            invalidState = new StringBuilder("_").append(invalidState).toString();
        }
        return invalidState;
    }

    /**
     * Java 字段类型 → TypeScript 类型映射。
     *
     * <p>数值型映射为 {@code number}，布尔映射为 {@code boolean}，
     * 其余（字符串/时间/日期）统一映射为 {@code string}。</p>
     *
     * @param javaType Java 类型全名
     * @return TypeScript 类型
     */
    private static String toTsType(String javaType) {
        return switch (javaType) {
            case "Long",
                    "Integer",
                    "int",
                    "long",
                    "short",
                    "Short",
                    "Double",
                    "double",
                    "Float",
                    "float",
                    "BigDecimal" -> "number";
            case "Boolean", "boolean" -> "boolean";
            default -> "string";
        };
    }

    /**
     * 收集全部待生成文件（模板名 → 输出路径）。
     *
     * @param definition 表结构定义
     * @param model 数据模型
     * @return 待生成文件列表
     */
    private List<GeneratedFile> collectFiles(TableDefinition definition, Map<String, Object> model) {
        String entity = definition.getEntityName();
        String module = definition.getModule();
        String pkgPath = BASE_PACKAGE.replace('.', '/');

        List<GeneratedFile> files = new ArrayList<>();
        files.add(new GeneratedFile("entity.ftl", pkgPath + "/domain/model/entity/" + entity + "Entity.java"));
        files.add(new GeneratedFile("repository.ftl", pkgPath + "/domain/repository/" + entity + "Repository.java"));
        files.add(new GeneratedFile("po.ftl", pkgPath + "/infrastructure/persistence/po/" + entity + "PO.java"));
        files.add(new GeneratedFile(
                "mapper.ftl", pkgPath + "/infrastructure/persistence/mapper/" + entity + "Mapper.java"));
        files.add(new GeneratedFile(
                "converter.ftl", pkgPath + "/infrastructure/persistence/converter/" + entity + "PoConverter.java"));
        files.add(new GeneratedFile(
                "repositoryImpl.ftl",
                pkgPath + "/infrastructure/persistence/repository/" + entity + "RepositoryImpl.java"));
        files.add(new GeneratedFile(
                "dtoRequest.ftl", pkgPath + "/application/dto/" + module + "/" + entity + "Request.java"));
        files.add(new GeneratedFile(
                "dtoResponse.ftl", pkgPath + "/application/dto/" + module + "/" + entity + "DTO.java"));
        files.add(new GeneratedFile("service.ftl", pkgPath + "/application/service/" + entity + "Service.java"));
        files.add(new GeneratedFile(
                "handler.ftl", pkgPath + "/interfaces/handler/" + module + "/" + entity + "Handler.java"));
        files.add(new GeneratedFile("routerFragment.ftl", "generated/" + entity + "RouterFragment.txt"));
        files.add(new GeneratedFile(
                "migrationFragment.ftl",
                "generated/V" + model.get("migrationVersion") + "__create_" + definition.getTableName() + ".sql"));
        files.add(new GeneratedFile("serviceTest.ftl", "generated/" + entity + "ServiceTest.java"));
        return files;
    }

    /**
     * 渲染指定模板。
     *
     * @param templateName 模板文件名（classpath:templates 下）
     * @param model 数据模型
     * @return 渲染后的文本
     * @throws IOException 当模板加载或渲染失败时抛出
     */
    private String render(String templateName, Map<String, Object> model) throws IOException {
        try {
            Template template = freemarker.getTemplate(templateName);
            StringWriter writer = new StringWriter();
            template.process(model, writer);
            return writer.toString();
        } catch (freemarker.template.TemplateException e) {
            throw new IOException("模板渲染失败: " + templateName, e);
        }
    }

    /**
     * 首字母小写。
     *
     * @param value 输入字符串
     * @return 首字母小写后的字符串
     */
    private String lowerFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * 待生成文件记录。
     *
     * @param template 模板文件名
     * @param path 输出相对路径
     */
    private record GeneratedFile(String template, String path) {}
}
