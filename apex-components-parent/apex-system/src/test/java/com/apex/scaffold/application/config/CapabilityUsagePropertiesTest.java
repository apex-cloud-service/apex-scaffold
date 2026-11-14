package com.apex.scaffold.application.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.application.capability.CapabilityCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 能力计量前缀与开放路由契约测试（方案 D4）。
 *
 * <p>验证默认计量映射覆盖服务侧实际暴露的 {@code /open-api/**} 端点，且映射引用的能力编码
 * 均登记在 {@link CapabilityCatalog}。测试以静态源文件为证据，不启动 Spring 上下文，也不依赖
 * Nacos、数据库或下游服务；因此只能证明源码与配置契约一致，不能替代真实开放调用链 E2E。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("能力计量前缀与开放路由契约测试")
class CapabilityUsagePropertiesTest {

    /** 从 Router 源文件提取以 {@code /open-api/} 开头的字符串字面量。 */
    private static final Pattern OPEN_API_LITERAL = Pattern.compile("\"(/open-api/[^\"\\s]+)\"");

    /** 实际承载开放端点的 Router 源文件（两级嵌套布局，相对仓库根）。 */
    private static final List<Path> OPEN_ROUTER_FILES = List.of(
            Path.of(
                    "apex-services-bom/apex-file-service/src/main/java/com/apex/scaffold/fileservice/interfaces/router/OpenFileRouter.java"),
            Path.of(
                    "apex-services-bom/apex-workflow-service/src/main/java/com/apex/scaffold/workflowservice/interfaces/router/OpenWorkflowRouter.java"),
            Path.of(
                    "apex-services-bom/apex-office-service/src/main/java/com/apex/scaffold/officeservice/interfaces/router/OpenMessageRouter.java"),
            Path.of("apex-components-parent/apex-system/src/main/java/com/apex/scaffold/interfaces/router/OpenUserRouter.java"));

    /**
     * 验证默认计量前缀覆盖所有服务侧开放 Router 中声明的 {@code /open-api/**} 端点。
     *
     * <p>测试从 Router 源码提取路径字面量，而不是维护第二份手工路由清单；新增开放端点
     * 若未同步默认计量映射，会在此处失败。
     *
     * @throws IOException 任一 Router 源文件无法读取时抛出
     */
    @Test
    @DisplayName("默认映射覆盖全部实际开放路由")
    void defaultMappingsCoverActualOpenRoutes() throws IOException {
        CapabilityUsageProperties properties = new CapabilityUsageProperties();
        List<String> prefixes =
                properties.getMappings().values().stream().flatMap(List::stream).toList();

        List<String> actualRoutes = actualOpenApiRoutes();
        assertThat(actualRoutes).as("开放 Router 源文件应至少提取到实际端点").isNotEmpty();
        assertThat(actualRoutes).as("每个服务侧开放路由都必须命中至少一个默认计量前缀").allSatisfy(route -> assertThat(prefixes)
                .as("开放路由 %s 未被任何能力计量前缀覆盖", route)
                .anySatisfy(prefix -> assertThat(route).startsWith(prefix)));
    }

    /**
     * 验证默认计量映射的键均可由 {@link CapabilityCatalog} 解析。
     *
     * <p>能力目录是订阅与计量的共享编码来源；映射引用未登记编码会导致运营报表出现
     * 无法归属、无法在目录页展示的孤立数据。
     */
    @Test
    @DisplayName("计量映射只引用能力目录中已登记的能力")
    void mappingsReferenceKnownCapabilities() {
        CapabilityUsageProperties properties = new CapabilityUsageProperties();
        Set<String> capabilityCodes = properties.getMappings().keySet();

        assertThat(capabilityCodes).containsExactlyInAnyOrder("file-storage", "workflow", "user-org", "office-message");
        assertThat(capabilityCodes).allSatisfy(code -> assertThat(CapabilityCatalog.find(code))
                .as("计量映射引用的能力 %s 必须在能力目录登记", code)
                .isNotNull());
    }

    /**
     * 验证用户与消息开放端点同时登记无尾斜杠和有尾斜杠前缀。
     *
     * <p>实际 Router 声明的是无尾斜杠精确路径，而子资源请求通常带尾斜杠；两类前缀
     * 必须同时存在，否则调用量会漏计或把子路径误归到其他能力。
     */
    @Test
    @DisplayName("用户与消息的无尾斜杠端点被精确覆盖")
    void exactEndpointsAreCovered() {
        CapabilityUsageProperties properties = new CapabilityUsageProperties();
        assertThat(properties.getMappings().get("user-org"))
                .contains("/open-api/users", "/open-api/users/", "/open-api/user", "/open-api/user/");
        assertThat(properties.getMappings().get("office-message"))
                .contains("/open-api/messages", "/open-api/messages/");
    }

    /**
     * 静态提取实际开放 Router 中的 {@code /open-api/...} 字面量路径。
     *
     * <p>Surefire 以模块目录为工作目录（第二阶段嵌套布局下位于 {@code <分组父>/<模块>}），
     * 因此优先使用 {@code ../../<router>} 回到仓库根解析；若测试从仓库根运行，
     * 则直接使用仓库相对路径。两种方式均不读取外部运行时状态。
     *
     * @return 去重后的实际开放路径列表
     * @throws IOException 任一 Router 源文件读取失败
     */
    private List<String> actualOpenApiRoutes() throws IOException {
        List<String> routes = new ArrayList<>();
        for (Path repositoryRelativePath : OPEN_ROUTER_FILES) {
            routes.addAll(extractRoutes(repositoryRelativePath));
        }
        return routes.stream().distinct().toList();
    }

    /**
     * 读取单个 Router 源文件并提取开放路径。
     *
     * @param repositoryRelativePath 相对仓库根的 Router 路径
     * @return 文件内提取的开放路径
     * @throws IOException 文件读取失败
     */
    private List<String> extractRoutes(Path repositoryRelativePath) throws IOException {
        Path fromModule = Path.of("..", "..").resolve(repositoryRelativePath).normalize();
        Path source = Files.exists(fromModule) ? fromModule : repositoryRelativePath;
        assertThat(Files.exists(source))
                .as("开放 Router 源文件必须存在: %s", repositoryRelativePath)
                .isTrue();

        Matcher matcher = OPEN_API_LITERAL.matcher(Files.readString(source));
        List<String> routes = new ArrayList<>();
        while (matcher.find()) {
            routes.add(matcher.group(1).replaceAll("\\{[^}]+}", "{}"));
        }
        return routes;
    }
}
