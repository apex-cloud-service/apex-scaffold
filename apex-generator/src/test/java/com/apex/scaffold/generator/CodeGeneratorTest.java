package com.apex.scaffold.generator;

import static org.assertj.core.api.Assertions.assertThat;

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
}
