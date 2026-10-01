package com.apex.scaffold.interfaces.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * OpenAPI 文档导出测试。
 *
 * <p>基于 {@link SpringDocConfig}、{@link RouterOpenApiConfig} 和 {@link OpenApiDocumentationCustomizer}
 * 构建与运行时一致的完整 OpenAPI 文档，并导出为 JSON 和 YAML 文件，供版本归档和追溯使用。
 *
 * <p>此测试不依赖数据库或外部服务，仅通过反射读取注解声明并应用定制器，确保导出结果稳定可复现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
class OpenApiExportTest {

    /** 导出目录（相对于项目根目录）。 */
    private static final String EXPORT_DIR = "target/openapi-export";

    /**
     * 导出完整 OpenAPI 文档为 JSON 和 YAML 文件。
     *
     * @throws Exception 当导出过程失败时
     */
    @Test
    void exportOpenApiSpec() throws Exception {
        OpenAPI openApi = buildOpenApi();

        Path dir = Paths.get(EXPORT_DIR);
        Files.createDirectories(dir);

        // JSON 格式
        ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path jsonFile = dir.resolve("openapi.json");
        Files.writeString(jsonFile, jsonMapper.writeValueAsString(openApi));

        // YAML 格式（使用 Jackson YAML 模块）
        Path yamlFile = dir.resolve("openapi.yaml");
        try {
            ObjectMapper yamlMapper =
                    new com.fasterxml.jackson.dataformat.yaml.YAMLMapper().enable(SerializationFeature.INDENT_OUTPUT);
            Files.writeString(yamlFile, yamlMapper.writeValueAsString(openApi));
        } catch (NoClassDefFoundError ignored) {
            // YAML 模块不可用时跳过 YAML 导出
        }

        System.out.println("OpenAPI 文档已导出到: " + dir.toAbsolutePath());
    }

    /**
     * 构建包含全部文档操作并经过 Customizer 增强的 OpenAPI 实例。
     *
     * @return 完整的 OpenAPI 文档
     */
    private OpenAPI buildOpenApi() {
        OpenAPI openApi = new SpringDocConfig().apexOpenApi();
        io.swagger.v3.oas.models.Paths paths = new io.swagger.v3.oas.models.Paths();
        for (RouterOperation route : documentedOperations()) {
            PathItem pathItem = paths.computeIfAbsent(route.path(), ignored -> new PathItem());
            io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation()
                    .operationId(route.operation().operationId())
                    .summary(route.operation().summary())
                    .responses(new ApiResponses());
            for (RequestMethod method : route.method()) {
                pathItem.operation(PathItem.HttpMethod.valueOf(method.name()), operation);
            }
        }
        openApi.setPaths(paths);
        new SpringDocConfig().openApiCustomizer().customise(openApi);
        return openApi;
    }

    /**
     * 读取配置类中全部函数式端点文档声明。
     *
     * @return OpenAPI 路由声明列表
     */
    private List<RouterOperation> documentedOperations() {
        List<RouterOperation> operations = new ArrayList<>();
        for (Method method : RouterOpenApiConfig.class.getDeclaredMethods()) {
            RouterOperations annotations = method.getAnnotation(RouterOperations.class);
            if (annotations != null) {
                operations.addAll(List.of(annotations.value()));
            }
        }
        return operations;
    }
}
