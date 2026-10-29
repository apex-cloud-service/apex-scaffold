package com.apex.scaffold.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 代码生成器引擎单元测试。
 *
 * <p>使用临时目录生成一套示例代码，验证文件数量、包结构、关键类名
 * 与业务字段的渲染正确性。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class CodeGeneratorTest {

    @TempDir
    Path tempDir;

    /** 商品表定义 */
    private TableDefinition definition;

    /**
     * 初始化测试数据。
     */
    @BeforeEach
    void setUp() {
        definition = new TableDefinition();
        definition.setModule("demo");
        definition.setEntityName("Product");
        definition.setTableName("biz_product");
        definition.setTableComment("商品");

        FieldDefinition name = new FieldDefinition();
        name.setName("productName");
        name.setType("String");
        name.setComment("商品名称");
        name.setRequired(true);
        definition.getFields().add(name);

        FieldDefinition price = new FieldDefinition();
        price.setName("price");
        price.setType("BigDecimal");
        price.setComment("销售价格");
        definition.getFields().add(price);
    }

    /**
     * 测试生成文件数量与包结构。
     */
    @Test
    @DisplayName("generate - 生成 13 个文件且包结构正确")
    void generate_ProducesExpectedFiles() throws Exception {
        CodeGenerator generator = new CodeGenerator();
        List<String> files = generator.generate(definition, tempDir, 3);

        assertThat(files).hasSize(13);
        assertThat(files)
                .contains(
                        "com/apex/scaffold/domain/model/entity/ProductEntity.java",
                        "com/apex/scaffold/domain/repository/ProductRepository.java",
                        "com/apex/scaffold/infrastructure/persistence/po/ProductPO.java",
                        "com/apex/scaffold/infrastructure/persistence/mapper/ProductMapper.java",
                        "com/apex/scaffold/infrastructure/persistence/converter/ProductPoConverter.java",
                        "com/apex/scaffold/infrastructure/persistence/repository/ProductRepositoryImpl.java",
                        "com/apex/scaffold/application/dto/demo/ProductRequest.java",
                        "com/apex/scaffold/application/dto/demo/ProductDTO.java",
                        "com/apex/scaffold/application/service/ProductService.java",
                        "com/apex/scaffold/interfaces/handler/demo/ProductHandler.java");
    }

    /**
     * 测试领域实体渲染正确性。
     */
    @Test
    @DisplayName("entity - 实体包含类名、字段与工厂方法")
    void entity_RendersClassAndFields() throws Exception {
        CodeGenerator generator = new CodeGenerator();
        generator.generate(definition, tempDir, 3);

        String entity = Files.readString(tempDir.resolve("com/apex/scaffold/domain/model/entity/ProductEntity.java"));
        assertThat(entity)
                .contains("package com.apex.scaffold.domain.model.entity;")
                .contains("public class ProductEntity extends BaseEntity")
                .contains("private String productName;")
                .contains("private BigDecimal price;")
                .contains("public static ProductEntity create(")
                .contains("public void updateInfo(")
                .contains("@author Apex Team");
    }

    /**
     * 测试持久化对象渲染正确性。
     */
    @Test
    @DisplayName("po - 持久化对象包含表名注解与字段")
    void po_RendersTableAnnotation() throws Exception {
        CodeGenerator generator = new CodeGenerator();
        generator.generate(definition, tempDir, 3);

        String po = Files.readString(tempDir.resolve("com/apex/scaffold/infrastructure/persistence/po/ProductPO.java"));
        assertThat(po).contains("@TableName(\"biz_product\")").contains("private BigDecimal price;");
    }

    /**
     * 测试迁移片段渲染正确性。
     */
    @Test
    @DisplayName("migration - 迁移片段包含表名、列与版本号")
    void migration_RendersTableAndColumns() throws Exception {
        CodeGenerator generator = new CodeGenerator();
        generator.generate(definition, tempDir, 3);

        String migration = Files.readString(tempDir.resolve("generated/V3__create_biz_product.sql"));
        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS `biz_product`")
                .contains("`product_name` VARCHAR(255)")
                .contains("`price` DECIMAL(18,2)")
                .contains("V3: 创建 商品表");
    }

    @Test
    @DisplayName("tenantAware - 开启后实体、PO 与迁移统一生成租户字段")
    void tenantAware_RendersTenantIsolationContract() throws Exception {
        TableDefinition tenantDefinition = new ObjectMapper()
                .readValue(
                        """
                {
                  "module": "order",
                  "entityName": "Order",
                  "tableName": "biz_order",
                  "tableComment": "订单",
                  "tenantAware": true,
                  "fields": [
                    {"name": "orderName", "type": "String", "comment": "订单名称", "required": true}
                  ]
                }
                """,
                        TableDefinition.class);

        new CodeGenerator().generate(tenantDefinition, tempDir, 8);

        String entity = Files.readString(tempDir.resolve("com/apex/scaffold/domain/model/entity/OrderEntity.java"));
        String po = Files.readString(tempDir.resolve("com/apex/scaffold/infrastructure/persistence/po/OrderPO.java"));
        String migration = Files.readString(tempDir.resolve("generated/V8__create_biz_order.sql"));

        assertThat(entity)
                .contains("import lombok.Setter;")
                .contains("@Setter\n    private String tenantId;")
                .contains("@Setter\n    private String orderName;");
        assertThat(po).contains("@TableField(\"tenant_id\")").contains("private String tenantId;");
        assertThat(migration)
                .containsPattern("`tenant_id`\\s+VARCHAR\\(64\\)\\s+NOT NULL COMMENT '租户ID'")
                .contains("KEY `idx_biz_order_tenant_id` (`tenant_id`)");
    }

    @Test
    @DisplayName("stateMachine - 开启后生成实体流转、应用服务与测试骨架")
    void stateMachine_RendersEntityServiceAndTestContract() throws Exception {
        TableDefinition statefulDefinition = new ObjectMapper()
                .readValue(
                        """
                {
                  "module": "order",
                  "entityName": "Order",
                  "tableName": "biz_order",
                  "tableComment": "订单",
                  "stateMachine": {
                    "field": "status",
                    "initialState": "PENDING",
                    "transitions": [
                      {"from": "PENDING", "to": "PROCESSING"},
                      {"from": "PROCESSING", "to": "DONE"}
                    ]
                  },
                  "fields": [
                    {"name": "orderName", "type": "String", "comment": "订单名称", "required": true},
                    {"name": "status", "type": "String", "comment": "订单状态", "required": true}
                  ]
                }
                """,
                        TableDefinition.class);

        List<String> files = new CodeGenerator().generate(statefulDefinition, tempDir, 9);

        String entity = Files.readString(tempDir.resolve("com/apex/scaffold/domain/model/entity/OrderEntity.java"));
        String service = Files.readString(tempDir.resolve("com/apex/scaffold/application/service/OrderService.java"));
        String test = Files.readString(tempDir.resolve("generated/OrderServiceTest.java"));

        assertThat(files).hasSize(13);
        assertThat(entity)
                .contains("StateTransitionMachine<String>")
                .contains(".allow(\"PENDING\", \"PROCESSING\")")
                .contains(".allow(\"PROCESSING\", \"DONE\")")
                .contains("entity.status = \"PENDING\";")
                .contains("public void transitionTo(String targetState)")
                .contains("this.status = STATE_MACHINE.transition(this.status, targetState);")
                .doesNotContain("String orderName,\n            String status")
                .doesNotContain("this.status = status;");
        assertThat(service)
                .contains("OrderEntity.create(\n                            request.getOrderName()")
                .contains("existing.updateInfo(\n                            request.getOrderName()")
                .doesNotContain("request.getStatus()")
                .contains("public Mono<OrderEntity> transition(Long id, String targetState)")
                .contains("return get(id)")
                .contains("existing.transitionTo(targetState);")
                .contains("orderRepository.updateById(existing).map(rows -> existing)")
                .contains("流转订单状态成功");
        assertThat(test)
                .contains("OrderEntity.create(\n                orderName")
                .doesNotContain("String status = null", "orderName,\n                status")
                .contains("void transition_Success()")
                .contains("void transition_RejectsInvalidTargetState()")
                .contains("orderService.transition(1L, \"PROCESSING\")")
                .contains("assertThat(transitioned.getStatus())")
                .contains(".isEqualTo(\"PROCESSING\")")
                .contains("orderService.transition(1L, \"__INVALID_STATE__\")")
                .contains("expectError(BusinessException.class)")
                .contains("verify(orderRepository, never()).updateById(any(OrderEntity.class))");
    }

    @Test
    @DisplayName("stateMachine - 空迁移列表时抛出参数异常")
    void stateMachine_RejectsEmptyTransitions() throws Exception {
        TableDefinition statefulDefinition = statefulDefinition();
        statefulDefinition.getStateMachine().getTransitions().clear();

        assertThatThrownBy(() -> new CodeGenerator().generate(statefulDefinition, tempDir, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态迁移列表不能为空");
    }

    @Test
    @DisplayName("stateMachine - 初始状态不在迁移图中时抛出参数异常")
    void stateMachine_RejectsInitialStateOutsideTransitionGraph() throws Exception {
        TableDefinition statefulDefinition = statefulDefinition();
        statefulDefinition.getStateMachine().setInitialState("UNKNOWN");

        assertThatThrownBy(() -> new CodeGenerator().generate(statefulDefinition, tempDir, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态机初始状态必须存在于迁移图中: UNKNOWN");
    }

    @Test
    @DisplayName("stateMachine - fields 为 null 时抛出参数异常")
    void stateMachine_RejectsNullFields() throws Exception {
        TableDefinition statefulDefinition = statefulDefinition();
        statefulDefinition.setFields(null);

        assertThatThrownBy(() -> new CodeGenerator().generate(statefulDefinition, tempDir, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态机字段列表不能为空");
    }

    @Test
    @DisplayName("stateMachine - transitions 为 null 时抛出参数异常")
    void stateMachine_RejectsNullTransitions() throws Exception {
        TableDefinition statefulDefinition = statefulDefinition();
        statefulDefinition.getStateMachine().setTransitions(null);

        assertThatThrownBy(() -> new CodeGenerator().generate(statefulDefinition, tempDir, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态迁移列表不能为空");
    }

    @Test
    @DisplayName("stateMachine - transition 为 null 时抛出参数异常")
    void stateMachine_RejectsNullTransition() throws Exception {
        TableDefinition statefulDefinition = statefulDefinition();
        statefulDefinition.getStateMachine().getTransitions().add(null);

        assertThatThrownBy(() -> new CodeGenerator().generate(statefulDefinition, tempDir, 9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("状态迁移不能为空");
    }

    @Test
    @DisplayName("stateMachine - 未配置时保持原有实体与服务输出")
    void stateMachine_DefaultsToDisabled() throws Exception {
        new CodeGenerator().generate(definition, tempDir, 3);

        String entity = Files.readString(tempDir.resolve("com/apex/scaffold/domain/model/entity/ProductEntity.java"));
        String service = Files.readString(tempDir.resolve("com/apex/scaffold/application/service/ProductService.java"));
        String test = Files.readString(tempDir.resolve("generated/ProductServiceTest.java"));

        assertThat(entity).doesNotContain("StateTransitionMachine", "transitionTo");
        assertThat(service).doesNotContain("transition(Long id, String targetState)");
        assertThat(test).doesNotContain("transition_Success", "transition_RejectsInvalidTargetState");
    }

    @Test
    @DisplayName("tenantAware - 未开启时保持原有非租户输出")
    void tenantAware_DefaultsToDisabled() throws Exception {
        new CodeGenerator().generate(definition, tempDir, 3);

        String entity = Files.readString(tempDir.resolve("com/apex/scaffold/domain/model/entity/ProductEntity.java"));
        String po = Files.readString(tempDir.resolve("com/apex/scaffold/infrastructure/persistence/po/ProductPO.java"));
        String migration = Files.readString(tempDir.resolve("generated/V3__create_biz_product.sql"));

        assertThat(entity).doesNotContain("tenantId");
        assertThat(po).doesNotContain("tenantId", "tenant_id");
        assertThat(migration).doesNotContain("tenant_id");
    }

    /**
     * 测试路由片段渲染正确性。
     */
    @Test
    @DisplayName("router - 路由片段包含路径与权限点")
    void router_RendersPathAndPermission() throws Exception {
        CodeGenerator generator = new CodeGenerator();
        generator.generate(definition, tempDir, 3);

        String router = Files.readString(tempDir.resolve("generated/ProductRouterFragment.txt"));
        assertThat(router)
                .contains("/admin-api/v1/demo/products")
                .contains("demo:product:*")
                .contains("adminProductRoutes");
    }

    /**
     * 测试测试骨架渲染正确性。
     */
    @Test
    @DisplayName("serviceTest - 测试骨架包含服务测试结构")
    void serviceTest_RendersTestStructure() throws Exception {
        CodeGenerator generator = new CodeGenerator();
        generator.generate(definition, tempDir, 3);

        String test = Files.readString(tempDir.resolve("generated/ProductServiceTest.java"));
        assertThat(test)
                .contains("class ProductServiceTest")
                .contains("@ExtendWith(MockitoExtension.class)")
                .contains("private ProductService productService;");
    }

    /**
     * 测试前端代码生成（Vue3 页面 + API + 路由说明）。
     */
    @Test
    @DisplayName("generateFrontend - 生成 4 个前端文件且类型/路由正确")
    void generateFrontend_ProducesVue3Files() throws Exception {
        CodeGenerator generator = new CodeGenerator();
        List<String> files = generator.generateFrontend(definition, tempDir);

        assertThat(files).hasSize(4);
        assertThat(files)
                .contains(
                        "src/api/demo/product.ts",
                        "src/views/demo/product/index.vue",
                        "src/views/demo/product/form.vue",
                        "generated/ProductRouteFragment.txt");

        // API 模块：类型化接口 + 后端路径
        String api = Files.readString(tempDir.resolve("src/api/demo/product.ts"));
        assertThat(api)
                .contains("export interface ProductItem")
                .contains("productName?: string")
                .contains("price?: number")
                .contains("'/admin-api/v1/demo/products'");

        // 列表页：表格列 + 分页组件引用
        String listPage = Files.readString(tempDir.resolve("src/views/demo/product/index.vue"));
        assertThat(listPage)
                .contains("el-table")
                .contains("productName")
                .contains("Pagination")
                .contains("import " + lowerFirst("product") + "Form from './form.vue'");

        // 路由说明：注册表 + 权限码
        String route = Files.readString(tempDir.resolve("generated/ProductRouteFragment.txt"));
        assertThat(route)
                .contains("VIEW_REGISTRY")
                .contains("demo:product:list")
                .contains("'demo/product/index'");
    }

    @Test
    @DisplayName("generateDatabaseDeploymentConfig - MySQL 配置包含驱动、URL 前缀、Flyway 目录与分页方言")
    void generateDatabaseDeploymentConfig_MysqlRendersDatasourceAndFlyway() throws Exception {
        DatabaseDeploymentConfig config = new DatabaseDeploymentConfig();
        config.setDbType("mysql");
        config.setHost("127.0.0.1");
        config.setPort(3306);
        config.setDatabase("apex_data");
        config.setUsername("root");

        List<String> files = new CodeGenerator().generateDatabaseDeploymentConfig(config, tempDir);

        assertThat(files).containsExactly("generated/database-deployment.yml");
        String yaml = Files.readString(tempDir.resolve("generated/database-deployment.yml"));
        assertThat(yaml)
                .contains("driver-class-name: com.mysql.cj.jdbc.Driver")
                .contains("jdbc:mysql://127.0.0.1:3306/apex_data")
                .contains("db-type: mysql")
                .contains("classpath:db/migration/mysql")
                .contains("db-type: mysql")
                .contains("${DB_MASTER_PASSWORD}");
    }

    @Test
    @DisplayName("generateDatabaseDeploymentConfig - PostgreSQL 配置使用对应驱动、URL 前缀与 Flyway 目录")
    void generateDatabaseDeploymentConfig_PostgresRendersDatasourceAndFlyway() throws Exception {
        DatabaseDeploymentConfig config = new DatabaseDeploymentConfig();
        config.setDbType("postgresql");
        config.setHost("127.0.0.1");
        config.setPort(5432);
        config.setDatabase("apex_data");
        config.setUsername("apex");
        config.setSchema("public");

        List<String> files = new CodeGenerator().generateDatabaseDeploymentConfig(config, tempDir);

        assertThat(files).containsExactly("generated/database-deployment.yml");
        String yaml = Files.readString(tempDir.resolve("generated/database-deployment.yml"));
        assertThat(yaml)
                .contains("driver-class-name: org.postgresql.Driver")
                .contains("jdbc:postgresql://127.0.0.1:5432/apex_data")
                .contains("db-type: postgresql")
                .contains("classpath:db/migration/postgresql")
                .contains("${DB_MASTER_PASSWORD}");
    }

    @Test
    @DisplayName("generateDatabaseDeploymentConfig - 不支持的数据库类型抛出参数异常")
    void generateDatabaseDeploymentConfig_RejectsUnsupportedDbType() throws Exception {
        DatabaseDeploymentConfig config = new DatabaseDeploymentConfig();
        config.setDbType("oracle");
        config.setHost("127.0.0.1");
        config.setPort(1521);
        config.setDatabase("apex");
        config.setUsername("apex");

        assertThatThrownBy(() -> new CodeGenerator().generateDatabaseDeploymentConfig(config, tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的数据库类型");
    }

    private static TableDefinition statefulDefinition() throws Exception {
        return new ObjectMapper()
                .readValue(
                        """
                {
                  "module": "order",
                  "entityName": "Order",
                  "tableName": "biz_order",
                  "tableComment": "订单",
                  "stateMachine": {
                    "field": "status",
                    "initialState": "PENDING",
                    "transitions": [
                      {"from": "PENDING", "to": "PROCESSING"}
                    ]
                  },
                  "fields": [
                    {"name": "orderName", "type": "String", "comment": "订单名称", "required": true},
                    {"name": "status", "type": "String", "comment": "订单状态", "required": true}
                  ]
                }
                """,
                        TableDefinition.class);
    }

    /**
     * 首字母小写（与 CodeGenerator 语义一致，供本测试断言复用）。
     *
     * @param value 输入字符串
     * @return 首字母小写后的字符串
     */
    private static String lowerFirst(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
